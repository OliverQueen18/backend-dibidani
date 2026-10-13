package ml.dibidani.service;

import ml.dibidani.api.dto.CategoryDtos.CategoryRequest;
import ml.dibidani.api.dto.CategoryDtos.CategoryResponse;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.Category;
import ml.dibidani.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {
  private final CategoryRepository categories;

  public CategoryService(CategoryRepository categories) { this.categories = categories; }

  /** Arbre public : catégories racines actives et leurs sous-catégories actives. */
  public List<CategoryResponse> publicTree() {
    return categories.findByParentIsNullAndActiveTrueOrderByDisplayOrderAscNameAsc().stream()
        .map(c -> CategoryResponse.tree(c, Category::isActive)).toList();
  }

  public List<CategoryResponse> publicFlat() {
    return categories.findByActiveTrueOrderByDisplayOrderAsc().stream().map(CategoryResponse::flat).toList();
  }

  public List<CategoryResponse> featured() {
    return categories.findByFeaturedTrueAndActiveTrueOrderByDisplayOrderAsc().stream().map(c -> CategoryResponse.tree(c, Category::isActive)).toList();
  }

  public CategoryResponse publicById(Long id) { return CategoryResponse.tree(activeOrThrow(categories.findById(id).orElse(null)), Category::isActive); }

  public CategoryResponse publicBySlug(String slug) { return CategoryResponse.tree(activeOrThrow(categories.findBySlug(slug).orElse(null)), Category::isActive); }

  public List<CategoryResponse> adminList() {
    return categories.findAllByOrderByDisplayOrderAscNameAsc().stream().map(CategoryResponse::flat).toList();
  }

  public CategoryResponse adminGet(Long id) { return CategoryResponse.tree(find(id), c -> true); }

  @Transactional
  public CategoryResponse create(CategoryRequest r) {
    var c = new Category();
    apply(c, r);
    return CategoryResponse.flat(categories.save(c));
  }

  @Transactional
  public CategoryResponse update(Long id, CategoryRequest r) {
    var c = find(id);
    apply(c, r);
    return CategoryResponse.flat(categories.save(c));
  }

  /** Désactivation logique : la catégorie et ses sous-catégories sont masquées, les produits restent intacts. */
  @Transactional
  public CategoryResponse deactivate(Long id) {
    var c = find(id);
    deactivateRecursively(c);
    return CategoryResponse.flat(c);
  }

  private void deactivateRecursively(Category c) {
    c.setActive(false);
    c.getChildren().forEach(this::deactivateRecursively);
  }

  private void apply(Category c, CategoryRequest r) {
    String slug = Slugs.resolve(r.slug(), r.name(), s -> c.getId() == null ? categories.existsBySlug(s) : categories.existsBySlugAndIdNot(s, c.getId()));
    boolean taken = c.getId() == null ? categories.existsBySlug(slug) : categories.existsBySlugAndIdNot(slug, c.getId());
    if (taken) throw new ConflictException("Le slug « " + slug + " » est déjà utilisé");

    Category parent = null;
    if (r.parentId() != null) {
      parent = find(r.parentId());
      for (Category p = parent; p != null; p = p.getParent()) {
        if (c.getId() != null && p.getId().equals(c.getId())) throw new IllegalArgumentException("Une catégorie ne peut pas être sa propre parente ou celle d'un de ses parents");
      }
    }

    c.setName(r.name().trim());
    c.setSlug(slug);
    c.setDescription(Slugs.blankToNull(r.description()));
    c.setImageUrl(Slugs.blankToNull(r.imageUrl()));
    c.setIcon(Slugs.blankToNull(r.icon()));
    c.setParent(parent);
    c.setDisplayOrder(r.displayOrder());
    c.setActive(r.active() == null || r.active());
    c.setFeatured(Boolean.TRUE.equals(r.featured()));
  }

  Category find(Long id) { return categories.findById(id).orElseThrow(() -> new NotFoundException("Catégorie introuvable")); }

  private Category activeOrThrow(Category c) {
    if (c == null || !c.isActive() || (c.getParent() != null && !c.getParent().isActive())) throw new NotFoundException("Catégorie introuvable");
    return c;
  }
}
