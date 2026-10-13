package ml.dibidani.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProductSpecification {
  @Column(nullable = false) private String label;
  @Column(name = "spec_value", nullable = false, length = 1000) private String value;

  public ProductSpecification() {}
  public ProductSpecification(String label, String value) { this.label = label; this.value = value; }

  public String getLabel() { return label; }
  public void setLabel(String v) { label = v; }
  public String getValue() { return value; }
  public void setValue(String v) { value = v; }
}
