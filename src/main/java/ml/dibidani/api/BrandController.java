package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.BrandDtos.BrandRequest;
import ml.dibidani.api.dto.BrandDtos.BrandResponse;
import ml.dibidani.service.BrandService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class BrandController {
  private final BrandService brands;

  public BrandController(BrandService brands) { this.brands = brands; }

  @GetMapping("/api/v1/brands")
  public List<BrandResponse> list() { return brands.publicList(); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  @GetMapping("/api/v1/admin/brands")
  public List<BrandResponse> adminList() { return brands.adminList(); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PostMapping("/api/v1/admin/brands")
  @ResponseStatus(HttpStatus.CREATED)
  public BrandResponse create(@Valid @RequestBody BrandRequest r) { return brands.create(r); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PutMapping("/api/v1/admin/brands/{id}")
  public BrandResponse update(@PathVariable Long id, @Valid @RequestBody BrandRequest r) { return brands.update(id, r); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @DeleteMapping("/api/v1/admin/brands/{id}")
  public BrandResponse delete(@PathVariable Long id) { return brands.deactivate(id); }
}
