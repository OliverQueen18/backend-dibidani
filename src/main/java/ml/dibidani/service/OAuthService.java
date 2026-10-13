package ml.dibidani.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ml.dibidani.api.dto.AuthDtos.AuthResponse;
import ml.dibidani.api.dto.AuthDtos.OAuthProviderInfo;
import ml.dibidani.api.dto.AuthDtos.OAuthProvidersResponse;
import ml.dibidani.api.dto.AuthDtos.OAuthRequest;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.Slugs;
import ml.dibidani.common.Tokens;
import ml.dibidani.common.TooManyRequestsException;
import ml.dibidani.config.OAuthProperties;
import ml.dibidani.domain.AppUser;
import ml.dibidani.domain.OAuthProvider;
import ml.dibidani.domain.Role;
import ml.dibidani.domain.Vendor;
import ml.dibidani.repository.AppUserRepository;
import ml.dibidani.repository.VendorRepository;
import ml.dibidani.security.LoginRateLimiter;
import ml.dibidani.security.RequestContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.Locale;
import java.util.Map;

@Service
public class OAuthService {
  private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

  private final OAuthProperties oauth;
  private final AppUserRepository users;
  private final VendorRepository vendors;
  private final PasswordEncoder encoder;
  private final AuthService auth;
  private final LoginRateLimiter limiter;
  private final RestClient http = RestClient.create();

  public OAuthService(OAuthProperties oauth, AppUserRepository users, VendorRepository vendors, PasswordEncoder encoder,
                      AuthService auth, LoginRateLimiter limiter) {
    this.oauth = oauth;
    this.users = users;
    this.vendors = vendors;
    this.encoder = encoder;
    this.auth = auth;
    this.limiter = limiter;
  }

  public OAuthProvidersResponse providers() {
    boolean mock = oauth.mockEnabled();
    String googleId = oauth.google() == null ? "" : blank(oauth.google().clientId());
    String facebookId = oauth.facebook() == null ? "" : blank(oauth.facebook().appId());
    return new OAuthProvidersResponse(
        mock,
        new OAuthProviderInfo(mock || !googleId.isBlank(), googleId),
        new OAuthProviderInfo(mock || !facebookId.isBlank(), facebookId));
  }

  @Transactional
  public AuthResponse authenticate(OAuthRequest r, HttpServletRequest req, HttpServletResponse res) {
    OAuthProvider provider = OAuthProvider.parse(r.provider());
    String ip = RequestContext.ip(req);
    if (!limiter.allow(ip, "oauth:" + provider.name())) {
      throw new TooManyRequestsException("Trop de tentatives. Réessayez dans quelques minutes.");
    }
    Identity id = identity(provider, r);
    boolean seller = r.seller();
    String shopName = r.shopName() == null ? "" : r.shopName().trim();
    if (seller && shopName.isBlank()) throw new IllegalArgumentException("Le nom de la boutique est obligatoire");

    AppUser existing = users.findByOauthProviderAndOauthSubject(provider.name(), id.subject()).orElse(null);
    if (existing == null) existing = users.findByEmailIgnoreCase(id.email()).orElse(null);
    if (existing != null) {
      if (existing.getOauthProvider() != null && !provider.name().equals(existing.getOauthProvider())) {
        throw new ConflictException("Cet e-mail est déjà lié à un autre compte.");
      }
      if (seller && existing.getRole() != Role.SELLER) {
        throw new ConflictException("Un compte existe déjà avec cet e-mail. Connectez-vous, puis ouvrez une boutique séparément.");
      }
      existing.setOauthProvider(provider.name());
      existing.setOauthSubject(id.subject());
      if (id.name() != null && !id.name().isBlank() && (existing.getFullName() == null || existing.getFullName().isBlank())) {
        existing.setFullName(id.name());
      }
      return auth.completeExternalLogin(existing, req, res, "OAUTH_LOGIN_" + provider.name());
    }

    AppUser u = new AppUser();
    u.setFullName(id.name() == null || id.name().isBlank() ? "Membre Dibidani" : id.name().trim());
    u.setEmail(id.email());
    u.setPasswordHash(encoder.encode(Tokens.secure()));
    u.setOauthProvider(provider.name());
    u.setOauthSubject(id.subject());
    u.setRole(seller ? Role.SELLER : Role.CUSTOMER);
    u = users.save(u);
    if (seller) {
      var shop = new Vendor();
      shop.setName(shopName);
      shop.setSlug(Slugs.resolve(null, shopName, vendors::existsBySlug));
      shop.setCity("Bamako");
      shop.setDistrict(Slugs.blankToNull(r.district()));
      shop.setOwner(u);
      shop.setDescription("Boutique inscrite sur Dibidani via " + label(provider) + ".");
      vendors.save(shop);
    }
    return auth.completeExternalLogin(u, req, res, seller ? "OAUTH_REGISTER_SELLER_" + provider.name() : "OAUTH_REGISTER_" + provider.name());
  }

