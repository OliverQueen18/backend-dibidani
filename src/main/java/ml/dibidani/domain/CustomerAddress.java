package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_addresses")
public class CustomerAddress extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private AppUser user;
  private String label;
  @Column(nullable = false) private String fullName;
  @Column(nullable = false) private String phone;
  @Column(nullable = false) private String addressLine;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "delivery_zone_id") private DeliveryZone deliveryZone;
  private String landmark;
  @Column(name = "is_default", nullable = false) private boolean defaultAddress = false;

  public AppUser getUser() { return user; }
  public void setUser(AppUser v) { user = v; }
  public String getLabel() { return label; }
  public void setLabel(String v) { label = v; }
  public String getFullName() { return fullName; }
  public void setFullName(String v) { fullName = v; }
  public String getPhone() { return phone; }
  public void setPhone(String v) { phone = v; }
  public String getAddressLine() { return addressLine; }
  public void setAddressLine(String v) { addressLine = v; }
  public DeliveryZone getDeliveryZone() { return deliveryZone; }
  public void setDeliveryZone(DeliveryZone v) { deliveryZone = v; }
  public String getLandmark() { return landmark; }
  public void setLandmark(String v) { landmark = v; }
  public boolean isDefaultAddress() { return defaultAddress; }
  public void setDefaultAddress(boolean v) { defaultAddress = v; }
}
