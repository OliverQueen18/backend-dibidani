package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.domain.Vendor;
import java.time.Instant;

public final class VendorDtos {
  private VendorDtos() {}

  private static final String PHONE = "^$|^\\+?[0-9 ().-]{6,25}$";

  public record VendorRequest(
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 150) String name,
      @Size(max = 160) @Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug invalide (minuscules, chiffres et tirets)") String slug,
      @Size(max = 4000) String description,
      @Size(max = 500) String logoUrl,
      @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String phone,
      @Pattern(regexp = PHONE, message = "Numéro WhatsApp invalide") String whatsapp,
      @Size(max = 500) String address,
      @Size(max = 255) String district,
      @Size(max = 120) String city,
      Boolean active) {}

  public record VendorRef(Long id, String name, String slug) {
    public static VendorRef of(Vendor v) { return v == null ? null : new VendorRef(v.getId(), v.getName(), v.getSlug()); }
  }

  public record VendorResponse(
      Long id, String name, String slug, String description, String logoUrl, String phone, String whatsapp,
      String address, String district, String city, boolean active, long productCount, Instant createdAt, Instant updatedAt) {
    public static VendorResponse of(Vendor v, long productCount) {
      return new VendorResponse(v.getId(), v.getName(), v.getSlug(), v.getDescription(), v.getLogoUrl(), v.getPhone(), v.getWhatsapp(),
          v.getAddress(), v.getDistrict(), v.getCity(), v.isActive(), productCount, v.getCreatedAt(), v.getUpdatedAt());
    }
  }
}
