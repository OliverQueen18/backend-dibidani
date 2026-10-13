package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.domain.AdPosition;
import ml.dibidani.domain.AdType;
import ml.dibidani.domain.Advertisement;
import java.time.Instant;

public final class AdvertisementDtos {
  private AdvertisementDtos() {}

  public record AdvertisementRequest(
      @NotBlank(message = "Le titre est obligatoire") @Size(max = 255) String title,
      @Size(max = 2000) String description,
      @Size(max = 500) String imageUrl,
      @Size(max = 500) String mobileImageUrl,
      @Size(max = 500) @Pattern(regexp = "^$|^(/|https?://).*", message = "Lien interne (/...) ou externe (http(s)://...)") String targetUrl,
      @Size(max = 80) String ctaLabel,
      @NotNull(message = "La position est obligatoire") AdPosition position,
      @NotNull(message = "Le type est obligatoire") AdType type,
      @NotNull(message = "La date de début est obligatoire") Instant startDate,
      @NotNull(message = "La date de fin est obligatoire") Instant endDate,
      @Min(0) Integer displayOrder,
      Boolean active) {

    @AssertTrue(message = "La date de fin doit être postérieure à la date de début")
    public boolean isPeriodValid() { return startDate == null || endDate == null || endDate.isAfter(startDate); }
  }

  public record AdvertisementResponse(
      Long id, String title, String description, String imageUrl, String mobileImageUrl, String targetUrl, String ctaLabel,
      boolean external, AdPosition position, AdType type, Instant startDate, Instant endDate, int displayOrder, boolean active,
      boolean visibleNow, Instant createdAt, Instant updatedAt) {

    public static AdvertisementResponse of(Advertisement a) {
      Instant now = Instant.now();
      boolean visible = a.isActive() && !a.getStartDate().isAfter(now) && !a.getEndDate().isBefore(now);
      boolean external = a.getTargetUrl() != null && a.getTargetUrl().matches("^https?://.*");
      return new AdvertisementResponse(a.getId(), a.getTitle(), a.getDescription(), a.getImageUrl(), a.getMobileImageUrl(), a.getTargetUrl(),
          a.getCtaLabel(), external, a.getPosition(), a.getType(), a.getStartDate(), a.getEndDate(), a.getDisplayOrder(), a.isActive(),
          visible, a.getCreatedAt(), a.getUpdatedAt());
    }
  }
}
