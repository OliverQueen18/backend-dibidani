package ml.dibidani.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Un paiement par commande ; chaque tentative, notification ou action manuelle est tracée dans {@link PaymentTransaction}. */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {
  @OneToOne(optional = false, fetch = FetchType.LAZY) private CustomerOrder order;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentMethod method;
  @Enumerated(EnumType.STRING) private PaymentProvider provider;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus status = PaymentStatus.PENDING;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
  @Column(nullable = false, length = 3) private String currency = "XOF";
  private String providerReference;
  /** Jeton secret renvoyé par l'opérateur et vérifié lors du webhook. */
  private String providerToken;
  @Column(length = 1000) private String checkoutUrl;
  private String payerPhone;
  private Instant paidAt;
  @Column(length = 500) private String failureReason;

  public CustomerOrder getOrder() { return order; }
  public void setOrder(CustomerOrder v) { order = v; }
  public PaymentMethod getMethod() { return method; }
  public void setMethod(PaymentMethod v) { method = v; }
  public PaymentProvider getProvider() { return provider; }
  public void setProvider(PaymentProvider v) { provider = v; }
  public PaymentStatus getStatus() { return status; }
  public void setStatus(PaymentStatus v) { status = v; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal v) { amount = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { currency = v; }
  public String getProviderReference() { return providerReference; }
  public void setProviderReference(String v) { providerReference = v; }
  public String getProviderToken() { return providerToken; }
  public void setProviderToken(String v) { providerToken = v; }
  public String getCheckoutUrl() { return checkoutUrl; }
  public void setCheckoutUrl(String v) { checkoutUrl = v; }
  public String getPayerPhone() { return payerPhone; }
  public void setPayerPhone(String v) { payerPhone = v; }
  public Instant getPaidAt() { return paidAt; }
  public void setPaidAt(Instant v) { paidAt = v; }
  public String getFailureReason() { return failureReason; }
  public void setFailureReason(String v) { failureReason = v; }
}
