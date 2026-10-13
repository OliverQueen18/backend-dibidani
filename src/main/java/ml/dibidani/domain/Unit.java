package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "units")
public class Unit extends BaseEntity {
  @Column(nullable = false, unique = true) private String name;
  private String symbol;
  @Column(nullable = false) private boolean active = true;
  @Column(nullable = false) private int displayOrder = 0;

  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public String getSymbol() { return symbol; }
  public void setSymbol(String v) { symbol = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
  public int getDisplayOrder() { return displayOrder; }
  public void setDisplayOrder(int v) { displayOrder = v; }
}
