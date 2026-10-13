package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.domain.AppUser;
import ml.dibidani.domain.Role;
import java.time.Instant;
import java.util.List;

public final class UserDtos {
  private UserDtos() {}

  static final String PHONE = "^$|^\\+?[0-9 ().-]{6,25}$";

  public record UserResponse(Long id, String fullName, String email, String phone, Role role, boolean active, Instant lastLoginAt, Instant createdAt) {
    public static UserResponse of(AppUser u) {
      return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.isActive(), u.getLastLoginAt(), u.getCreatedAt());
    }
  }

  public record CreateUserRequest(
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 255) String fullName,
      @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 255) String email,
      @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String phone,
      @NotNull(message = "Le rôle est obligatoire") Role role,
      @NotBlank(message = "Le mot de passe est obligatoire") @Size(min = 8, max = 100, message = "8 caractères minimum") String password) {}

  public record UpdateUserRequest(
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 255) String fullName,
      @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String phone,
      @NotNull(message = "Le rôle est obligatoire") Role role,
      Boolean active,
      @Size(min = 8, max = 100, message = "8 caractères minimum") String password) {}

  public record RoleResponse(Role role, String label, String description, List<String> permissions, long userCount) {}
}
