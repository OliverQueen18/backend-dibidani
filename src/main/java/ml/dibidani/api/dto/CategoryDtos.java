package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.domain.Category;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public final class CategoryDtos {
  private CategoryDtos() {}

  public record CategoryRequest(
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 120, message = "120 caractères maximum") String name,
      @Size(max = 140) @Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug invalide (minuscules, chiffres et tirets)") String slug,
      @Size(max = 2000) String description,
      @Size(max = 500) String imageUrl,
      @Size(max = 32) String icon,
      Long parentId,
      @Min(value = 0, message = "L'ordre doit être positif") Integer displayOrder,
      Boolean active,
      Boolean featured) {}

  public record CategoryRef(Long id, String name, String slug) {
    public static CategoryRef of(Category c) { return c == null ? null : new CategoryRef(c.getId(), c.getName(), c.getSlug()); }
  }

  public record CategoryResponse(
      Long id, String name, String slug, String description, String imageUrl, String icon,
      Long parentId, CategoryRef parent, Integer displayOrder, boolean active, boolean featured,
      List<CategoryResponse> children, Instant createdAt, Instant updatedAt) {

    public static CategoryResponse flat(Category c) { return of(c, List.of()); }

    /** Catégorie avec ses descendants retenus par le filtre (ex. actifs uniquement côté public). */
    public static CategoryResponse tree(Category c, Predicate<Category> keep) {
      List<CategoryResponse> kids = c.getChildren().stream().filter(keep).map(k -> tree(k, keep)).toList();
      return of(c, kids);
    }

    private static CategoryResponse of(Category c, List<CategoryResponse> children) {
      Category p = c.getParent();
      return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getImageUrl(), c.getIcon(),
          p == null ? null : p.getId(), CategoryRef.of(p), c.getDisplayOrder(), c.isActive(), c.isFeatured(),
          children, c.getCreatedAt(), c.getUpdatedAt());
    }
  }
}
