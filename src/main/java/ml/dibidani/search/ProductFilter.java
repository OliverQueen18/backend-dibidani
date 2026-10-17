package ml.dibidani.search;

import java.math.BigDecimal;
import java.util.List;

/**
 * Critères du catalogue indépendants du moteur de recherche.
 * {@code category} accepte une catégorie racine (inclut ses sous-catégories) ou une sous-catégorie.
 */
public record ProductFilter(
    String q,
    String category,
    List<Long> brandIds,
    List<String> vendors,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Boolean inStock,
    Boolean promo,
    Boolean newArrival,
    Boolean featured,
    String sort,
    boolean includeInactive,
    Boolean active) {

  public ProductFilter {
    brandIds = brandIds == null ? List.of() : List.copyOf(brandIds);
    vendors = vendors == null ? List.of() : List.copyOf(vendors);
  }

  public static ProductFilter query(String q) {
    return new ProductFilter(q, null, List.of(), List.of(), null, null, null, null, null, null, null, false, null);
  }

  public ProductFilter withCategory(String slug) {
    return new ProductFilter(q, slug, brandIds, vendors, minPrice, maxPrice, inStock, promo, newArrival, featured, sort, includeInactive, active);
  }

  public ProductFilter withVendor(String slug) {
    List<String> next = slug == null || slug.isBlank() ? List.of() : List.of(slug.trim());
    return new ProductFilter(q, category, brandIds, next, minPrice, maxPrice, inStock, promo, newArrival, featured, sort, includeInactive, active);
  }

  public boolean hasQuery() { return q != null && !q.isBlank(); }
}
