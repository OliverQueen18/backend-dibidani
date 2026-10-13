package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.SocialDtos.ProductShare;
import ml.dibidani.api.dto.SocialDtos.PublishRequest;
import ml.dibidani.api.dto.SocialDtos.SocialPostResponse;
import ml.dibidani.api.dto.SocialDtos.SocialStatus;
import ml.dibidani.service.SocialPublishService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class SocialController {
  private final SocialPublishService social;

  public SocialController(SocialPublishService social) { this.social = social; }

  @GetMapping("/api/v1/public/products/{slug}/share")
  public ProductShare share(@PathVariable String slug) { return social.share(slug); }

  @GetMapping("/api/v1/admin/social/status")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasAuthority('PERM_ADS_MANAGE')")
  public SocialStatus status() { return social.status(); }

  @GetMapping("/api/v1/admin/products/{id}/social-posts")
  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  public List<SocialPostResponse> history(@PathVariable Long id) { return social.history(id); }

  @PostMapping("/api/v1/admin/products/{id}/social-publish")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasAuthority('PERM_ADS_MANAGE')")
  public List<SocialPostResponse> publish(Authentication a, @PathVariable Long id, @Valid @RequestBody PublishRequest r) {
    return social.publish(id, r.channels(), r.message(), a == null ? null : a.getName());
  }
}
