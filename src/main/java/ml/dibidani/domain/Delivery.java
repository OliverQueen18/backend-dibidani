package ml.dibidani.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Suivi logistique d'une commande ; le livreur (rôle COURIER) sera rattaché par la future application Livreur. */
@Entity
@Table(name = "deliveries")
public class Delivery extends BaseEntity {
  @OneToOne(optional = false, fetch = FetchType.LAZY) private CustomerOrder order;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryMethod method;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryStatus status = DeliveryStatus.PENDING;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "courier_id") private AppUser courier;
  private String courierName;
  private String courierPhone;
  private Instant assignedAt;
  private Instant pickedUpAt;
  private Instant deliveredAt;
  @Column(length = 1000) private String notes;

  public CustomerOrder getOrder() { return order; }
  public void setOrder(CustomerOrder v) { order = v; }
  public DeliveryMethod getMethod() { return method; }
  public void setMethod(DeliveryMethod v) { method = v; }
  public DeliveryStatus getStatus() { return status; }
  public void setStatus(DeliveryStatus v) { status = v; }
  public AppUser getCourier() { return courier; }
  public void setCourier(AppUser v) { courier = v; }
  public String getCourierName() { return courierName; }
  public void setCourierName(String v) { courierName = v; }
  public String getCourierPhone() { return courierPhone; }
  public void setCourierPhone(String v) { courierPhone = v; }
  public Instant getAssignedAt() { return assignedAt; }
  public void setAssignedAt(Instant v) { assignedAt = v; }
  public Instant getPickedUpAt() { return pickedUpAt; }
  public void setPickedUpAt(Instant v) { pickedUpAt = v; }
  public Instant getDeliveredAt() { return deliveredAt; }
  public void setDeliveredAt(Instant v) { deliveredAt = v; }
  public String getNotes() { return notes; }
  public void setNotes(String v) { notes = v; }
}
