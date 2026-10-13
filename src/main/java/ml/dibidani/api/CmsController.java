package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.CmsDtos.BlockRequest;
import ml.dibidani.api.dto.CmsDtos.BlockResponse;
import ml.dibidani.api.dto.CmsDtos.CmsResponse;
import ml.dibidani.service.CmsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class CmsController {
  private final CmsService cms;

  public CmsController(CmsService cms) { this.cms = cms; }

  @GetMapping("/api/v1/public/cms")
  public CmsResponse published() { return cms.published(); }

  @GetMapping("/api/v1/public/cms/pages/{slug}")
  public BlockResponse publicPage(@PathVariable String slug) { return cms.publicPage(slug); }

  @GetMapping("/api/v1/admin/cms")
  @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE') or hasAuthority('PERM_ADS_MANAGE')")
  public CmsResponse admin() { return cms.admin(); }

  @PostMapping("/api/v1/admin/cms/blocks")
  @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE') or hasAuthority('PERM_ADS_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public BlockResponse create(@Valid @RequestBody BlockRequest r) { return cms.create(r); }

  @PutMapping("/api/v1/admin/cms/blocks/{id}")
  @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE') or hasAuthority('PERM_ADS_MANAGE')")
  public BlockResponse update(@PathVariable Long id, @Valid @RequestBody BlockRequest r) { return cms.update(id, r); }

  @DeleteMapping("/api/v1/admin/cms/blocks/{id}")
  @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE') or hasAuthority('PERM_ADS_MANAGE')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) { cms.delete(id); }
}
