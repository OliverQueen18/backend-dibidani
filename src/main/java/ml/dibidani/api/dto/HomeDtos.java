package ml.dibidani.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ml.dibidani.api.dto.AdvertisementDtos.AdvertisementResponse;
import ml.dibidani.api.dto.BrandDtos.BrandResponse;
import ml.dibidani.api.dto.CategoryDtos.CategoryResponse;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.api.dto.VendorDtos.VendorResponse;
import java.math.BigDecimal;
import java.util.List;

public final class HomeDtos {
  private HomeDtos() {}

  public record CategorySection(CategoryResponse category, List<ProductSummary> products) {}

  public record HomeResponse(
      List<AdvertisementResponse> heroAds,
      List<CategoryResponse> categories,
      List<AdvertisementResponse> topAds,
      List<ProductSummary> promotions,
      List<ProductSummary> popular,
      List<AdvertisementResponse> middleAds,
      List<ProductSummary> latest,
      List<CategorySection> categorySections,
      List<BrandResponse> brands,
      List<VendorResponse> vendors,
      List<AdvertisementResponse> bottomAds) {}

  public record SettingsResponse(
      String name, String slogan, String phone, String whatsapp, String whatsappUrl, String email, String address, String city,
      String openingHours, BigDecimal deliveryFee, BigDecimal freeDeliveryThreshold,
      String facebookUrl, String instagramUrl, String tiktokUrl, String youtubeUrl, String linkedinUrl,
      String pickupAddress, String pickupHours, String bankTransferDetails,
      String logoUrl, String topbarText, String heroLocation, String heroLocationSub, String floatingBanner,
      String navPromoLabel, String footerAbout, String footerCopyright,
      String testimonialsTitle, String testimonialsSubtitle, String partnersTitle, String partnersSubtitle) {}

  public record NewsletterRequest(
      @NotBlank(message = "L'adresse e-mail est obligatoire") @Email(message = "Adresse e-mail invalide") @Size(max = 255) String email) {}

  public record NewsletterResponse(String message, boolean alreadySubscribed) {}
}
