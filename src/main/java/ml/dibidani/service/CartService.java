package ml.dibidani.service;

import ml.dibidani.api.dto.CartDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.domain.*;
import ml.dibidani.repository.CartRepository;
import ml.dibidani.repository.ProductRepository;
import ml.dibidani.service.PricingService.Line;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

/** Panier persistant des clients connectés. Les quantités ne sont pas plafonnées sur un stock d'entrepôt. */
@Service
@Transactional
public class CartService {
  private static final int MAX_LINES = 100;

  private final CartRepository carts;
  private final ProductRepository products;
  private final PricingService pricing;
  private final UserService users;

  public CartService(CartRepository carts, ProductRepository products, PricingService pricing, UserService users) {
    this.carts = carts;
    this.products = products;
    this.pricing = pricing;
    this.users = users;
  }

  public CartView view(String email) { return view(email, null, null); }

  public CartView view(String email, Long zoneId, DeliveryMethod method) {
    Cart cart = cartOf(email);
    CartView first = pricing.quote(lines(cart), cart.getPromoCode(), zoneId, method);
    boolean corrected = false;
    for (CartLine l : first.items()) {
      if (l.status() == LineStatus.QUANTITY_ADJUSTED && l.itemId() != null) {
        cart.getItems().stream().filter(i -> i.getId().equals(l.itemId())).findFirst().ifPresent(i -> i.setQuantity(l.quantity()));
        corrected = true;
      }
    }
    if (cart.getPromoCode() != null && first.promo() != null && !first.promo().applied()) {
      cart.setPromoCode(null);
      corrected = true;
    }
    if (!corrected) return first;
    CartView fresh = pricing.quote(lines(cart), cart.getPromoCode(), zoneId, method);
    return withWarnings(fresh, first.warnings(), first.promo());
  }

  public CartView add(String email, LineRequest r) {
    Cart cart = cartOf(email);
    Product p = products.findByIdAndActiveTrue(r.productId()).orElseThrow(() -> new NotFoundException("Produit introuvable"));
    ProductVariant variant = resolveVariant(p, r.variantId());

    CartItem item = cart.getItems().stream().filter(i -> i.matches(p.getId(), variant == null ? null : variant.getId())).findFirst().orElse(null);
    int wanted = (item == null ? 0 : item.getQuantity()) + r.quantity();
    if (item == null) {
      if (cart.getItems().size() >= MAX_LINES) throw new ConflictException("Votre panier est plein (" + MAX_LINES + " articles différents maximum)");
      item = new CartItem();
      item.setCart(cart);
      item.setProduct(p);
      item.setVariant(variant);
      cart.getItems().add(item);
    }
    item.setQuantity(Math.min(wanted, 999));
    carts.save(cart);
    return view(email);
  }

  public CartView update(String email, Long itemId, int quantity) {
    Cart cart = cartOf(email);
    CartItem item = item(cart, itemId);
    item.setQuantity(Math.min(quantity, 999));
    return view(email);
  }

  public CartView remove(String email, Long itemId) {
    Cart cart = cartOf(email);
    cart.getItems().remove(item(cart, itemId));
    return view(email);
  }

  public CartView removeUnavailable(String email) {
    Cart cart = cartOf(email);
    cart.getItems().removeIf(i -> {
      Product p = i.getProduct();
      ProductVariant v = i.getVariant();
      boolean variantMissing = v == null && p.getVariants().stream().anyMatch(ProductVariant::isActive);
      return !p.isActive() || (v != null && !v.isActive()) || variantMissing;
    });
    return view(email);
  }

  public CartView clear(String email) {
    Cart cart = cartOf(email);
    cart.getItems().clear();
    cart.setPromoCode(null);
    return view(email);
  }

  public CartView applyPromo(String email, String code) {
    Cart cart = cartOf(email);
    CartView check = pricing.quote(lines(cart), code, null, null);
    if (check.promo() == null || !check.promo().applied()) {
      throw new IllegalArgumentException(check.promo() == null ? "Code promo invalide" : check.promo().message());
    }
    cart.setPromoCode(PromoCodeService.normalize(code));
    return view(email);
  }

  public CartView removePromo(String email) {
    cartOf(email).setPromoCode(null);
    return view(email);
  }

  /**
   * Fusion à la connexion : pour une même ligne on garde la plus grande des deux quantités (le visiteur a souvent
   * déjà ajouté les mêmes articles sur un autre appareil) ; les lignes invalides sont ignorées.
   */
  public CartView merge(String email, MergeRequest r) {
    Cart cart = cartOf(email);
    List<String> warnings = new ArrayList<>();
    if (r.items() != null) {
      for (LineRequest l : r.items()) {
        Product p = products.findByIdAndActiveTrue(l.productId()).orElse(null);
        if (p == null) continue;
        ProductVariant variant;
        try {
          variant = resolveVariant(p, l.variantId());
        } catch (RuntimeException e) {
          continue;
        }
        CartItem item = cart.getItems().stream().filter(i -> i.matches(p.getId(), variant == null ? null : variant.getId())).findFirst().orElse(null);
        if (item == null) {
          if (cart.getItems().size() >= MAX_LINES) break;
          item = new CartItem();
          item.setCart(cart);
          item.setProduct(p);
          item.setVariant(variant);
          cart.getItems().add(item);
        }
        item.setQuantity(Math.min(999, Math.max(item.getQuantity(), l.quantity())));
      }
    }
    if (cart.getPromoCode() == null && r.promoCode() != null && !r.promoCode().isBlank()) cart.setPromoCode(PromoCodeService.normalize(r.promoCode()));
    carts.save(cart);
    CartView v = view(email);
    return warnings.isEmpty() ? v : withWarnings(v, warnings, v.promo());
  }

  /** Lignes du panier pour la création de commande. */
  public List<Line> lines(Cart cart) {
    return cart.getItems().stream()
        .map(i -> new Line(i.getId(), i.getProduct().getId(), i.getVariant() == null ? null : i.getVariant().getId(), i.getQuantity()))
        .toList();
  }

  public Cart cartOf(String email) {
    AppUser user = users.byEmail(email);
    return carts.findByUserId(user.getId()).orElseGet(() -> {
      var c = new Cart();
      c.setUser(user);
      return carts.save(c);
    });
  }

  public void empty(Cart cart) {
    cart.getItems().clear();
    cart.setPromoCode(null);
  }

  private static CartItem item(Cart cart, Long itemId) {
    return cart.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
        .orElseThrow(() -> new NotFoundException("Article introuvable dans votre panier"));
  }

  private static ProductVariant resolveVariant(Product p, Long variantId) {
    boolean hasVariants = p.getVariants().stream().anyMatch(ProductVariant::isActive);
    if (variantId == null) {
      if (hasVariants) throw new IllegalArgumentException("Choisissez une variante pour « " + p.getName() + " »");
      return null;
    }
    return p.getVariants().stream().filter(v -> v.getId().equals(variantId) && v.isActive()).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Variante indisponible pour « " + p.getName() + " »"));
  }

  private static CartView withWarnings(CartView v, List<String> extra, PromoView promo) {
    List<String> all = new ArrayList<>(extra);
    v.warnings().stream().filter(w -> !all.contains(w)).forEach(all::add);
    return new CartView(v.items(), v.itemCount(), v.subtotal(), v.discount(), v.deliveryFee(), v.total(), promo != null ? promo : v.promo(),
        v.delivery(), v.freeDeliveryThreshold(), v.remainingForFreeDelivery(), all, v.canCheckout());
  }
}
