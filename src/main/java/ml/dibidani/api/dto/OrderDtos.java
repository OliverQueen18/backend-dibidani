package ml.dibidani.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ml.dibidani.api.dto.DeliveryDtos.DeliveryResponse;
import ml.dibidani.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
  private OrderDtos() {}

  /** Ancienne API POST /orders, conservée : les prix restent recalculés par le serveur. */
  public record Item(@NotNull Long productId, Long variantId, @Min(1) @Max(999) int quantity) {}
  public record CreateOrderRequest(@NotEmpty @Size(max = 100) List<@Valid Item> items, @NotBlank @Size(max = 500) String deliveryAddress,
                                   @Size(max = 255) String deliveryDistrict, Long deliveryZoneId, DeliveryMethod deliveryMethod,
                                   @NotBlank @Size(max = 50) String customerPhone, @NotBlank @Size(max = 40) String paymentMethod,
                                   @Size(max = 50) String promoCode) {}

  /** Nouvelle tentative de paiement en ligne ; method/provider permettent de changer d'opérateur. */
  public record PaymentRequest(String method, PaymentProvider provider, @Size(max = 50) String phone) {}

  public record OrderItemResponse(Long productId, String productName, String productSku, String productSlug, String imageUrl,
                                  Long variantId, String variantName, Long vendorId, String vendorName,
                                  int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    public static OrderItemResponse of(OrderItem i) {
      return new OrderItemResponse(i.getProduct().getId(), i.getProductName(), i.getProductSku(), i.getProduct().getSlug(), i.getImageUrl(),
          i.getVariant() == null ? null : i.getVariant().getId(), i.getVariantName(), i.getVendorId(), i.getVendorName(),
          i.getQuantity(), i.getUnitPrice(), i.getLineTotal());
    }
  }

  /** changedBy et comment ne sont renseignés que pour le back-office. */
  public record HistoryEntry(OrderStatus fromStatus, OrderStatus toStatus, String label, String changedBy, String comment, Instant createdAt) {}

  public record PaymentSummary(Long id, PaymentMethod method, String methodLabel, PaymentProvider provider, PaymentStatus status, BigDecimal amount,
                               String currency, String providerReference, String checkoutUrl, Instant paidAt, String failureReason) {}

  public record StatusOption(OrderStatus status, String label) {}

  public record OrderResponse(
      Long id, String orderNumber, OrderStatus status, String statusLabel, PaymentStatus paymentStatus,
      BigDecimal subtotal, BigDecimal discountTotal, BigDecimal deliveryFee, BigDecimal total, String promoCode,
      Long customerId, String customerName, String customerEmail, String customerPhone,
      DeliveryMethod deliveryMethod, String deliveryMethodLabel, Long deliveryZoneId, String deliveryZoneName,
      String deliveryAddress, String deliveryDistrict, String deliveryCommune, String deliveryCity, String deliveryLandmark, String estimatedDelivery,
      String paymentMethod, String paymentMethodLabel, String paymentProvider,
      String trackingCode, String trackingToken, String trackingUrl, String qrCodeUrl,
      List<OrderItemResponse> items, int itemCount, List<HistoryEntry> history,
      PaymentSummary payment, DeliveryResponse delivery, List<StatusOption> allowedTransitions, String adminNote,
      boolean cancellable, Instant createdAt, Instant updatedAt) {}

  public record OrderSummary(Long id, String orderNumber, OrderStatus status, String statusLabel, PaymentStatus paymentStatus,
                             String paymentMethod, String paymentMethodLabel, DeliveryMethod deliveryMethod, BigDecimal total, int itemCount,
                             String customerName, String customerPhone, String zone, Instant createdAt) {}

  public record StatusChangeRequest(@NotNull(message = "Le statut est obligatoire") OrderStatus status, @Size(max = 1000) String comment) {}
  public record NoteRequest(@Size(max = 1000) String note) {}
  public record MarkPaidRequest(@Size(max = 255) String reference, @Size(max = 1000) String comment) {}
  public record CancelRequest(@NotBlank(message = "Indiquez le motif") @Size(max = 1000) String reason) {}

  public record PlaceOrderResult(OrderResponse order, PaymentSummary payment, String nextAction, String message) {}

  // ---------- Suivi public (QR code) ----------

  public record TrackingStep(OrderStatus status, String label, boolean done, boolean current, Instant date) {}
  public record TrackingItem(String name, String variantName, int quantity, String imageUrl) {}
  public record TrackingResponse(String orderNumber, OrderStatus status, String statusLabel, boolean closed, List<TrackingStep> steps,
                                 DeliveryMethod deliveryMethod, String deliveryMethodLabel, String district, String estimatedDelivery,
                                 String paymentMethodLabel, boolean paid, List<TrackingItem> items, int itemCount, Instant createdAt, Instant updatedAt) {}
}
