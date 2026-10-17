package ml.dibidani.api;

import ml.dibidani.api.dto.PhotoStudioDtos.*;
import ml.dibidani.service.ProductImageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class ProductPhotoStudioController {
  private final ProductImageService studio;

  public ProductPhotoStudioController(ProductImageService studio) { this.studio = studio; }

  @PostMapping(value = "/api/v1/products/images/upload", consumes = "multipart/form-data")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasRole('SELLER')")
  @ResponseStatus(HttpStatus.CREATED)
  public AssetResponse upload(Authentication a, @RequestParam("file") MultipartFile file,
                              @RequestParam(required = false) Long productId) throws IOException {
    return studio.upload(a, file, productId);
  }

  @PostMapping("/api/v1/products/images/process")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasRole('SELLER')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public JobResponse process(Authentication a, @RequestBody ProcessRequest body) {
    return studio.process(a, body);
  }

  @GetMapping("/api/v1/products/images/jobs/{id}")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasRole('SELLER')")
  public JobResponse job(Authentication a, @PathVariable Long id) {
    return studio.job(a, id);
  }

  @PostMapping("/api/v1/products/images/{id}/confirm")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasRole('SELLER')")
  public AssetResponse confirm(Authentication a, @PathVariable Long id, @RequestBody(required = false) ConfirmRequest body) {
    return studio.confirm(a, id, body == null ? new ConfirmRequest(null, false) : body);
  }

  @GetMapping("/api/v1/products/{id:\\d+}/images")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasRole('SELLER')")
  public List<AssetResponse> list(Authentication a, @PathVariable Long id) {
    return studio.list(a, id);
  }

  @PostMapping("/api/v1/products/{id}/vitrine")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasRole('SELLER')")
  public VitrineResponse setVitrine(Authentication a, @PathVariable Long id, @RequestBody VitrineRequest body) {
    return studio.setVitrine(a, id, body);
  }

  @DeleteMapping("/api/v1/products/images/{id}")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasRole('SELLER')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Authentication a, @PathVariable Long id) {
    studio.delete(a, id);
  }

  @GetMapping("/api/v1/products/images/{id}/original")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasRole('SELLER')")
  public ResponseEntity<Resource> original(Authentication a, @PathVariable Long id) {
    Resource file = studio.original(a, id);
    return ResponseEntity.ok()
        .contentType(studio.originalMedia(id))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("original", StandardCharsets.UTF_8).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body(file);
  }

  @GetMapping("/api/v1/products/images/lookup")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasRole('SELLER')")
  public LookupResponse lookup(Authentication a, @RequestParam String code) {
    return studio.lookup(a, code);
  }

  @GetMapping("/api/v1/admin/photo-studio/settings")
  @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE') or hasAuthority('PERM_CATALOG_MANAGE')")
  public SettingsResponse settings() { return studio.settings(); }

  @PutMapping("/api/v1/admin/photo-studio/settings")
  @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
  public SettingsResponse saveSettings(@RequestBody SettingsRequest body) { return studio.saveSettings(body); }
}
