package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.domain.Delivery;
import ml.dibidani.domain.DeliveryMethod;
import ml.dibidani.domain.DeliveryStatus;
import ml.dibidani.domain.DeliveryZone;
import java.math.BigDecimal;
import java.time.Instant;

public final class DeliveryDtos {
  private DeliveryDtos() {}

  public record ZoneRequest(
      @NotBlank(message = "La ville est obligatoire") @Size(max = 120) String city,
      @Size(max = 120) String commune,
      @NotBlank(message = "Le quartier est obligatoire") @Size(max = 150) String district,
      @Size(max = 200) String name,
      @NotNull(message = "Le tarif standard est obligatoire") @DecimalMin(value = "0.0", message = "Tarif invalide") BigDecimal standardPrice,
      @DecimalMin(value = "0.0", message = "Tarif invalide") BigDecimal expressPrice,
      @Size(max = 100) String estimatedStandardTime,
      @Size(max = 100) String estimatedExpressTime,
      Boolean active,
      Integer displayOrder) {}

  public record ZoneResponse(Long id, String city, String commune, String district, String name,
                             BigDecimal standardPrice, BigDecimal expressPrice, String estimatedStandardTime, String estimatedExpressTime,
                             boolean expressAvailable, boolean active, int displayOrder) {
    public static ZoneResponse of(DeliveryZone z) {
      if (z == null) return null;
      return new ZoneResponse(z.getId(), z.getCity(), z.getCommune(), z.getDistrict(), z.getName(), z.getStandardPrice(), z.getExpressPrice(),
          z.getEstimatedStandardTime(), z.getEstimatedExpressTime(), z.getExpressPrice() != null, z.isActive(), z.getDisplayOrder());
    }
  }

  public record DeliveryOption(DeliveryMethod method, String label, BigDecimal fee, String estimatedTime, boolean available, String message) {}

  public record DeliveryResponse(Long id, DeliveryMethod method, DeliveryStatus status, Long courierId, String courierName, String courierPhone,
                                 Instant assignedAt, Instant pickedUpAt, Instant deliveredAt, String notes) {
    public static DeliveryResponse of(Delivery d) {
      if (d == null) return null;
      return new DeliveryResponse(d.getId(), d.getMethod(), d.getStatus(), d.getCourier() == null ? null : d.getCourier().getId(),
          d.getCourierName(), d.getCourierPhone(), d.getAssignedAt(), d.getPickedUpAt(), d.getDeliveredAt(), d.getNotes());
    }
  }

  public record AssignRequest(Long courierId, @Size(max = 255) String courierName, @Size(max = 50) String courierPhone, @Size(max = 1000) String notes) {}

  public record DeliveryRow(Long id, Long orderId, String orderNumber, String orderStatus, DeliveryMethod method, DeliveryStatus status,
                            String customerName, String customerPhone, String zone, String address, String courierName, String courierPhone,
                            BigDecimal total, String paymentMethod, String paymentStatus, Instant createdAt) {}
}
