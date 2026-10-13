package ml.dibidani.search;

import ml.dibidani.api.dto.SearchDtos.Suggestion;
import ml.dibidani.api.dto.SearchDtos.SuggestionResponse;
import ml.dibidani.api.dto.SearchDtos.SuggestionType;
import ml.dibidani.domain.Brand;
import ml.dibidani.domain.Category;
import ml.dibidani.domain.Product;
import ml.dibidani.domain.Vendor;
import ml.dibidani.repository.BrandRepository;
import ml.dibidani.repository.CategoryRepository;
import ml.dibidani.repository.ProductRepository;
import ml.dibidani.repository.VendorRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/** Recherche PostgreSQL (pg_trgm + unaccent), suffisante pour un catalogue de quelques dizaines de milliers de produits. */
@Component
@ConditionalOnProperty(name = "app.search.provider", havingValue = "postgres", matchIfMissing = true)
@Transactional(readOnly = true)
public class PostgresSearchProvider implements SearchProvider {
  private final ProductRepository products;
  private final CategoryRepository categories;
  private final BrandRepository brands;
  private final VendorRepository vendors;

  public PostgresSearchProvider(ProductRepository products, CategoryRepository categories, BrandRepository brands, VendorRepository vendors) {
    this.products = products;
    this.categories = categories;
    this.brands = brands;
    this.vendors = vendors;
  }

  @Override
  public Page<Long> searchProductIds(ProductFilter filter, Pageable pageable) {
    boolean relevance = pageable.getSort().isUnsorted() && filter.hasQuery();
    return products.findAll(ProductSpecs.of(filter, relevance), pageable).map(Product::getId);
  }

  @Override
  public SuggestionResponse suggest(String query, int limit) {
    String q = query == null ? "" : query.trim();
    if (q.length() < 2) return new SuggestionResponse(q, List.of(), List.of(), List.of(), List.of());
    int max = Math.max(1, Math.min(limit, 10));

    List<Product> productHits = products.findAll(ProductSpecs.of(ProductFilter.query(q), true), PageRequest.of(0, 20)).getContent();

    List<Suggestion> productSuggestions = productHits.stream().limit(max)
        .map(p -> new Suggestion(SuggestionType.PRODUCT, p.getId(), p.getName(),
            p.getBrand() != null ? p.getBrand().getName() : p.getCategory().getName(),
            "/product/" + p.getSlug(), image(p), p.getEffectivePrice()))
        .toList();

    Sort byName = Sort.by("name");
    List<Category> categoryHits = merge(
        categories.findAll(ProductSpecs.<Category>nameMatches(q, true), PageRequest.of(0, max, Sort.by("displayOrder", "name"))).getContent(),
        productHits.stream().flatMap(p -> Stream.of(p.getSubcategory(), p.getCategory())).filter(c -> c != null && c.isActive()).toList(),
        Category::getId, 4);
    List<Brand> brandHits = merge(
        brands.findAll(ProductSpecs.<Brand>nameMatches(q, true), PageRequest.of(0, max, byName)).getContent(),
        productHits.stream().map(Product::getBrand).filter(b -> b != null && b.isActive()).toList(),
        Brand::getId, 4);
    List<Vendor> vendorHits = merge(
        vendors.findAll(ProductSpecs.<Vendor>nameMatches(q, true), PageRequest.of(0, max, byName)).getContent(),
        productHits.stream().map(Product::getVendor).filter(v -> v != null && v.isActive()).toList(),
        Vendor::getId, 4);

    return new SuggestionResponse(q, productSuggestions,
        categoryHits.stream().map(c -> new Suggestion(SuggestionType.CATEGORY, c.getId(), c.getName(),
            c.getParent() != null ? c.getParent().getName() : "Catégorie", "/category/" + c.getSlug(), c.getImageUrl(), null)).toList(),
        brandHits.stream().map(b -> new Suggestion(SuggestionType.BRAND, b.getId(), b.getName(), "Marque",
            "/recherche?q=" + java.net.URLEncoder.encode(b.getName(), java.nio.charset.StandardCharsets.UTF_8) + "&brand=" + b.getId(), b.getLogoUrl(), null)).toList(),
        vendorHits.stream().map(v -> new Suggestion(SuggestionType.VENDOR, v.getId(), v.getName(),
            v.getDistrict() != null ? v.getDistrict() : "Vendeur", "/vendeurs/" + v.getSlug(), v.getLogoUrl(), null)).toList());
  }

  /** Correspondances directes d'abord, puis entités liées aux produits trouvés, sans doublon. */
  private static <T> List<T> merge(List<T> direct, List<T> related, Function<T, Long> id, int max) {
    Map<Long, T> out = new LinkedHashMap<>();
    Stream.concat(direct.stream(), related.stream()).forEach(t -> out.putIfAbsent(id.apply(t), t));
    return out.values().stream().limit(max).toList();
  }

  private static String image(Product p) {
    if (p.getMainImageUrl() != null && !p.getMainImageUrl().isBlank()) return p.getMainImageUrl();
    return p.getImages().stream().findFirst().orElse(null);
  }
}
