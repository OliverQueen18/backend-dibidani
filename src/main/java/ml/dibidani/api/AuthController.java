package ml.dibidani.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ml.dibidani.api.dto.AuthDtos.*;
import ml.dibidani.service.AuthService;
import ml.dibidani.service.OAuthService;
import ml.dibidani.service.PasswordResetService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;
  private final PasswordResetService reset;
  private final OAuthService oauth;

  public AuthController(AuthService auth, PasswordResetService reset, OAuthService oauth) {
    this.auth = auth;
    this.reset = reset;
    this.oauth = oauth;
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest req, HttpServletResponse res) {
    return auth.login(r, req, res);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody RegisterRequest r, HttpServletRequest req, HttpServletResponse res) {
    return auth.register(r, req, res);
  }

  @GetMapping("/oauth/providers")
  public OAuthProvidersResponse oauthProviders() { return oauth.providers(); }

  @PostMapping("/oauth")
  public AuthResponse oauth(@Valid @RequestBody OAuthRequest r, HttpServletRequest req, HttpServletResponse res) {
    return oauth.authenticate(r, req, res);
  }

  @PostMapping("/refresh")
  public AuthResponse refresh(HttpServletRequest req, HttpServletResponse res) { return auth.refresh(req, res); }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest req, HttpServletResponse res) { auth.logout(req, res); }

  @PostMapping("/forgot-password")
  public ForgotPasswordResponse forgot(@Valid @RequestBody ForgotPasswordRequest r, HttpServletRequest req) {
    return reset.forgot(r, req);
  }

  @PostMapping("/verify-reset-otp")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void verifyOtp(@Valid @RequestBody VerifyResetOtpRequest r, HttpServletRequest req) {
    reset.verify(r, req);
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest r, HttpServletRequest req) {
    reset.reset(r, req);
  }

  @GetMapping("/me")
  public AuthResponse me(Authentication a) { return auth.me(a.getName()); }
}
