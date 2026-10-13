package ml.dibidani.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ml.dibidani.api.dto.VendorDtos.VendorRef;
import ml.dibidani.domain.DeliveryMethod;
import ml.dibidani.domain.PromoType;
import java.math.BigDecimal;
import java.util.List;

/** Le client n'envoie que des identifiants et des quantités : tous les montants sont calculés par le serveur. */
public final class CartDtos {
  private CartDtos() {}

  public record LineRequest(
      @NotNull(message = "Produit obligatoire") Long productId,
      Long variantId,
      @Min(value = 1, message = "Quantité minimale : 1") @Max(value = 999, message = "Quantité maximale : 999") int quantity) {}

  public record QuoteRequest(
      @NotNull @Size(max = 100, message = "100 articles maximum") List<@Valid LineRequest> items,
      @Size(max = 50) String promoCode,
      Long deliveryZoneId,
      DeliveryMethod deliveryMethod) {}

  public record MergeRequest(@Size(max = 100) List<@Valid LineRequest> items, @Size(max = 50) String promoCode) {}

  public record UpdateItemRequest(@Min(value = 1, message = "Quantité minimale : 1") @Max(value = 999, message = "Quantité maximale : 999") int quantity) {}

  public record PromoRequest(@NotBlank(message = "Code promo obligatoire") @Size(max = 50) String code) {}

  public enum LineStatus { AVAILABLE, QUANTITY_ADJUSTED, OUT_OF_STOCK, UNAVAILABLE }

  public record CartLine(
      Long itemId, Long productId, Long variantId, String productSlug, String productName, String sku, String variantName,
      String imageUrl, VendorRef vendor, String unit,
      BigDecimal unitPrice, BigDecimal regularPrice, boolean onPromotion,
      int quantity, int requestedQuantity, int availableStock, BigDecimal lineTotal,
      LineStatus status, String message) {}

  public record PromoView(String code, boolean applied, PromoType type, String description, String message) {}

  public record DeliveryQuote(Long zoneId, String zoneName, DeliveryMethod method, String methodLabel, BigDecimal fee,
                              String estimatedTime, boolean freeDelivery) {}

  public record CartView(
      List<CartLine> items, int itemCount,
      BigDecimal subtotal, BigDecimal discount, BigDecimal deliveryFee, BigDecimal total,
      PromoView promo, DeliveryQuote delivery,
      BigDecimal freeDeliveryThreshold, BigDecimal remainingForFreeDelivery,
      List<String> warnings, boolean canCheckout) {}
}
