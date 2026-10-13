package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.ProductDtos.ProductDetail;
import ml.dibidani.api.dto.ProductDtos.ProductRequest;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.api.dto.VehicleDtos.CompatibilityResponse;
import ml.dibidani.service.ProductService;
import ml.dibidani.service.VehicleService;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class ProductController {
  private final ProductService products;
  private final VehicleService vehicles;

  public ProductController(ProductService products, VehicleService vehicles) {
    this.products = products;
    this.vehicles = vehicles;
  }

  @GetMapping("/api/v1/products")
  public PageResponse<ProductSummary> list(@ModelAttribute ProductQuery query) {
    return products.page(query.toFilter(false), query.pageOrDefault(), query.sizeOrDefault());
  }

  @GetMapping("/api/v1/products/search")
  public PageResponse<ProductSummary> search(@ModelAttribute ProductQuery query) { return list(query); }

  @GetMapping("/api/v1/products/{id:\\d+}")
  public ProductDetail get(@PathVariable Long id) { return products.publicById(id); }

  @GetMapping("/api/v1/products/slug/{slug}")
  public ProductDetail bySlug(@PathVariable String slug) { return products.publicBySlug(slug); }

  @GetMapping("/api/v1/products/{id:\\d+}/similar")
  public List<ProductSummary> similar(@PathVariable Long id, @RequestParam(defaultValue = "8") int limit) { return products.similar(id, limit); }

  @GetMapping("/api/v1/products/category/{slug}")
  public PageResponse<ProductSummary> byCategory(@PathVariable String slug, @ModelAttribute ProductQuery query) {
    return products.byCategory(slug, query.toFilter(false), query.pageOrDefault(), query.sizeOrDefault());
  }

  @GetMapping("/api/v1/products/featured")
  public List<ProductSummary> featured(@RequestParam(defaultValue = "8") int limit) { return products.featured(limit); }

  @GetMapping("/api/v1/products/promotions")
  public List<ProductSummary> promotions(@RequestParam(defaultValue = "8") int limit) { return products.promotions(limit); }

  @GetMapping("/api/v1/products/latest")
  public List<ProductSummary> latest(@RequestParam(defaultValue = "8") int limit) { return products.latest(limit); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  @GetMapping("/api/v1/admin/products")
  public PageResponse<ProductSummary> adminList(@ModelAttribute ProductQuery query) {
    return products.page(query.toFilter(true), query.pageOrDefault(), query.sizeOrDefault());
  }

  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  @GetMapping("/api/v1/admin/products/{id}")
  public ProductDetail adminGet(@PathVariable Long id) { return products.adminGet(id); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PostMapping("/api/v1/admin/products")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductDetail create(Authentication a, @Valid @RequestBody ProductRequest r) { return products.create(r, a.getName()); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PutMapping("/api/v1/admin/products/{id}")
  public ProductDetail update(Authentication a, @PathVariable Long id, @Valid @RequestBody ProductRequest r) { return products.update(id, r, a.getName()); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PostMapping("/api/v1/admin/products/{id}/duplicate")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductDetail duplicate(Authentication a, @PathVariable Long id) { return products.duplicate(id, a.getName()); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PostMapping("/api/v1/admin/products/{id}/active")
  public ProductDetail setActive(@PathVariable Long id, @RequestParam boolean active) {
    return products.setActive(id, active);
  }

  @GetMapping("/api/v1/products/{id:\\d+}/compatibilities")
  public List<CompatibilityResponse> compatibilities(@PathVariable Long id) { return vehicles.forProduct(products.publicById(id).id()); }

  /** Désactivation logique : les commandes passées conservent leur produit. */
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @DeleteMapping("/api/v1/admin/products/{id}")
  public ProductDetail delete(@PathVariable Long id) { return products.deactivate(id); }
}
