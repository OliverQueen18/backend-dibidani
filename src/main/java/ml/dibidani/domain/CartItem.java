package ml.dibidani.domain;

import jakarta.persistence.*;
import java.util.Objects;

/** Ne stocke aucun prix : il est toujours recalculé depuis le produit. */
@Entity
@Table(name = "cart_items")
public class CartItem extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private Cart cart;
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private Product product;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "variant_id") private ProductVariant variant;
  @Column(nullable = false) private int quantity;

  public boolean matches(Long productId, Long variantId) {
    return product.getId().equals(productId) && Objects.equals(variant == null ? null : variant.getId(), variantId);
  }

  public Cart getCart() { return cart; }
  public void setCart(Cart v) { cart = v; }
  public Product getProduct() { return product; }
  public void setProduct(Product v) { product = v; }
  public ProductVariant getVariant() { return variant; }
  public void setVariant(ProductVariant v) { variant = v; }
  public int getQuantity() { return quantity; }
  public void setQuantity(int v) { quantity = v; }
}
