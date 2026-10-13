package ml.dibidani.domain;

import jakarta.persistence.*;

/** Ligne immuable du journal de stock : toute variation du stock d'un produit ou d'une variante en crée une. */
@Entity
@Table(name = "stock_movements")
public class StockMovement extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private Product product;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "variant_id") private ProductVariant variant;
  private String variantName;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private StockMovementType type;
  @Column(nullable = false) private int quantityBefore;
  /** Variation signée : positive pour une entrée, négative pour une sortie. */
  @Column(nullable = false) private int quantity;
  @Column(nullable = false) private int stockAfter;
  @Column(length = 1000) private String comment;
  private String reference;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private AppUser user;
  private String createdBy;

  public Product getProduct() { return product; }
  public void setProduct(Product v) { product = v; }
  public ProductVariant getVariant() { return variant; }
  public void setVariant(ProductVariant v) { variant = v; variantName = v == null ? null : v.getName(); }
  public String getVariantName() { return variantName; }
  public StockMovementType getType() { return type; }
  public void setType(StockMovementType v) { type = v; }
  public int getQuantityBefore() { return quantityBefore; }
  public void setQuantityBefore(int v) { quantityBefore = v; }
  public int getQuantity() { return quantity; }
  public void setQuantity(int v) { quantity = v; }
  public int getStockAfter() { return stockAfter; }
  public void setStockAfter(int v) { stockAfter = v; }
  public String getComment() { return comment; }
  public void setComment(String v) { comment = v; }
  public String getReference() { return reference; }
  public void setReference(String v) { reference = v; }
  public AppUser getUser() { return user; }
  public void setUser(AppUser v) { user = v; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String v) { createdBy = v; }
}
