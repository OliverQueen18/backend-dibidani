package ml.dibidani.api.dto;

import ml.dibidani.api.dto.AccountDtos.AddressResponse;
import ml.dibidani.api.dto.OrderDtos.OrderSummary;
import ml.dibidani.api.dto.PaymentDtos.StatusStat;
import ml.dibidani.api.dto.StockDtos.StockRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StatsDtos {
  private StatsDtos() {}

  public record DayPoint(LocalDate date, long orders, BigDecimal revenue) {}
  public record LabelValue(String key, String label, long count, BigDecimal amount) {}
  public record TopProduct(Long productId, String name, String sku, long quantity, BigDecimal revenue) {}

  public record Kpis(
      BigDecimal revenue, BigDecimal revenuePrevious, long orders, long ordersPrevious,
      BigDecimal salesToday, long ordersToday, BigDecimal averageBasket, long newCustomers, long unitsSold,
      long lowStock, long outOfStock, long pendingPayments, BigDecimal paymentsCollected,
      long ordersToProcess, long ordersPreparing, long ordersToDeliver, long totalCustomers, long activeProducts) {}

  public record DashboardResponse(
      int periodDays, LocalDate from, LocalDate to, Kpis kpis,
      List<DayPoint> salesByDay, List<LabelValue> ordersByStatus, List<LabelValue> salesByPaymentMethod,
      List<LabelValue> salesByCategory, List<TopProduct> topProducts, List<StatusStat> payments,
      List<StockRow> lowStockProducts, List<OrderSummary> latestOrders) {}

  public record ReportResponse(
      LocalDate from, LocalDate to, BigDecimal revenue, long orders, BigDecimal averageBasket, long unitsSold, long newCustomers,
      List<DayPoint> salesByDay, List<LabelValue> salesByCategory, List<LabelValue> salesByVendor, List<LabelValue> salesByPaymentMethod,
      List<LabelValue> salesByZone, List<LabelValue> ordersByStatus, List<TopProduct> topProducts) {}

  public record CustomerRow(Long id, String fullName, String email, String phone, boolean active, long orders, BigDecimal totalSpent,
                            Instant lastOrderAt, Instant lastLoginAt, Instant createdAt) {}

  public record CustomerDetail(CustomerRow customer, List<AddressResponse> addresses, List<OrderSummary> orders, long favoritesCount) {}
}
