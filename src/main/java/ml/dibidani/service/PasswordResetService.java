package ml.dibidani.service;

import jakarta.servlet.http.HttpServletRequest;
import ml.dibidani.api.dto.AuthDtos.*;
import ml.dibidani.common.TooManyRequestsException;
import ml.dibidani.common.Tokens;
import ml.dibidani.domain.AppUser;
import ml.dibidani.domain.PasswordResetOtp;
import ml.dibidani.notify.OtpNotifier;
import ml.dibidani.notify.Phones;
import ml.dibidani.repository.AppUserRepository;
import ml.dibidani.repository.PasswordResetOtpRepository;
import ml.dibidani.security.OtpRateLimiter;
import ml.dibidani.security.RequestContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class PasswordResetService {
  static final Duration TTL = Duration.ofMinutes(10);
  static final int MAX_ATTEMPTS = 5;
  static final String GENERIC = "Si un compte existe, un code a été envoyé par e-mail et par SMS.";
  static final String BAD_CODE = "Code invalide ou expiré";

  private final AppUserRepository users;
  private final PasswordResetOtpRepository otps;
  private final PasswordEncoder encoder;
  private final OtpNotifier notifier;
  private final OtpRateLimiter limiter;
  private final AuthService auth;
  private final AuditService audit;

  public PasswordResetService(AppUserRepository users, PasswordResetOtpRepository otps, PasswordEncoder encoder,
                              OtpNotifier notifier, OtpRateLimiter limiter, AuthService auth, AuditService audit) {
    this.users = users;
    this.otps = otps;
    this.encoder = encoder;
    this.notifier = notifier;
    this.limiter = limiter;
    this.auth = auth;
    this.audit = audit;
  }

  @Transactional
  public ForgotPasswordResponse forgot(ForgotPasswordRequest r, HttpServletRequest req) {
    String identifier = r.identifier().trim();
    String ip = RequestContext.ip(req);
    if (!limiter.allowForgot(ip, identifier)) {
      throw new TooManyRequestsException("Trop de demandes. Réessayez dans quelques minutes.");
    }
    AppUser user = findUser(identifier).orElse(null);
    if (user == null || !user.isActive()) {
      audit.record(req, identifier, "PASSWORD_RESET_REQUEST", "User", identifier, false, "Compte inconnu");
      return new ForgotPasswordResponse(GENERIC, (int) TTL.toSeconds(), false, false, null);
    }
    for (PasswordResetOtp previous : otps.findByUserIdAndConsumedAtIsNull(user.getId())) {
      previous.setConsumedAt(Instant.now());
    }
    String code = Tokens.digits(6);
    var otp = new PasswordResetOtp();
    otp.setUser(user);
    otp.setCodeHash(encoder.encode(code));
    otp.setExpiresAt(Instant.now().plus(TTL));
    otp.setCreatedIp(ip);
    var delivery = notifier.sendResetCode(user.getEmail(), user.getPhone(), user.getFullName(), code);
    otp.setEmailSent(delivery.emailSent());
    otp.setSmsSent(delivery.smsSent());
    otps.save(otp);
    audit.record(req, user.getEmail(), "PASSWORD_RESET_REQUEST", "User", String.valueOf(user.getId()), true,
        "email=" + delivery.emailSent() + ",sms=" + delivery.smsSent());
    return new ForgotPasswordResponse(GENERIC, (int) TTL.toSeconds(), delivery.emailSent(), delivery.smsSent(),
        delivery.demo() ? code : null);
  }

  @Transactional
  public void verify(VerifyResetOtpRequest r, HttpServletRequest req) {
    matchOpenOtp(r.identifier(), r.code(), req, false);
  }

  @Transactional
  public void reset(ResetPasswordRequest r, HttpServletRequest req) {
    PasswordResetOtp otp = matchOpenOtp(r.identifier(), r.code(), req, true);
    AppUser user = otp.getUser();
    if (encoder.matches(r.newPassword(), user.getPasswordHash())) {
      throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'actuel");
    }
    user.setPasswordHash(encoder.encode(r.newPassword()));
    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);
    otp.setConsumedAt(Instant.now());
    auth.revokeAll(user, "Mot de passe réinitialisé");
    limiter.clear(r.identifier());
    audit.record(req, user.getEmail(), "PASSWORD_RESET", "User", String.valueOf(user.getId()), true, null);
  }

  @Scheduled(cron = "0 40 3 * * *")
  @Transactional
  public void purgeExpired() {
    otps.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(2)));
  }

  private PasswordResetOtp matchOpenOtp(String identifier, String code, HttpServletRequest req, boolean consumeOnMatch) {
    String id = identifier.trim();
    if (!limiter.allowVerify(id)) throw new TooManyRequestsException("Trop de tentatives. Réessayez dans quelques minutes.");
    AppUser user = findUser(id).orElse(null);
    if (user == null) {
      audit.record(req, id, "PASSWORD_RESET_OTP_FAILED", "User", id, false, "Compte inconnu");
      throw new IllegalArgumentException(BAD_CODE);
    }
    PasswordResetOtp otp = otps.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId()).orElse(null);
    Instant now = Instant.now();
    if (otp == null || !otp.isOpen(now)) {
      audit.record(req, user.getEmail(), "PASSWORD_RESET_OTP_FAILED", "User", String.valueOf(user.getId()), false, "Expiré");
      throw new IllegalArgumentException(BAD_CODE);
    }
    if (otp.getAttempts() >= MAX_ATTEMPTS) {
      otp.setConsumedAt(now);
      throw new IllegalArgumentException(BAD_CODE);
    }
    otp.setAttempts(otp.getAttempts() + 1);
    if (!encoder.matches(code.trim(), otp.getCodeHash())) {
      audit.record(req, user.getEmail(), "PASSWORD_RESET_OTP_FAILED", "User", String.valueOf(user.getId()), false, "Code incorrect");
      throw new IllegalArgumentException(BAD_CODE);
    }
    if (consumeOnMatch) otp.setConsumedAt(now);
    else otp.setAttempts(Math.max(0, otp.getAttempts() - 1));
    return otp;
  }

  private java.util.Optional<AppUser> findUser(String identifier) {
    String raw = identifier.trim();
    if (raw.contains("@")) return users.findByEmailIgnoreCase(raw.toLowerCase(Locale.ROOT));
    String last8 = Phones.last8(raw);
    if (last8.length() < 8) return java.util.Optional.empty();
    return users.findByPhoneDigits(last8);
  }
}
