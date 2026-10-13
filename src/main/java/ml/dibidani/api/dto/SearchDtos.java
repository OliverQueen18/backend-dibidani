package ml.dibidani.api.dto;

import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import java.math.BigDecimal;
import java.util.List;

public final class SearchDtos {
  private SearchDtos() {}

  public enum SuggestionType { PRODUCT, CATEGORY, BRAND, VENDOR }

  /** Une suggestion d'autocomplétion ; {@code url} est la route Angular à ouvrir. */
  public record Suggestion(SuggestionType type, Long id, String label, String sublabel, String url, String imageUrl, BigDecimal price) {}

  public record SuggestionResponse(String query, List<Suggestion> products, List<Suggestion> categories, List<Suggestion> brands, List<Suggestion> vendors) {
    public boolean isEmpty() { return products.isEmpty() && categories.isEmpty() && brands.isEmpty() && vendors.isEmpty(); }
  }

  public record SearchResponse(String query, PageResponse<ProductSummary> products, SuggestionResponse related) {}
}
