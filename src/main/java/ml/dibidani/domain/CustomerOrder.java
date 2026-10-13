package ml.dibidani.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder extends BaseEntity {
  @Column(nullable = false, unique = true) private String orderNumber;
  /** Jeton public aléatoire utilisé par le QR code et la page de suivi ; jamais l'id séquentiel. */
  @Column(nullable = false, unique = true, length = 64) private String trackingToken;
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private AppUser customer;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus status = OrderStatus.NEW;

  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal discountTotal = BigDecimal.ZERO;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal deliveryFee = BigDecimal.ZERO;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal total = BigDecimal.ZERO;
  private String promoCode;

  private String customerName;
  private String customerEmail;
  private String customerPhone;

  @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryMethod deliveryMethod = DeliveryMethod.STANDARD;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "delivery_zone_id") private DeliveryZone deliveryZone;
  private String deliveryZoneName;
  private String deliveryAddress;
  private String deliveryDistrict;
  private String deliveryCommune;
  private String deliveryCity;
  private String deliveryLandmark;
  private String estimatedDelivery;

  /** Valeur de {@link PaymentMethod} ; texte libre conservé pour les anciennes commandes. */
  private String paymentMethod;
  private String paymentProvider;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus paymentStatus = PaymentStatus.PENDING;

  private String trackingCode;
  @Column(nullable = false) private boolean stockReleased = false;
  @Column(length = 1000) private String adminNote;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) @BatchSize(size = 50)
  private List<OrderItem> items = new ArrayList<>();

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) @BatchSize(size = 50)
  @OrderBy("createdAt asc, id asc")
  private List<OrderStatusHistory> history = new ArrayList<>();

  public PaymentMethod paymentMethodEnum() {
    if (paymentMethod == null) return null;
    try { return PaymentMethod.valueOf(paymentMethod); } catch (IllegalArgumentException e) { return null; }
  }

  public String getOrderNumber() { return orderNumber; }
  public void setOrderNumber(String v) { orderNumber = v; }
  public String getTrackingToken() { return trackingToken; }
  public void setTrackingToken(String v) { trackingToken = v; }
  public AppUser getCustomer() { return customer; }
  public void setCustomer(AppUser v) { customer = v; }
  public OrderStatus getStatus() { return status; }
  public void setStatus(OrderStatus v) { status = v; }
  public BigDecimal getSubtotal() { return subtotal; }
  public void setSubtotal(BigDecimal v) { subtotal = v; }
  public BigDecimal getDiscountTotal() { return discountTotal; }
  public void setDiscountTotal(BigDecimal v) { discountTotal = v; }
  public BigDecimal getDeliveryFee() { return deliveryFee; }
  public void setDeliveryFee(BigDecimal v) { deliveryFee = v; }
  public BigDecimal getTotal() { return total; }
  public void setTotal(BigDecimal v) { total = v; }
  public String getPromoCode() { return promoCode; }
  public void setPromoCode(String v) { promoCode = v; }
  public String getCustomerName() { return customerName; }
  public void setCustomerName(String v) { customerName = v; }
  public String getCustomerEmail() { return customerEmail; }
  public void setCustomerEmail(String v) { customerEmail = v; }
  public String getCustomerPhone() { return customerPhone; }
  public void setCustomerPhone(String v) { customerPhone = v; }
  public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
  public void setDeliveryMethod(DeliveryMethod v) { deliveryMethod = v; }
  public DeliveryZone getDeliveryZone() { return deliveryZone; }
  public void setDeliveryZone(DeliveryZone v) { deliveryZone = v; }
  public String getDeliveryZoneName() { return deliveryZoneName; }
  public void setDeliveryZoneName(String v) { deliveryZoneName = v; }
  public String getDeliveryAddress() { return deliveryAddress; }
  public void setDeliveryAddress(String v) { deliveryAddress = v; }
  public String getDeliveryDistrict() { return deliveryDistrict; }
  public void setDeliveryDistrict(String v) { deliveryDistrict = v; }
  public String getDeliveryCommune() { return deliveryCommune; }
  public void setDeliveryCommune(String v) { deliveryCommune = v; }
  public String getDeliveryCity() { return deliveryCity; }
  public void setDeliveryCity(String v) { deliveryCity = v; }
  public String getDeliveryLandmark() { return deliveryLandmark; }
  public void setDeliveryLandmark(String v) { deliveryLandmark = v; }
  public String getEstimatedDelivery() { return estimatedDelivery; }
  public void setEstimatedDelivery(String v) { estimatedDelivery = v; }
  public String getPaymentMethod() { return paymentMethod; }
  public void setPaymentMethod(String v) { paymentMethod = v; }
  public String getPaymentProvider() { return paymentProvider; }
  public void setPaymentProvider(String v) { paymentProvider = v; }
  public PaymentStatus getPaymentStatus() { return paymentStatus; }
  public void setPaymentStatus(PaymentStatus v) { paymentStatus = v; }
  public String getTrackingCode() { return trackingCode; }
  public void setTrackingCode(String v) { trackingCode = v; }
  public boolean isStockReleased() { return stockReleased; }
  public void setStockReleased(boolean v) { stockReleased = v; }
  public String getAdminNote() { return adminNote; }
  public void setAdminNote(String v) { adminNote = v; }
  public List<OrderItem> getItems() { return items; }
  public List<OrderStatusHistory> getHistory() { return history; }
}
