package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.CategoryDtos.CategoryRequest;
import ml.dibidani.api.dto.CategoryDtos.CategoryResponse;
import ml.dibidani.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class CategoryController {
  private final CategoryService categories;

  public CategoryController(CategoryService categories) { this.categories = categories; }

  /** Arbre des catégories actives ; {@code flat=true} pour une liste à plat. */
  @GetMapping("/api/v1/categories")
  public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean flat) {
    return flat ? categories.publicFlat() : categories.publicTree();
  }

  @GetMapping("/api/v1/categories/{id:\\d+}")
  public CategoryResponse get(@PathVariable Long id) { return categories.publicById(id); }

  @GetMapping("/api/v1/categories/slug/{slug}")
  public CategoryResponse bySlug(@PathVariable String slug) { return categories.publicBySlug(slug); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  @GetMapping("/api/v1/admin/categories")
  public List<CategoryResponse> adminList() { return categories.adminList(); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  @GetMapping("/api/v1/admin/categories/{id}")
  public CategoryResponse adminGet(@PathVariable Long id) { return categories.adminGet(id); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PostMapping("/api/v1/admin/categories")
  @ResponseStatus(HttpStatus.CREATED)
  public CategoryResponse create(@Valid @RequestBody CategoryRequest r) { return categories.create(r); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PutMapping("/api/v1/admin/categories/{id}")
  public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) { return categories.update(id, r); }

  /** Désactivation logique (jamais de suppression physique). */
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @DeleteMapping("/api/v1/admin/categories/{id}")
  public CategoryResponse delete(@PathVariable Long id) { return categories.deactivate(id); }
}
