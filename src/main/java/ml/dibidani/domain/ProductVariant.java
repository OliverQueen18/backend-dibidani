package ml.dibidani.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
public class ProductVariant extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private Product product;
  @Column(nullable = false) private String name;
  @Column(unique = true) private String sku;
  /** Prix propre à la variante ; à défaut, le prix effectif du produit s'applique. */
  @Column(precision = 14, scale = 2) private BigDecimal price;
  @Column(nullable = false) private Integer stock = 0;
  @Column(nullable = false) private boolean active = true;
  @Column(nullable = false) private Integer position = 0;

  public Product getProduct() { return product; }
  public void setProduct(Product v) { product = v; }
  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public String getSku() { return sku; }
  public void setSku(String v) { sku = v; }
  public BigDecimal getPrice() { return price; }
  public void setPrice(BigDecimal v) { price = v; }
  public Integer getStock() { return stock; }
  public void setStock(Integer v) { stock = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
  public Integer getPosition() { return position; }
  public void setPosition(Integer v) { position = v; }
}
