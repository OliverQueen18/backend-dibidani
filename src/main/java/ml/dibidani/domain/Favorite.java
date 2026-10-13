package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "favorites")
public class Favorite extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private AppUser user;
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private Product product;

  public AppUser getUser() { return user; }
  public void setUser(AppUser v) { user = v; }
  public Product getProduct() { return product; }
  public void setProduct(Product v) { product = v; }
}
