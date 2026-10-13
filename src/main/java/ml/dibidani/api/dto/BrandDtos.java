package ml.dibidani.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ml.dibidani.domain.Brand;

public final class BrandDtos {
  private BrandDtos() {}

  public record BrandRequest(
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 120) String name,
      @Size(max = 500) String logoUrl,
      Boolean active) {}

  public record BrandResponse(Long id, String name, String logoUrl, boolean active) {
    public static BrandResponse of(Brand b) { return b == null ? null : new BrandResponse(b.getId(), b.getName(), b.getLogoUrl(), b.isActive()); }
  }
}
