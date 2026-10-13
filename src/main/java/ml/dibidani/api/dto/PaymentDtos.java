package ml.dibidani.api.dto;

import ml.dibidani.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaymentDtos {
  private PaymentDtos() {}

  public record ProviderOption(PaymentProvider provider, String name, boolean test) {}

  public record PaymentOption(PaymentMethod method, String label, boolean online, boolean available, List<ProviderOption> providers,
                              boolean requiresPhone, String description) {}

  public record TransactionResponse(Long id, TransactionType type, PaymentStatus status, BigDecimal amount, PaymentProvider provider,
                                    String providerReference, String message, Instant createdAt) {
    public static TransactionResponse of(PaymentTransaction t) {
      return new TransactionResponse(t.getId(), t.getType(), t.getStatus(), t.getAmount(), t.getProvider(), t.getProviderReference(), t.getMessage(), t.getCreatedAt());
    }
  }

  public record PaymentRow(Long id, Long orderId, String orderNumber, String customerName, PaymentMethod method, String methodLabel,
                           PaymentProvider provider, PaymentStatus status, BigDecimal amount, String providerReference, String payerPhone,
                           Instant paidAt, Instant createdAt) {
    public static PaymentRow of(Payment p) {
      var o = p.getOrder();
      return new PaymentRow(p.getId(), o.getId(), o.getOrderNumber(), o.getCustomerName(), p.getMethod(), p.getMethod().label(), p.getProvider(),
          p.getStatus(), p.getAmount(), p.getProviderReference(), p.getPayerPhone(), p.getPaidAt(), p.getCreatedAt());
    }
  }

  public record PaymentDetail(PaymentRow payment, String failureReason, List<TransactionResponse> transactions) {}

  public record StatusStat(PaymentStatus status, long count, BigDecimal amount) {}
}
