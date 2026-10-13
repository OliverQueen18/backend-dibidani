package ml.dibidani.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ml.dibidani.domain.SiteBlock;
import ml.dibidani.domain.SiteBlockKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CmsDtos {
  private CmsDtos() {}

  public record BlockRequest(
      @NotNull(message = "Le type de contenu est obligatoire") SiteBlockKind kind,
      @Size(max = 80) String slug,
      @NotBlank(message = "Le titre est obligatoire") @Size(max = 255) String title,
      @Size(max = 500) String subtitle,
      @Size(max = 20000) String body,
      @Size(max = 500) String imageUrl,
      @Size(max = 500) String linkUrl,
      @Size(max = 80) String ctaLabel,
      @Size(max = 120) String meta,
      Integer displayOrder,
      Boolean active) {}

  public record BlockResponse(
      Long id, SiteBlockKind kind, String slug, String title, String subtitle, String body,
      String imageUrl, String linkUrl, String ctaLabel, String meta, int displayOrder, boolean active,
      Instant createdAt, Instant updatedAt) {
    public static BlockResponse of(SiteBlock b) {
      return new BlockResponse(b.getId(), b.getKind(), b.getSlug(), b.getTitle(), b.getSubtitle(), b.getBody(),
          b.getImageUrl(), b.getLinkUrl(), b.getCtaLabel(), b.getMeta(), b.getDisplayOrder() == null ? 0 : b.getDisplayOrder(),
          b.isActive(), b.getCreatedAt(), b.getUpdatedAt());
    }
  }

  public record CmsResponse(
      Map<String, String> appearance,
      List<BlockResponse> heroes,
      List<BlockResponse> testimonials,
      List<BlockResponse> partners,
      List<BlockResponse> pages,
      List<BlockResponse> socials,
      List<BlockResponse> menus,
      List<BlockResponse> homeSections,
      List<BlockResponse> faqs) {}
}
