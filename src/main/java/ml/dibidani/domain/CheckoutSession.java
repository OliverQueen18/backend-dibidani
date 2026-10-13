package ml.dibidani.domain;

import jakarta.persistence.*;

/** Brouillon de commande sauvegardé étape par étape ; aucun montant n'y est stocké. */
@Entity
@Table(name = "checkout_sessions")
public class CheckoutSession extends BaseEntity {
  @OneToOne(optional = false, fetch = FetchType.LAZY) private AppUser user;
  private String fullName;
  private String phone;
  private String email;
  private String addressLine;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "delivery_zone_id") private DeliveryZone deliveryZone;
  private String landmark;
  private Long addressId;
  @Enumerated(EnumType.STRING) private DeliveryMethod deliveryMethod;
  @Enumerated(EnumType.STRING) private PaymentMethod paymentMethod;
  @Enumerated(EnumType.STRING) private PaymentProvider paymentProvider;
  private String paymentPhone;
  @Column(nullable = false) private int step = 1;

  public AppUser getUser() { return user; }
  public void setUser(AppUser v) { user = v; }
  public String getFullName() { return fullName; }
  public void setFullName(String v) { fullName = v; }
  public String getPhone() { return phone; }
  public void setPhone(String v) { phone = v; }
  public String getEmail() { return email; }
  public void setEmail(String v) { email = v; }
  public String getAddressLine() { return addressLine; }
  public void setAddressLine(String v) { addressLine = v; }
  public DeliveryZone getDeliveryZone() { return deliveryZone; }
  public void setDeliveryZone(DeliveryZone v) { deliveryZone = v; }
  public String getLandmark() { return landmark; }
  public void setLandmark(String v) { landmark = v; }
  public Long getAddressId() { return addressId; }
  public void setAddressId(Long v) { addressId = v; }
  public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
  public void setDeliveryMethod(DeliveryMethod v) { deliveryMethod = v; }
  public PaymentMethod getPaymentMethod() { return paymentMethod; }
  public void setPaymentMethod(PaymentMethod v) { paymentMethod = v; }
  public PaymentProvider getPaymentProvider() { return paymentProvider; }
  public void setPaymentProvider(PaymentProvider v) { paymentProvider = v; }
  public String getPaymentPhone() { return paymentPhone; }
  public void setPaymentPhone(String v) { paymentPhone = v; }
  public int getStep() { return step; }
  public void setStep(int v) { step = v; }
}
