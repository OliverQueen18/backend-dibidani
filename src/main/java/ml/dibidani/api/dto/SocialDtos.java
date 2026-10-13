package ml.dibidani.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import ml.dibidani.domain.SocialChannel;
import ml.dibidani.domain.SocialPost;
import ml.dibidani.domain.SocialPostStatus;
import java.time.Instant;
import java.util.List;

public final class SocialDtos {
  private SocialDtos() {}

  public record PublishRequest(
      @NotEmpty(message = "Choisissez au moins un réseau") List<SocialChannel> channels,
      @Size(max = 1500) String message) {}

  public record SocialPostResponse(
      Long id, Long productId, String productName, SocialChannel channel, String channelLabel,
      SocialPostStatus status, String message, String productUrl, String shareUrl, String externalId,
      String errorMessage, boolean mock, Instant publishedAt, Instant createdAt) {
    public static SocialPostResponse of(SocialPost p) {
      return new SocialPostResponse(p.getId(), p.getProduct().getId(), p.getProduct().getName(), p.getChannel(),
          p.getChannel().label(), p.getStatus(), p.getMessage(), p.getProductUrl(), p.getShareUrl(), p.getExternalId(),
          p.getErrorMessage(), p.isMock(), p.getPublishedAt(), p.getCreatedAt());
    }
  }

  public record ProductShare(
      String caption, String productUrl, String whatsappUrl, String facebookUrl, String tiktokUrl, String orderWhatsappUrl) {}

  public record SocialStatus(boolean facebookConfigured, boolean tiktokConfigured, boolean whatsappConfigured, boolean mockEnabled) {}
}
