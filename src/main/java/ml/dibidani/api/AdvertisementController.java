package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.AdvertisementDtos.AdvertisementRequest;
import ml.dibidani.api.dto.AdvertisementDtos.AdvertisementResponse;
import ml.dibidani.domain.AdPosition;
import ml.dibidani.service.AdvertisementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class AdvertisementController {
  private final AdvertisementService ads;

  public AdvertisementController(AdvertisementService ads) { this.ads = ads; }

  @GetMapping("/api/v1/advertisements/position/{position}")
  public List<AdvertisementResponse> byPosition(@PathVariable AdPosition position) { return ads.visible(position); }

  @PreAuthorize("hasAuthority('PERM_ADS_MANAGE')")
  @GetMapping({"/api/v1/admin/advertisements", "/api/v1/admin/ads"})
  public List<AdvertisementResponse> adminList(@RequestParam(required = false) AdPosition position) { return ads.adminList(position); }

  @PreAuthorize("hasAuthority('PERM_ADS_MANAGE')")
  @GetMapping("/api/v1/admin/advertisements/{id}")
  public AdvertisementResponse adminGet(@PathVariable Long id) { return ads.adminGet(id); }

  @PreAuthorize("hasAuthority('PERM_ADS_MANAGE')")
  @PostMapping("/api/v1/admin/advertisements")
  @ResponseStatus(HttpStatus.CREATED)
  public AdvertisementResponse create(@Valid @RequestBody AdvertisementRequest r) { return ads.create(r); }

  @PreAuthorize("hasAuthority('PERM_ADS_MANAGE')")
  @PutMapping("/api/v1/admin/advertisements/{id}")
  public AdvertisementResponse update(@PathVariable Long id, @Valid @RequestBody AdvertisementRequest r) { return ads.update(id, r); }

  @PreAuthorize("hasAuthority('PERM_ADS_MANAGE')")
  @DeleteMapping("/api/v1/admin/advertisements/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) { ads.delete(id); }
}
