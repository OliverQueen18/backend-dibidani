package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private CustomerOrder order;
  @Enumerated(EnumType.STRING) private OrderStatus fromStatus;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus toStatus;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "changed_by_id") private AppUser changedBy;
  /** Nom figé de l'auteur (ou « Système », « Passerelle de paiement »). */
  @Column(nullable = false) private String changedByName;
  @Column(length = 1000) private String comment;

  public CustomerOrder getOrder() { return order; }
  public void setOrder(CustomerOrder v) { order = v; }
  public OrderStatus getFromStatus() { return fromStatus; }
  public void setFromStatus(OrderStatus v) { fromStatus = v; }
  public OrderStatus getToStatus() { return toStatus; }
  public void setToStatus(OrderStatus v) { toStatus = v; }
  public AppUser getChangedBy() { return changedBy; }
  public void setChangedBy(AppUser v) { changedBy = v; }
  public String getChangedByName() { return changedByName; }
  public void setChangedByName(String v) { changedByName = v; }
  public String getComment() { return comment; }
  public void setComment(String v) { comment = v; }
}
