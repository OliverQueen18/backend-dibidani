package ml.dibidani.api;

import ml.dibidani.search.ProductFilter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Paramètres de requête du catalogue liés depuis l'URL (les produits inactifs ne sont jamais exposés ici). */
public record ProductQuery(
    String q, String category, String brand, String vendor, BigDecimal minPrice, BigDecimal maxPrice,
    Boolean inStock, Boolean promo, Boolean newArrival, Boolean featured, Boolean active, String sort, Integer page, Integer size) {

  public ProductFilter toFilter(boolean includeInactive) {
    return new ProductFilter(q, category, parseLongs(brand), parseCsv(vendor), minPrice, maxPrice, inStock, promo, newArrival, featured, sort, includeInactive, active);
  }

  public int pageOrDefault() { return page == null ? 0 : page; }
  public int sizeOrDefault() { return size == null ? 24 : size; }

  private static List<Long> parseLongs(String raw) {
    List<Long> out = new ArrayList<>();
    for (String part : parseCsv(raw)) {
      try { out.add(Long.parseLong(part)); } catch (NumberFormatException ignored) {}
    }
    return out;
  }

  private static List<String> parseCsv(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    List<String> out = new ArrayList<>();
    for (String part : raw.split(",")) {
      String t = part.trim();
      if (!t.isEmpty()) out.add(t);
    }
    return out;
  }
}
