package ml.dibidani.service;

import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.StockDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.domain.*;
import ml.dibidani.repository.AppUserRepository;
import ml.dibidani.repository.ProductRepository;
import ml.dibidani.repository.StockMovementRepository;
import ml.dibidani.search.SearchText;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Point unique de mutation du stock. Une vente, un retour, une casse ou un inventaire
 * passent tous ici : le journal {@code stock_movements} est écrit dans la même transaction.
 */
@Service
@Transactional(readOnly = true)
public class StockService {
  private final ProductRepository products;
  private final StockMovementRepository movements;
  private final AppUserRepository users;

  public StockService(ProductRepository products, StockMovementRepository movements, AppUserRepository users) {
    this.products = products;
    this.movements = movements;
    this.users = users;
  }

  /**
   * Applique une variation signée sur le produit (et sa variante le cas échéant) puis journalise
   * quantité avant, mouvement et quantité après. Interdit un stock négatif.
   */
  @Transactional
  public StockMovement move(Product p, ProductVariant v, StockMovementType type, int delta, String comment, String reference, String by) {
    if (delta == 0) throw new IllegalArgumentException("Aucune variation de stock");
    int before = v != null ? v.getStock() : p.getStock();
    int after = before + delta;
    if (after < 0) {
      String name = p.getName() + (v == null ? "" : " (" + v.getName() + ")");
      throw new ConflictException("Stock insuffisant pour " + name);
    }
    if (v != null) {
      v.setStock(after);
      p.setStock(Math.max(0, p.getStock() + delta));
    } else {
      p.setStock(after);
    }
    return record(p, v, type, before, delta, after, comment, reference, by);
  }

  /** Trace une variation déjà appliquée (fiche produit). Même contrat : avant, mouvement, après. */
  @Transactional
  public StockMovement log(Product p, ProductVariant v, StockMovementType type, int before, int delta, int after, String comment, String reference, String by) {
    if (delta == 0) return null;
    return record(p, v, type, before, delta, Math.max(0, after), comment, reference, by);
  }

  @Transactional
  public StockRow adjust(AdjustRequest r, String by) {
    if (!r.type().manual()) throw new IllegalArgumentException("Le type « " + r.type().label() + " » est réservé au système (commandes)");
    Product p = products.lockById(r.productId()).orElseThrow(() -> new NotFoundException("Produit introuvable"));
    ProductVariant v = r.variantId() == null ? null : p.getVariants().stream().filter(x -> x.getId().equals(r.variantId())).findFirst()
        .orElseThrow(() -> new NotFoundException("Variante introuvable"));
    int current = v != null ? v.getStock() : p.getStock();
    int delta = switch (r.type()) {
      case PURCHASE, RETURN -> Math.abs(r.quantity());
      case LOSS, DAMAGE -> -Math.abs(r.quantity());
      case ADJUSTMENT -> r.quantity() - current;
      default -> throw new IllegalArgumentException("Type de mouvement réservé au système");
    };
    if (r.type() == StockMovementType.ADJUSTMENT && r.quantity() < 0) throw new IllegalArgumentException("Le stock ne peut pas être négatif");
    if (delta == 0) throw new IllegalArgumentException("Aucune variation de stock");
    move(p, v, r.type(), delta, r.comment().trim(), blank(r.reference()), by);
    return StockRow.of(p);
  }

  @Transactional
  public StockRow setThreshold(Long productId, int threshold) {
    Product p = products.findById(productId).orElseThrow(() -> new NotFoundException("Produit introuvable"));
    p.setLowStockThreshold(threshold);
    return StockRow.of(p);
  }

  public PageResponse<StockRow> page(String q, String filter, int page, int size) {
    Specification<Product> spec = (root, query, cb) -> {
      List<Predicate> and = new ArrayList<>();
      if ("low".equals(filter)) and.add(cb.and(cb.greaterThan(root.get("stock"), 0), cb.lessThanOrEqualTo(root.get("stock"), root.get("lowStockThreshold"))));
      else if ("out".equals(filter)) and.add(cb.lessThanOrEqualTo(root.get("stock"), 0));
      if ("inactive".equals(filter)) and.add(cb.isFalse(root.get("active")));
      else if (filter != null && !filter.isBlank() && !"all".equals(filter) && !"low".equals(filter) && !"out".equals(filter)) and.add(cb.isTrue(root.get("active")));
      else if ("low".equals(filter) || "out".equals(filter)) and.add(cb.isTrue(root.get("active")));
      if (q != null && !q.isBlank()) {
        for (String term : SearchText.terms(q)) {
          String like = SearchText.contains(term);
          and.add(cb.or(cb.like(SearchText.norm(cb, root.get("name")), like, SearchText.ESCAPE), cb.like(cb.lower(root.get("sku")), like, SearchText.ESCAPE)));
        }
      }
      return cb.and(and.toArray(Predicate[]::new));
    };
    var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Order.asc("stock"), Sort.Order.asc("name")));
    return PageResponse.of(products.findAll(spec, pageable).map(StockRow::of));
  }

  public PageResponse<MovementResponse> movements(Long productId, StockMovementType type, Instant from, Instant to, int page, int size) {
    Specification<StockMovement> spec = (root, query, cb) -> {
      if (query != null) query.distinct(true);
      List<Predicate> and = new ArrayList<>();
      if (productId != null) and.add(cb.equal(root.get("product").get("id"), productId));
      if (type != null) and.add(cb.equal(root.get("type"), type));
      if (from != null) and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
      if (to != null) and.add(cb.lessThan(root.get("createdAt"), to));
      return cb.and(and.toArray(Predicate[]::new));
    };
    var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    return PageResponse.of(movements.findAll(spec, pageable).map(MovementResponse::of));
  }

  public StockOverview overview() {
    long productsCount = products.countByActiveTrue();
    long out = products.countByActiveTrueAndStockLessThanEqual(0);
    long low = products.countLowStock();
    Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
    List<TypeStat> stats = movements.totalsByTypeSince(since).stream()
        .map(row -> {
          var t = (StockMovementType) row[0];
          return new TypeStat(t, t.label(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue());
        }).toList();
    var types = Arrays.stream(StockMovementType.values()).map(TypeOption::of).toList();
    return new StockOverview(productsCount, Math.max(0, productsCount - out), low, out, stats, types);
  }

  public List<TypeOption> types() { return Arrays.stream(StockMovementType.values()).map(TypeOption::of).toList(); }

  private StockMovement record(Product p, ProductVariant v, StockMovementType type, int before, int delta, int after, String comment, String reference, String by) {
    var m = new StockMovement();
    m.setProduct(p);
    m.setVariant(v);
    m.setType(type);
    m.setQuantityBefore(before);
    m.setQuantity(delta);
    m.setStockAfter(after);
    m.setComment(comment);
    m.setReference(reference);
    m.setCreatedBy(by);
    if (by != null && by.contains("@")) users.findByEmailIgnoreCase(by).ifPresent(m::setUser);
    return movements.save(m);
  }

  private static String blank(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
