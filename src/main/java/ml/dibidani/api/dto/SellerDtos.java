package ml.dibidani.api.dto;

import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.domain.CustomerOrder;
import ml.dibidani.domain.OrderItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SellerDtos {
  private SellerDtos() {}

  public record SellerDashboard(
      Long shopId,
      String shopName,
      String shopSlug,
      String district,
      long productCount,
      long activeProductCount,
      long orderedLines,
      long openOrders,
      List<ProductSummary> recentProducts,
      List<SellerOrderLine> recentOrders) {}

  public record SellerOrderLine(
      Long orderId,
      String orderNumber,
      Instant createdAt,
      String status,
      String statusLabel,
      String customerName,
      String customerPhone,
      String district,
      Long productId,
      String productName,
      String productSku,
      String imageUrl,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal lineTotal) {

    public static SellerOrderLine of(OrderItem i) {
      CustomerOrder o = i.getOrder();
      return new SellerOrderLine(
          o.getId(),
          o.getOrderNumber(),
          o.getCreatedAt(),
          o.getStatus().name(),
          o.getStatus().label(),
          o.getCustomerName(),
          o.getCustomerPhone(),
          o.getDeliveryDistrict() != null ? o.getDeliveryDistrict() : o.getDeliveryZoneName(),
          i.getProduct() == null ? null : i.getProduct().getId(),
          i.getProductName(),
          i.getProductSku(),
          i.getImageUrl(),
          i.getQuantity(),
          i.getUnitPrice(),
          i.getLineTotal());
    }
  }
}
