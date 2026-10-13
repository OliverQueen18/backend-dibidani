package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.VendorDtos.VendorRequest;
import ml.dibidani.api.dto.VendorDtos.VendorResponse;
import ml.dibidani.service.VendorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class VendorController {
  private final VendorService vendors;

  public VendorController(VendorService vendors) { this.vendors = vendors; }

  @GetMapping("/api/v1/vendors")
  public List<VendorResponse> list() { return vendors.publicList(); }

  @GetMapping("/api/v1/vendors/slug/{slug}")
  public VendorResponse bySlug(@PathVariable String slug) { return vendors.publicBySlug(slug); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  @GetMapping("/api/v1/admin/vendors")
  public List<VendorResponse> adminList() { return vendors.adminList(); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  @GetMapping("/api/v1/admin/vendors/{id}")
  public VendorResponse adminGet(@PathVariable Long id) { return vendors.adminGet(id); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PostMapping("/api/v1/admin/vendors")
  @ResponseStatus(HttpStatus.CREATED)
  public VendorResponse create(@Valid @RequestBody VendorRequest r) { return vendors.create(r); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @PutMapping("/api/v1/admin/vendors/{id}")
  public VendorResponse update(@PathVariable Long id, @Valid @RequestBody VendorRequest r) { return vendors.update(id, r); }

  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @DeleteMapping("/api/v1/admin/vendors/{id}")
  public VendorResponse delete(@PathVariable Long id) { return vendors.deactivate(id); }
}
