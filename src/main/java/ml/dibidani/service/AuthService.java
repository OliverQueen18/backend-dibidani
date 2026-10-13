package ml.dibidani.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ml.dibidani.api.dto.AuthDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.TooManyRequestsException;
import ml.dibidani.common.Tokens;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.AppUser;
import ml.dibidani.domain.RefreshToken;
import ml.dibidani.domain.Role;
import ml.dibidani.domain.Vendor;
import ml.dibidani.repository.AppUserRepository;
import ml.dibidani.repository.RefreshTokenRepository;
import ml.dibidani.repository.VendorRepository;
import ml.dibidani.security.JwtService;
import ml.dibidani.security.LoginRateLimiter;
import ml.dibidani.security.RequestContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

@Service
public class AuthService {
  public static final String REFRESH_COOKIE = "dibidani_rt";
  static final int MAX_FAILED = 5;
  static final Duration LOCK = Duration.ofMinutes(15);

  private final AppUserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final VendorRepository vendors;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final LoginRateLimiter limiter;
  private final AuditService audit;

  public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, VendorRepository vendors, PasswordEncoder encoder,
                     JwtService jwt, LoginRateLimiter limiter, AuditService audit) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.vendors = vendors;
    this.encoder = encoder;
    this.jwt = jwt;
    this.limiter = limiter;
    this.audit = audit;
  }

  @Transactional
  public AuthResponse login(LoginRequest r, HttpServletRequest req, HttpServletResponse res) {
    String email = r.email().trim().toLowerCase(Locale.ROOT);
    String ip = RequestContext.ip(req);
    if (!limiter.allow(ip, email)) {
      audit.record(req, email, "LOGIN_RATE_LIMITED", "User", email, false, "Trop de tentatives");
      throw new TooManyRequestsException("Trop de tentatives. Réessayez dans quelques minutes.");
    }
    var u = users.findByEmailIgnoreCase(email).orElse(null);
    Instant now = Instant.now();
    if (u == null) {
      audit.record(req, email, "LOGIN_FAILED", "User", email, false, "Compte inconnu");
      throw new IllegalArgumentException("Identifiants invalides");
    }
    if (u.isLocked(now)) {
      audit.record(req, email, "LOGIN_LOCKED", "User", String.valueOf(u.getId()), false, "Compte verrouillé");
      throw new IllegalArgumentException("Compte temporairement verrouillé. Réessayez plus tard.");
    }
    if (!u.isActive() || !encoder.matches(r.password(), u.getPasswordHash())) {
      fail(u, now);
      audit.record(req, email, "LOGIN_FAILED", "User", String.valueOf(u.getId()), false, "Mot de passe incorrect ou compte inactif");
      throw new IllegalArgumentException("Identifiants invalides");
    }
    u.setFailedLoginAttempts(0);
    u.setLockedUntil(null);
    u.setLastLoginAt(now);
    limiter.success(ip, email);
    issueRefresh(u, Tokens.secure(), req, res);
    audit.record(req, u.getEmail(), "LOGIN", "User", String.valueOf(u.getId()), true, null);
    return issued(u);
  }

  @Transactional
  public AuthResponse register(RegisterRequest r, HttpServletRequest req, HttpServletResponse res) {
    boolean seller = r.seller();
    String shopName = r.shopName() == null ? "" : r.shopName().trim();
    if (seller && shopName.isBlank()) throw new IllegalArgumentException("Le nom de la boutique est obligatoire");
    String email = r.email().trim().toLowerCase(Locale.ROOT);
    if (users.findByEmailIgnoreCase(email).isPresent()) throw new ConflictException("Cet e-mail est déjà utilisé");
    var u = new AppUser();
    u.setFullName(r.fullName().trim());
    u.setEmail(email);
    u.setPhone(r.phone());
    u.setPasswordHash(encoder.encode(r.password()));
    u.setRole(seller ? Role.SELLER : Role.CUSTOMER);
    u.setLastLoginAt(Instant.now());
    u = users.save(u);
    if (seller) {
      var shop = new Vendor();
      shop.setName(shopName);
      shop.setSlug(Slugs.resolve(null, shopName, vendors::existsBySlug));
      shop.setPhone(r.phone());
      shop.setWhatsapp(r.phone());
      shop.setDistrict(Slugs.blankToNull(r.district()));
      shop.setCity("Bamako");
      shop.setOwner(u);
      shop.setDescription("Boutique inscrite sur Dibidani par " + u.getFullName() + ".");
      vendors.save(shop);
    }
    issueRefresh(u, Tokens.secure(), req, res);
    audit.record(req, u.getEmail(), seller ? "REGISTER_SELLER" : "REGISTER", "User", String.valueOf(u.getId()), true, null);
    return issued(u);
  }

  @Transactional
  public AuthResponse refresh(HttpServletRequest req, HttpServletResponse res) {
    String raw = cookie(req);
    if (raw == null) throw new IllegalArgumentException("Session expirée");
    Instant now = Instant.now();
    var current = refreshTokens.lockByHash(JwtService.hash(raw)).orElse(null);
    if (current == null) {
      clearCookie(res);
      throw new IllegalArgumentException("Session expirée");
    }
    if (!current.isActive(now)) {
      if (current.getRevokedAt() != null) {
        refreshTokens.revokeFamily(current.getFamilyId(), "Réutilisation d'un jeton révoqué", now);
        current.getUser().revokeTokens();
        audit.record(req, current.getUser().getEmail(), "REFRESH_REUSE", "User", String.valueOf(current.getUser().getId()), false, "Famille révoquée");
      }
      clearCookie(res);
      throw new IllegalArgumentException("Session expirée");
    }
    var user = current.getUser();
    if (!user.isActive()) {
      refreshTokens.revokeAllForUser(user.getId(), "Compte désactivé", now);
      clearCookie(res);
      throw new IllegalArgumentException("Compte désactivé");
    }
    String nextRaw = Tokens.secure();
    var next = newToken(user, current.getFamilyId(), nextRaw, req, now);
    current.revoke("Remplacé");
    current.setReplacedBy(next);
    writeCookie(res, nextRaw, req);
    return issued(user);
  }

  @Transactional
  public void logout(HttpServletRequest req, HttpServletResponse res) {
    String raw = cookie(req);
    if (raw != null) {
      refreshTokens.lockByHash(JwtService.hash(raw)).ifPresent(t -> {
        refreshTokens.revokeFamily(t.getFamilyId(), "Déconnexion", Instant.now());
        audit.record(req, t.getUser().getEmail(), "LOGOUT", "User", String.valueOf(t.getUser().getId()), true, null);
      });
    }
    clearCookie(res);
  }

  public AuthResponse me(String email) {
    var u = users.findByEmailIgnoreCase(email).orElseThrow(() -> new IllegalArgumentException("Session expirée"));
    return AuthResponse.of(null, u, shopOf(u));
  }

  @Transactional
  public AuthResponse completeExternalLogin(AppUser u, HttpServletRequest req, HttpServletResponse res, String action) {
    Instant now = Instant.now();
    if (!u.isActive()) throw new IllegalArgumentException("Compte désactivé");
    if (u.isLocked(now)) throw new IllegalArgumentException("Compte temporairement verrouillé. Réessayez plus tard.");
    u.setFailedLoginAttempts(0);
    u.setLockedUntil(null);
    u.setLastLoginAt(now);
    limiter.success(RequestContext.ip(req), u.getEmail());
    issueRefresh(u, Tokens.secure(), req, res);
    audit.record(req, u.getEmail(), action, "User", String.valueOf(u.getId()), true, null);
    return issued(u);
  }

  private AuthResponse issued(AppUser u) {
    return AuthResponse.of(jwt.accessToken(u), u, shopOf(u));
  }

  private Vendor shopOf(AppUser u) {
    return u.getId() == null ? null : vendors.findByOwnerId(u.getId()).orElse(null);
  }

  @Scheduled(cron = "0 20 3 * * *")
  @Transactional
  public void purgeExpiredRefreshTokens() {
    refreshTokens.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(7)));
  }

  @Transactional
  public void revokeAll(AppUser user, String reason) {
    user.revokeTokens();
    refreshTokens.revokeAllForUser(user.getId(), reason, Instant.now());
  }

  private void fail(AppUser u, Instant now) {
    int n = u.getFailedLoginAttempts() + 1;
    u.setFailedLoginAttempts(n);
    if (n >= MAX_FAILED) u.setLockedUntil(now.plus(LOCK));
  }

  private void issueRefresh(AppUser user, String raw, HttpServletRequest req, HttpServletResponse res) {
    newToken(user, Tokens.secure(), raw, req, Instant.now());
    writeCookie(res, raw, req);
  }

  private RefreshToken newToken(AppUser user, String family, String raw, HttpServletRequest req, Instant now) {
    var t = new RefreshToken();
    t.setUser(user);
    t.setTokenHash(JwtService.hash(raw));
    t.setFamilyId(family);
    t.setExpiresAt(now.plus(JwtService.REFRESH_TTL));
    t.setCreatedIp(RequestContext.ip(req));
    t.setUserAgent(RequestContext.userAgent(req));
    return refreshTokens.save(t);
  }

  private static String cookie(HttpServletRequest req) {
    Cookie[] cookies = req.getCookies();
    if (cookies == null) return null;
    return Arrays.stream(cookies).filter(c -> REFRESH_COOKIE.equals(c.getName())).map(Cookie::getValue).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
  }

  private void writeCookie(HttpServletResponse res, String raw, HttpServletRequest req) {
    res.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder(raw, JwtService.REFRESH_TTL, req != null && req.isSecure()).toString());
  }

  private static void clearCookie(HttpServletResponse res) {
    res.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder("", Duration.ZERO, false).toString());
  }

  private static ResponseCookie cookieBuilder(String value, Duration maxAge, boolean secure) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(maxAge)
        .build();
  }
}
