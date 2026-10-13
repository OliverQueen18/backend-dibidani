package ml.dibidani.domain;

import jakarta.persistence.*;

/** Version / motorisation d'un modèle, produite sur une plage d'années (yearTo null : toujours produite). */
@Entity
@Table(name = "vehicle_engines")
public class VehicleEngine extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.EAGER) private VehicleModel model;
  @Column(nullable = false) private String name;
  @Enumerated(EnumType.STRING) private FuelType fuelType;
  private Integer displacementCc;
  private Integer powerHp;
  @Column(nullable = false) private int yearFrom;
  private Integer yearTo;
  @Column(nullable = false) private boolean active = true;

  public boolean coversYear(int year) { return year >= yearFrom && (yearTo == null || year <= yearTo); }

  public VehicleModel getModel() { return model; }
  public void setModel(VehicleModel v) { model = v; }
  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public FuelType getFuelType() { return fuelType; }
  public void setFuelType(FuelType v) { fuelType = v; }
  public Integer getDisplacementCc() { return displacementCc; }
  public void setDisplacementCc(Integer v) { displacementCc = v; }
  public Integer getPowerHp() { return powerHp; }
  public void setPowerHp(Integer v) { powerHp = v; }
  public int getYearFrom() { return yearFrom; }
  public void setYearFrom(int v) { yearFrom = v; }
  public Integer getYearTo() { return yearTo; }
  public void setYearTo(Integer v) { yearTo = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
}