  private Identity identity(OAuthProvider provider, OAuthRequest r) {
    String access = blank(r.accessToken());
    String idToken = blank(r.idToken());
    boolean mockToken = access.isBlank() && (idToken.isBlank() || "mock".equalsIgnoreCase(idToken));
    if (mockToken) {
      if (!oauth.mockEnabled()) throw new IllegalArgumentException("Jeton " + label(provider) + " manquant");
      return mockIdentity(provider);
    }
    return switch (provider) {
      case GOOGLE -> google(idToken, access);
      case FACEBOOK -> facebook(access.isBlank() ? idToken : access);
    };
  }

  private Identity mockIdentity(OAuthProvider provider) {
    return switch (provider) {
      case GOOGLE -> new Identity("google-mock", "google.user@dibidani.ml", "Compte Google Dibidani");
      case FACEBOOK -> new Identity("facebook-mock", "facebook.user@dibidani.ml", "Compte Facebook Dibidani");
    };
  }

  private Identity google(String idToken, String accessToken) {
    if (oauth.google() == null || !oauth.google().configured()) {
      throw new IllegalArgumentException("Google n'est pas configuré");
    }
    try {
      if (!idToken.isBlank()) {
        Map<String, Object> body = http.get()
            .uri("https://oauth2.googleapis.com/tokeninfo?id_token={t}", idToken)
            .retrieve().body(MAP);
        String aud = str(body, "aud");
        if (!oauth.google().clientId().equals(aud)) throw new IllegalArgumentException("Jeton Google invalide");
        if (!truthy(body.get("email_verified"))) throw new IllegalArgumentException("L'e-mail Google n'est pas vérifié");
        return identityFrom(str(body, "sub"), str(body, "email"), str(body, "name"));
      }
      Map<String, Object> body = http.get()
          .uri("https://www.googleapis.com/oauth2/v3/userinfo")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .retrieve().body(MAP);
      if (!truthy(body.get("email_verified"))) throw new IllegalArgumentException("L'e-mail Google n'est pas vérifié");
      return identityFrom(str(body, "sub"), str(body, "email"), str(body, "name"));
    } catch (RestClientException e) {
      throw new IllegalArgumentException("Connexion Google impossible");
    }
  }

  private Identity facebook(String accessToken) {
    if (oauth.facebook() == null || !oauth.facebook().configured()) {
      throw new IllegalArgumentException("Facebook n'est pas configuré");
    }
    if (accessToken.isBlank()) throw new IllegalArgumentException("Jeton Facebook manquant");
    try {
      if (oauth.facebook().secretConfigured()) {
        Map<String, Object> debug = http.get()
            .uri("https://graph.facebook.com/debug_token?input_token={t}&access_token={a}",
                accessToken, oauth.facebook().appId() + "|" + oauth.facebook().appSecret())
            .retrieve().body(MAP);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = debug == null ? Map.of() : (Map<String, Object>) debug.getOrDefault("data", Map.of());
        if (!Boolean.TRUE.equals(data.get("is_valid")) || !oauth.facebook().appId().equals(String.valueOf(data.get("app_id")))) {
          throw new IllegalArgumentException("Jeton Facebook invalide");
        }
      }
      Map<String, Object> me = http.get()
          .uri("https://graph.facebook.com/v21.0/me?fields=id,name,email&access_token={t}", accessToken)
          .retrieve().body(MAP);
      return identityFrom(str(me, "id"), str(me, "email"), str(me, "name"));
    } catch (RestClientException e) {
      throw new IllegalArgumentException("Connexion Facebook impossible");
    }
  }

  private static Identity identityFrom(String subject, String email, String name) {
    if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Profil social incomplet");
    if (email == null || !email.contains("@")) throw new IllegalArgumentException("L'e-mail n'a pas été fourni par le réseau. Autorisez l'accès à l'e-mail.");
    return new Identity(subject, email.trim().toLowerCase(Locale.ROOT), name);
  }

  private static String label(OAuthProvider p) { return p == OAuthProvider.GOOGLE ? "Google" : "Facebook"; }

  private static String blank(String v) { return v == null ? "" : v.trim(); }

  private static String str(Map<String, Object> body, String key) {
    if (body == null) return "";
    Object v = body.get(key);
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static boolean truthy(Object v) {
    if (v instanceof Boolean b) return b;
    return v != null && "true".equalsIgnoreCase(String.valueOf(v));
  }

  private record Identity(String subject, String email, String name) {}
}
