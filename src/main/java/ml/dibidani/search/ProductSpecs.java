package ml.dibidani.search;

import jakarta.persistence.criteria.*;
import ml.dibidani.domain.Brand;
import ml.dibidani.domain.Category;
import ml.dibidani.domain.Product;
import ml.dibidani.domain.Vendor;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.List;

import static ml.dibidani.search.SearchText.*;

public final class ProductSpecs {
  private ProductSpecs() {}

  public static final String SORT_RELEVANCE = "relevance";

  public static Specification<Product> of(ProductFilter f, boolean relevanceOrder) {
    return (root, query, cb) -> {
      Join<Product, Category> category = root.join("category", JoinType.INNER);
      Join<Category, Category> parent = category.join("parent", JoinType.LEFT);
      Join<Product, Category> sub = root.join("subcategory", JoinType.LEFT);
      Join<Product, Brand> brand = root.join("brand", JoinType.LEFT);
      Join<Product, Vendor> vendor = root.join("vendor", JoinType.LEFT);

      List<Predicate> and = new ArrayList<>();
      if (!f.includeInactive()) and.add(cb.isTrue(root.get("active")));
      if (f.category() != null && !f.category().isBlank()) {
        String slug = f.category().trim();
        and.add(cb.or(cb.equal(category.get("slug"), slug), cb.equal(sub.get("slug"), slug), cb.equal(parent.get("slug"), slug)));
      }
      if (f.brandIds() != null && !f.brandIds().isEmpty()) and.add(brand.get("id").in(f.brandIds()));
      if (f.vendors() != null && !f.vendors().isEmpty()) and.add(vendor.get("slug").in(f.vendors()));

      Expression<java.math.BigDecimal> effectivePrice = cb.<java.math.BigDecimal>selectCase()
          .when(cb.and(cb.isNotNull(root.get("promoPrice")), cb.lessThan(root.get("promoPrice"), root.get("price"))), root.get("promoPrice"))
          .otherwise(root.get("price"));
      if (f.minPrice() != null) and.add(cb.greaterThanOrEqualTo(effectivePrice, f.minPrice()));
      if (f.maxPrice() != null) and.add(cb.lessThanOrEqualTo(effectivePrice, f.maxPrice()));
      // Dibidani ne gère pas d'entrepôt : « en stock » = produit actif, déjà filtré plus haut.
      if (Boolean.TRUE.equals(f.promo())) and.add(cb.and(cb.isNotNull(root.get("promoPrice")), cb.lessThan(root.get("promoPrice"), root.get("price"))));
      if (Boolean.TRUE.equals(f.newArrival())) and.add(cb.isTrue(root.get("newArrival")));
      if (Boolean.TRUE.equals(f.featured())) and.add(cb.isTrue(root.get("featured")));

      List<String> terms = f.hasQuery() ? terms(f.q()) : List.of();
      for (String term : terms) {
        String like = contains(term);
        and.add(cb.or(
            cb.like(norm(cb, root.get("name")), like, ESCAPE),
            cb.like(cb.lower(root.get("sku")), like, ESCAPE),
            cb.like(normNullable(cb, root.get("barcode")), like, ESCAPE),
            cb.like(normNullable(cb, root.get("oemReference")), like, ESCAPE),
            cb.like(normNullable(cb, root.get("manufacturerReference")), like, ESCAPE),
            cb.like(normNullable(cb, root.get("shortDescription")), like, ESCAPE),
            cb.like(normNullable(cb, root.get("description")), like, ESCAPE),
            cb.like(norm(cb, brand.get("name")), like, ESCAPE),
            cb.like(norm(cb, vendor.get("name")), like, ESCAPE),
            cb.like(norm(cb, category.get("name")), like, ESCAPE),
            cb.like(norm(cb, sub.get("name")), like, ESCAPE)));
      }

      boolean isCountQuery = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
      if (relevanceOrder && !terms.isEmpty() && !isCountQuery) {
        String first = terms.getFirst();
        Expression<Integer> rank = cb.<Integer>selectCase()
            .when(cb.like(norm(cb, root.get("name")), prefix(first), ESCAPE), 0)
            .when(cb.like(norm(cb, root.get("name")), contains(first), ESCAPE), 1)
            .when(cb.like(cb.lower(root.get("sku")), contains(first), ESCAPE), 2)
            .when(cb.like(normNullable(cb, root.get("barcode")), contains(first), ESCAPE), 2)
            .when(cb.like(normNullable(cb, root.get("oemReference")), contains(first), ESCAPE), 2)
            .when(cb.like(normNullable(cb, root.get("manufacturerReference")), contains(first), ESCAPE), 2)
            .otherwise(3);
        query.orderBy(cb.asc(rank), cb.desc(root.get("bestSeller")), cb.asc(root.get("name")));
      }
      return cb.and(and.toArray(Predicate[]::new));
    };
  }

  /** Recherche sur le nom pour les entités simples (catégories, marques, vendeurs). */
  public static <T> Specification<T> nameMatches(String q, boolean activeOnly) {
    return (root, query, cb) -> {
      List<Predicate> and = new ArrayList<>();
      if (activeOnly) and.add(cb.isTrue(root.get("active")));
      for (String term : terms(q)) and.add(cb.like(norm(cb, root.get("name")), contains(term), ESCAPE));
      return cb.and(and.toArray(Predicate[]::new));
    };
  }
}
