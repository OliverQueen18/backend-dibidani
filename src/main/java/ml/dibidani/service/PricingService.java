package ml.dibidani.service;

import ml.dibidani.api.dto.CartDtos.*;
import ml.dibidani.api.dto.VendorDtos.VendorRef;
import ml.dibidani.domain.*;
import ml.dibidani.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seule source de vérité des montants : panier, checkout et création de commande passent tous par ici.
 * Prix et disponibilité (produit actif) sont relus en base à chaque appel. Le stock n'est pas un entrepôt Dibidani.
 */
@Service
@Transactional(readOnly = true)
public class PricingService {
  private final ProductRepository products;
  private final PromoCodeService promos;
  private final DeliveryService delivery;
  private final ShopService shop;

  public PricingService(ProductRepository products, PromoCodeService promos, DeliveryService delivery, ShopService shop) {
    this.products = products;
    this.promos = promos;
    this.delivery = delivery;
    this.shop = shop;
  }

  /** Ligne demandée ; itemId est l'id de ligne d'un panier persistant (null pour un visiteur). */
  public record Line(Long itemId, Long productId, Long variantId, int quantity) {}

  /** Ligne tarifée avec ses entités, utilisée par la création de commande. */
  public record PricedLine(CartLine view, Product product, ProductVariant variant) {}

  public record Priced(CartView view, List<PricedLine> lines, PromoCode promo) {}

  public CartView quote(List<Line> lines, String promoCode, Long zoneId, DeliveryMethod method) {
    return price(lines, promoCode, zoneId, method).view();
  }

  public Priced price(List<Line> lines, String promoCode, Long zoneId, DeliveryMethod method) {
    List<Line> merged = mergeDuplicates(lines);
    Map<Long, Product> byId = products.findAllById(merged.stream().map(Line::productId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(Product::getId, Function.identity()));

    List<PricedLine> priced = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    BigDecimal subtotal = BigDecimal.ZERO;
    int count = 0;
    for (Line l : merged) {
      PricedLine pl = priceLine(l, byId.get(l.productId()));
      priced.add(pl);
      CartLine v = pl.view();
      if (v.status() != LineStatus.AVAILABLE) warnings.add(v.message());
      if (v.status() == LineStatus.AVAILABLE || v.status() == LineStatus.QUANTITY_ADJUSTED) {
        subtotal = subtotal.add(v.lineTotal());
        count += v.quantity();
      }
    }

    PromoCode promo = null;
    PromoView promoView = null;
    BigDecimal discount = BigDecimal.ZERO;
    if (promoCode != null && !promoCode.isBlank()) {
      var check = promos.evaluate(promoCode, subtotal, Instant.now());
      promoView = check.view();
      if (check.promo() != null) {
        promo = check.promo();
        discount = check.discount();
      }
    }

    BigDecimal net = subtotal.subtract(discount).max(BigDecimal.ZERO);
    boolean freeByPromo = promo != null && promo.getType() == PromoType.FREE_DELIVERY;
    DeliveryQuote quote = method == null && zoneId == null ? null : delivery.quote(zoneId, method == null ? DeliveryMethod.STANDARD : method, net, freeByPromo);
    BigDecimal fee = quote == null || quote.fee() == null ? BigDecimal.ZERO : quote.fee();

    BigDecimal threshold = shop.freeDeliveryThreshold();
    BigDecimal remaining = threshold == null ? null : threshold.subtract(net).max(BigDecimal.ZERO);
    boolean hasSellable = priced.stream().anyMatch(p -> p.view().status() == LineStatus.AVAILABLE);
    boolean allClean = priced.stream().allMatch(p -> p.view().status() == LineStatus.AVAILABLE);

    var view = new CartView(priced.stream().map(PricedLine::view).toList(), count, subtotal, discount, quote == null ? null : quote.fee(),
        net.add(fee), promoView, quote, threshold, remaining, warnings, hasSellable && allClean);
    return new Priced(view, priced, promo);
  }

  private PricedLine priceLine(Line l, Product p) {
    if (p == null) {
      return new PricedLine(new CartLine(l.itemId(), l.productId(), l.variantId(), null, "Produit supprimé", null, null, null, null, null,
          BigDecimal.ZERO, BigDecimal.ZERO, false, 0, l.quantity(), 0, BigDecimal.ZERO, LineStatus.UNAVAILABLE,
          "Un produit de votre panier n'existe plus."), null, null);
    }
    ProductVariant variant = null;
    String problem = null;
    if (!p.isActive()) problem = "« " + p.getName() + " » n'est plus disponible.";
    if (problem == null && l.variantId() != null) {
      variant = p.getVariants().stream().filter(v -> v.getId().equals(l.variantId())).findFirst().orElse(null);
      if (variant == null || !variant.isActive()) problem = "La variante choisie pour « " + p.getName() + " » n'est plus disponible.";
    }
    if (problem == null && l.variantId() == null && p.getVariants().stream().anyMatch(ProductVariant::isActive)) {
      problem = "Choisissez une variante pour « " + p.getName() + " ».";
    }

    BigDecimal regular = variant != null && variant.getPrice() != null ? variant.getPrice() : p.getPrice();
    BigDecimal unit = variant != null && variant.getPrice() != null ? variant.getPrice() : p.getEffectivePrice();
    boolean promo = unit.compareTo(regular) < 0;
    int stock = Math.max(0, variant != null ? variant.getStock() : p.getStock());
    var vendor = VendorRef.of(p.getVendor());
    String image = p.getMainImageUrl() != null && !p.getMainImageUrl().isBlank() ? p.getMainImageUrl()
        : p.getImages().stream().filter(i -> i != null && !i.isBlank()).findFirst().orElse(null);

    LineStatus status;
    int qty;
    String message = null;
    if (problem != null) {
      status = LineStatus.UNAVAILABLE;
      qty = 0;
      message = problem;
    } else {
      status = LineStatus.AVAILABLE;
      qty = l.quantity();
      message = "Disponibilité à confirmer auprès du vendeur.";
    }
    BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
    var view = new CartLine(l.itemId(), p.getId(), variant == null ? null : variant.getId(), p.getSlug(), p.getName(),
        variant != null && variant.getSku() != null ? variant.getSku() : p.getSku(), variant == null ? null : variant.getName(),
        image, vendor, p.getUnit(), unit, regular, promo, qty, l.quantity(), stock, lineTotal, status, message);
    return new PricedLine(view, p, variant);
  }

  private static List<Line> mergeDuplicates(List<Line> lines) {
    Map<String, Line> merged = new LinkedHashMap<>();
    for (Line l : lines) {
      if (l == null || l.productId() == null || l.quantity() <= 0) continue;
      merged.merge(l.productId() + ":" + l.variantId(), l,
          (a, b) -> new Line(a.itemId(), a.productId(), a.variantId(), Math.min(999, a.quantity() + b.quantity())));
    }
    return new ArrayList<>(merged.values());
  }
}
