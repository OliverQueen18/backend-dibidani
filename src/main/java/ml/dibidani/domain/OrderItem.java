package ml.dibidani.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Ligne de commande : nom, SKU, prix et vendeur sont figés au moment de l'achat. */
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private CustomerOrder order;
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private Product product;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "variant_id") private ProductVariant variant;
  @Column(nullable = false) private String productName;
  @Column(nullable = false) private String productSku;
  private String variantName;
  private Long vendorId;
  private String vendorName;
  private String imageUrl;
  @Column(nullable = false) private Integer quantity;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal lineTotal;

  public CustomerOrder getOrder() { return order; }
  public void setOrder(CustomerOrder v) { order = v; }
  public Product getProduct() { return product; }
  public void setProduct(Product v) { product = v; }
  public ProductVariant getVariant() { return variant; }
  public void setVariant(ProductVariant v) { variant = v; }
  public String getProductName() { return productName; }
  public void setProductName(String v) { productName = v; }
  public String getProductSku() { return productSku; }
  public void setProductSku(String v) { productSku = v; }
  public String getVariantName() { return variantName; }
  public void setVariantName(String v) { variantName = v; }
  public Long getVendorId() { return vendorId; }
  public void setVendorId(Long v) { vendorId = v; }
  public String getVendorName() { return vendorName; }
  public void setVendorName(String v) { vendorName = v; }
  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String v) { imageUrl = v; }
  public Integer getQuantity() { return quantity; }
  public void setQuantity(Integer v) { quantity = v; }
  public BigDecimal getUnitPrice() { return unitPrice; }
  public void setUnitPrice(BigDecimal v) { unitPrice = v; }
  public BigDecimal getLineTotal() { return lineTotal; }
  public void setLineTotal(BigDecimal v) { lineTotal = v; }
}
