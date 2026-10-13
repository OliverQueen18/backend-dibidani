package ml.dibidani.domain;

import jakarta.persistence.*;

/** Un produit monte sur une motorisation, éventuellement restreint à une plage d'années (null : toute la production). */
@Entity
@Table(name = "vehicle_compatibilities")
public class VehicleCompatibility extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private Product product;
  @ManyToOne(optional = false, fetch = FetchType.EAGER) private VehicleEngine engine;
  private Integer yearFrom;
  private Integer yearTo;
  @Column(length = 500) private String notes;

  public Product getProduct() { return product; }
  public void setProduct(Product v) { product = v; }
  public VehicleEngine getEngine() { return engine; }
  public void setEngine(VehicleEngine v) { engine = v; }
  public Integer getYearFrom() { return yearFrom; }
  public void setYearFrom(Integer v) { yearFrom = v; }
  public Integer getYearTo() { return yearTo; }
  public void setYearTo(Integer v) { yearTo = v; }
  public String getNotes() { return notes; }
  public void setNotes(String v) { notes = v; }
}
