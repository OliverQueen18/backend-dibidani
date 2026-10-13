package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.domain.Permission;
import ml.dibidani.domain.Role;
import java.util.List;

public final class AuthDtos {
  private AuthDtos() {}

  public record LoginRequest(
      @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 255) String email,
      @NotBlank(message = "Le mot de passe est obligatoire") @Size(max = 200) String password) {}

  public record RegisterRequest(
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 255) String fullName,
      @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 255) String email,
      @NotBlank(message = "Le téléphone est obligatoire") @Pattern(regexp = "^\\+?[0-9 ().-]{8,25}$", message = "Numéro de téléphone invalide") String phone,
      @NotBlank(message = "Le mot de passe est obligatoire") @Size(min = 8, max = 100, message = "8 caractères minimum") String password,
      @Size(max = 20) String accountType,
      @Size(max = 150) String shopName,
      @Size(max = 255) String district) {
    public boolean seller() { return accountType != null && "SELLER".equalsIgnoreCase(accountType.trim()); }
  }

  public record ForgotPasswordRequest(
      @NotBlank(message = "Indiquez votre e-mail ou votre téléphone") @Size(max = 255) String identifier) {}

  public record VerifyResetOtpRequest(
      @NotBlank(message = "Indiquez votre e-mail ou votre téléphone") @Size(max = 255) String identifier,
      @NotBlank(message = "Le code est obligatoire") @Pattern(regexp = "^[0-9]{6}$", message = "Le code comporte 6 chiffres") String code) {}

  public record ResetPasswordRequest(
      @NotBlank(message = "Indiquez votre e-mail ou votre téléphone") @Size(max = 255) String identifier,
      @NotBlank(message = "Le code est obligatoire") @Pattern(regexp = "^[0-9]{6}$", message = "Le code comporte 6 chiffres") String code,
      @NotBlank(message = "Le mot de passe est obligatoire") @Size(min = 8, max = 100, message = "8 caractères minimum") String newPassword) {}

  public record ForgotPasswordResponse(String message, int expiresInSeconds, boolean emailSent, boolean smsSent, String demoCode) {}

  public record OAuthRequest(
      @NotBlank(message = "Le fournisseur est obligatoire") @Size(max = 20) String provider,
      @Size(max = 8000) String idToken,
      @Size(max = 8000) String accessToken,
      @Size(max = 20) String accountType,
      @Size(max = 150) String shopName,
      @Size(max = 255) String district) {
    public boolean seller() { return accountType != null && "SELLER".equalsIgnoreCase(accountType.trim()); }
  }

  public record OAuthProvidersResponse(boolean mockEnabled, OAuthProviderInfo google, OAuthProviderInfo facebook) {}

  public record OAuthProviderInfo(boolean enabled, String clientId) {}

  public record AuthResponse(String token, String tokenType, long expiresIn, Long userId, String fullName, String email, String role,
                             List<String> permissions, boolean staff, Long shopId, String shopName, String shopSlug) {
    public static AuthResponse of(String token, ml.dibidani.domain.AppUser u) {
      return of(token, u, null);
    }
    public static AuthResponse of(String token, ml.dibidani.domain.AppUser u, ml.dibidani.domain.Vendor shop) {
      Role role = u.getRole();
      return new AuthResponse(token, "Bearer", ml.dibidani.security.JwtService.ACCESS_TTL.toSeconds(), u.getId(), u.getFullName(), u.getEmail(),
          role.name(), role.permissions().stream().map(Permission::name).toList(), role.isStaff(),
          shop == null ? null : shop.getId(), shop == null ? null : shop.getName(), shop == null ? null : shop.getSlug());
    }
  }
}
