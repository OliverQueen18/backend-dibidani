package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicle_models")
public class VehicleModel extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.EAGER) private VehicleBrand brand;
  @Column(nullable = false) private String name;
  @Column(nullable = false) private String slug;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private VehicleType vehicleType;
  @Column(nullable = false) private boolean active = true;

  public VehicleBrand getBrand() { return brand; }
  public void setBrand(VehicleBrand v) { brand = v; }
  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public String getSlug() { return slug; }
  public void setSlug(String v) { slug = v; }
  public VehicleType getVehicleType() { return vehicleType; }
  public void setVehicleType(VehicleType v) { vehicleType = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
}
