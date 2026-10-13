package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.CartDtos.*;
import ml.dibidani.domain.DeliveryMethod;
import ml.dibidani.service.CartService;
import ml.dibidani.service.PricingService;
import ml.dibidani.service.PricingService.Line;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
  private final CartService carts;
  private final PricingService pricing;

  public CartController(CartService carts, PricingService pricing) {
    this.carts = carts;
    this.pricing = pricing;
  }

  /** Panier visiteur (localStorage) : le serveur renvoie prix et disponibilité à jour, sans rien enregistrer. */
  @PostMapping("/quote")
  public CartView quote(@Valid @RequestBody QuoteRequest r) {
    var lines = r.items().stream().map(i -> new Line(null, i.productId(), i.variantId(), i.quantity())).toList();
    return pricing.quote(lines, r.promoCode(), r.deliveryZoneId(), r.deliveryMethod());
  }

  @GetMapping
  public CartView get(Authentication a, @RequestParam(required = false) Long zoneId, @RequestParam(required = false) DeliveryMethod method) {
    return carts.view(a.getName(), zoneId, method);
  }

  @PostMapping("/items")
  public CartView add(Authentication a, @Valid @RequestBody LineRequest r) { return carts.add(a.getName(), r); }

  @PutMapping("/items/{itemId}")
  public CartView update(Authentication a, @PathVariable Long itemId, @Valid @RequestBody UpdateItemRequest r) { return carts.update(a.getName(), itemId, r.quantity()); }

  @DeleteMapping("/items/{itemId}")
  public CartView remove(Authentication a, @PathVariable Long itemId) { return carts.remove(a.getName(), itemId); }

  @DeleteMapping("/unavailable")
  public CartView removeUnavailable(Authentication a) { return carts.removeUnavailable(a.getName()); }

  @DeleteMapping
  public CartView clear(Authentication a) { return carts.clear(a.getName()); }

  @PostMapping("/merge")
  public CartView merge(Authentication a, @Valid @RequestBody MergeRequest r) { return carts.merge(a.getName(), r); }

  @PostMapping("/promo")
  public CartView applyPromo(Authentication a, @Valid @RequestBody PromoRequest r) { return carts.applyPromo(a.getName(), r.code()); }

  @DeleteMapping("/promo")
  public CartView removePromo(Authentication a) { return carts.removePromo(a.getName()); }
}
