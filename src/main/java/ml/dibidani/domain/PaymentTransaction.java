package ml.dibidani.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private Payment payment;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private TransactionType type;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus status;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentProvider provider;
  private String providerReference;
  @Column(length = 1000) private String message;
  /** Charge utile brute de l'opérateur, sans secrets, pour l'audit. */
  @Column(columnDefinition = "text") private String rawPayload;

  public Payment getPayment() { return payment; }
  public void setPayment(Payment v) { payment = v; }
  public TransactionType getType() { return type; }
  public void setType(TransactionType v) { type = v; }
  public PaymentStatus getStatus() { return status; }
  public void setStatus(PaymentStatus v) { status = v; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal v) { amount = v; }
  public PaymentProvider getProvider() { return provider; }
  public void setProvider(PaymentProvider v) { provider = v; }
  public String getProviderReference() { return providerReference; }
  public void setProviderReference(String v) { providerReference = v; }
  public String getMessage() { return message; }
  public void setMessage(String v) { message = v; }
  public String getRawPayload() { return rawPayload; }
  public void setRawPayload(String v) { rawPayload = v; }
}
