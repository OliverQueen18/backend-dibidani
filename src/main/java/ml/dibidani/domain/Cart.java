package ml.dibidani.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts")
public class Cart extends BaseEntity {
  @OneToOne(optional = false, fetch = FetchType.LAZY) private AppUser user;
  private String promoCode;
  @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true) @BatchSize(size = 50)
  @OrderBy("createdAt asc, id asc")
  private List<CartItem> items = new ArrayList<>();

  public AppUser getUser() { return user; }
  public void setUser(AppUser v) { user = v; }
  public String getPromoCode() { return promoCode; }
  public void setPromoCode(String v) { promoCode = v; }
  public List<CartItem> getItems() { return items; }
}
