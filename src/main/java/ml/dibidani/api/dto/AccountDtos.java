package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.api.dto.DeliveryDtos.ZoneResponse;
import ml.dibidani.api.dto.OrderDtos.OrderSummary;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.domain.AppUser;
import ml.dibidani.domain.CustomerAddress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AccountDtos {
  private AccountDtos() {}

  static final String PHONE = "^\\+?[0-9 ().-]{8,25}$";

  public record ProfileResponse(Long id, String fullName, String email, String phone, String role, Instant createdAt) {
    public static ProfileResponse of(AppUser u) { return new ProfileResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole().name(), u.getCreatedAt()); }
  }

  public record ProfileRequest(
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 255) String fullName,
      @NotBlank(message = "Le téléphone est obligatoire") @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String phone) {}

  public record PasswordRequest(
      @NotBlank(message = "Mot de passe actuel obligatoire") String currentPassword,
      @NotBlank(message = "Nouveau mot de passe obligatoire") @Size(min = 8, max = 100, message = "8 caractères minimum") String newPassword) {}

  public record AddressRequest(
      @Size(max = 100) String label,
      @NotBlank(message = "Le nom du destinataire est obligatoire") @Size(max = 255) String fullName,
      @NotBlank(message = "Le téléphone est obligatoire") @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String phone,
      @NotBlank(message = "L'adresse est obligatoire") @Size(max = 500) String addressLine,
      @NotNull(message = "Choisissez votre quartier") Long deliveryZoneId,
      @Size(max = 500) String landmark,
      Boolean defaultAddress) {}

  public record AddressResponse(Long id, String label, String fullName, String phone, String addressLine, ZoneResponse zone, String landmark,
                                boolean defaultAddress, boolean deliverable) {
    public static AddressResponse of(CustomerAddress a) {
      return new AddressResponse(a.getId(), a.getLabel(), a.getFullName(), a.getPhone(), a.getAddressLine(), ZoneResponse.of(a.getDeliveryZone()),
          a.getLandmark(), a.isDefaultAddress(), a.getDeliveryZone() != null && a.getDeliveryZone().isActive());
    }
  }

  public record FavoritesMergeRequest(@Size(max = 200) List<Long> productIds) {}

  public record Shortcut(String label, String icon, String link) {}

  public record DashboardResponse(
      String fullName, long ordersCount, BigDecimal totalSpent, long favoritesCount, long addressesCount,
      OrderSummary currentOrder, List<OrderSummary> recentOrders, List<ProductSummary> favorites, List<ProductSummary> recommendations,
      List<Shortcut> shortcuts) {}
}
