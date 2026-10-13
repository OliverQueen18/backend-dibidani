package ml.dibidani.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "delivery_zones")
public class DeliveryZone extends BaseEntity {
  @Column(nullable = false) private String city = "Bamako";
  private String commune;
  @Column(nullable = false) private String district;
  @Column(nullable = false) private String name;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal standardPrice;
  /** Null : l'express n'est pas proposé dans ce quartier. */
  @Column(precision = 14, scale = 2) private BigDecimal expressPrice;
  private String estimatedStandardTime;
  private String estimatedExpressTime;
  @Column(nullable = false) private boolean active = true;
  @Column(nullable = false) private int displayOrder = 0;

  public String getCity() { return city; }
  public void setCity(String v) { city = v; }
  public String getCommune() { return commune; }
  public void setCommune(String v) { commune = v; }
  public String getDistrict() { return district; }
  public void setDistrict(String v) { district = v; }
  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public BigDecimal getStandardPrice() { return standardPrice; }
  public void setStandardPrice(BigDecimal v) { standardPrice = v; }
  public BigDecimal getExpressPrice() { return expressPrice; }
  public void setExpressPrice(BigDecimal v) { expressPrice = v; }
  public String getEstimatedStandardTime() { return estimatedStandardTime; }
  public void setEstimatedStandardTime(String v) { estimatedStandardTime = v; }
  public String getEstimatedExpressTime() { return estimatedExpressTime; }
  public void setEstimatedExpressTime(String v) { estimatedExpressTime = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
  public int getDisplayOrder() { return displayOrder; }
  public void setDisplayOrder(int v) { displayOrder = v; }
}
