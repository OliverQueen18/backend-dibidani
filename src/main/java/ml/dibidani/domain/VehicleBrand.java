package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicle_brands")
public class VehicleBrand extends BaseEntity {
  @Column(nullable = false) private String name;
  @Column(nullable = false, unique = true) private String slug;
  private String logoUrl;
  @Column(nullable = false) private boolean active = true;
  @Column(nullable = false) private int displayOrder = 0;

  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public String getSlug() { return slug; }
  public void setSlug(String v) { slug = v; }
  public String getLogoUrl() { return logoUrl; }
  public void setLogoUrl(String v) { logoUrl = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
  public int getDisplayOrder() { return displayOrder; }
  public void setDisplayOrder(int v) { displayOrder = v; }
}
