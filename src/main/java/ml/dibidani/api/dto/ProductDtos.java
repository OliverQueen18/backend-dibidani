package ml.dibidani.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ml.dibidani.api.dto.BrandDtos.BrandResponse;
import ml.dibidani.api.dto.CategoryDtos.CategoryRef;
import ml.dibidani.api.dto.VehicleDtos.CompatibilityRequest;
import ml.dibidani.api.dto.VehicleDtos.CompatibilityResponse;
import ml.dibidani.api.dto.VendorDtos.VendorRef;
import ml.dibidani.domain.Product;
import ml.dibidani.domain.ProductVariant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public final class ProductDtos {
  private ProductDtos() {}

  public record VariantRequest(
      Long id,
      @NotBlank(message = "Nom de variante obligatoire") @Size(max = 255) String name,
      @Size(max = 100) String sku,
      @DecimalMin(value = "0.0", inclusive = false, message = "Le prix doit être positif") BigDecimal price,
      @Min(value = 0, message = "Le stock ne peut pas être négatif") Integer stock,
      Boolean active) {}

  public record SpecificationDto(
      @NotBlank(message = "Libellé obligatoire") @Size(max = 255) String label,
      @NotBlank(message = "Valeur obligatoire") @Size(max = 1000) String value) {}

  public record ProductRequest(
      @NotBlank(message = "Le SKU est obligatoire") @Size(max = 100) String sku,
      @Size(max = 255, message = "255 caractères maximum")
      @Pattern(regexp = "^$|^[^\\p{Cntrl}]{4,255}$", message = "Code-barres ou QR invalide")
      String barcode,
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 255) String name,
      @Size(max = 255) @Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug invalide (minuscules, chiffres et tirets)") String slug,
      @Size(max = 500) String shortDescription,
      @Size(max = 20000) String description,
      @NotNull(message = "La catégorie est obligatoire") Long categoryId,
      Long subcategoryId,
      Long brandId,
      Long vendorId,
      @NotNull(message = "Le prix est obligatoire") @DecimalMin(value = "0.0", inclusive = false, message = "Le prix doit être positif") BigDecimal price,
      @DecimalMin(value = "0.0", inclusive = false, message = "Le prix promotionnel doit être positif") BigDecimal promoPrice,
      @DecimalMin(value = "0.0", message = "Le coût ne peut pas être négatif") BigDecimal costPrice,
      @Size(max = 100) String unit,
      @Min(value = 0, message = "Le stock ne peut pas être négatif") Integer stock,
      @Min(value = 0, message = "Le seuil ne peut pas être négatif") Integer lowStockThreshold,
      @DecimalMin(value = "0.0") BigDecimal weightKg,
      @DecimalMin(value = "0.0") BigDecimal lengthCm,
      @DecimalMin(value = "0.0") BigDecimal widthCm,
      @DecimalMin(value = "0.0") BigDecimal heightCm,
      @Size(max = 500) String mainImageUrl,
      @Size(max = 12, message = "12 images maximum") List<@NotBlank @Size(max = 500) String> images,
      List<@Valid VariantRequest> variants,
      List<@Valid SpecificationDto> specifications,
      @Size(max = 255) String oemReference,
      @Size(max = 255) String manufacturerReference,
      @Size(max = 300, message = "300 compatibilités maximum") List<@Valid CompatibilityRequest> compatibilities,
      Boolean active,
      Boolean featured,
      Boolean newArrival,
      Boolean bestSeller) {

    @AssertTrue(message = "Le prix promotionnel doit être inférieur au prix normal")
    public boolean isPromoValid() { return promoPrice == null || price == null || promoPrice.compareTo(price) < 0; }
  }

  public record VariantResponse(Long id, String name, String sku, BigDecimal price, int stock, boolean active) {
    static VariantResponse of(ProductVariant v) { return new VariantResponse(v.getId(), v.getName(), v.getSku(), v.getPrice(), v.getStock(), v.isActive()); }
  }

  public record ProductSummary(
      Long id, String sku, String barcode, String name, String slug, String shortDescription,
      BigDecimal price, BigDecimal promoPrice, BigDecimal effectivePrice, boolean onPromotion, int discountPercent,
      int stock, boolean inStock, boolean lowStock, String unit, String mainImageUrl,
      CategoryRef category, CategoryRef subcategory, BrandResponse brand, VendorRef vendor,
      boolean featured, boolean newArrival, boolean bestSeller, boolean hasVariants, double rating, int reviewCount, boolean active) {

    public static ProductSummary of(Product p) {
      return new ProductSummary(p.getId(), p.getSku(), p.getBarcode(), p.getName(), p.getSlug(), p.getShortDescription(),
          p.getPrice(), p.isOnPromotion() ? p.getPromoPrice() : null, p.getEffectivePrice(), p.isOnPromotion(), discount(p),
          p.getStock(), p.isActive(), false, p.getUnit(), mainImage(p),
          CategoryRef.of(p.getCategory()), CategoryRef.of(p.getSubcategory()), BrandResponse.of(p.getBrand()), VendorRef.of(p.getVendor()),
          p.isFeatured(), p.isNewArrival(), p.isBestSeller(), p.getVariants().stream().anyMatch(ProductVariant::isActive),
          p.getRating(), p.getReviewCount(), p.isActive());
    }
  }

  public record ProductDetail(
      Long id, String sku, String barcode, String name, String slug, String shortDescription, String description,
      BigDecimal price, BigDecimal promoPrice, BigDecimal effectivePrice, boolean onPromotion, int discountPercent, BigDecimal costPrice,
      int stock, boolean inStock, boolean lowStock, int lowStockThreshold, String unit,
      BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
      String mainImageUrl, List<String> images, List<VariantResponse> variants, List<SpecificationDto> specifications,
      String oemReference, String manufacturerReference, List<CompatibilityResponse> compatibilities,
      CategoryRef category, CategoryRef subcategory, BrandResponse brand, VendorRef vendor,
      boolean active, boolean featured, boolean newArrival, boolean bestSeller, double rating, int reviewCount,
      Instant createdAt, Instant updatedAt) {

    /** Fiche publique : variantes inactives et coût d'achat masqués. */
    public static ProductDetail publicView(Product p, List<CompatibilityResponse> compatibilities) { return of(p, false, compatibilities); }
    public static ProductDetail adminView(Product p, List<CompatibilityResponse> compatibilities) { return of(p, true, compatibilities); }

    private static ProductDetail of(Product p, boolean admin, List<CompatibilityResponse> compatibilities) {
      List<VariantResponse> variants = p.getVariants().stream().filter(v -> admin || v.isActive()).map(VariantResponse::of).toList();
      List<String> images = p.getImages().stream().filter(i -> i != null && !i.isBlank()).toList();
      return new ProductDetail(p.getId(), p.getSku(), p.getBarcode(), p.getName(), p.getSlug(), p.getShortDescription(), p.getDescription(),
          p.getPrice(), admin ? p.getPromoPrice() : (p.isOnPromotion() ? p.getPromoPrice() : null), p.getEffectivePrice(), p.isOnPromotion(), discount(p),
          admin ? p.getCostPrice() : null,
          p.getStock(), p.isActive(), false, p.getLowStockThreshold(), p.getUnit(),
          p.getWeightKg(), p.getLengthCm(), p.getWidthCm(), p.getHeightCm(),
          mainImage(p), images, variants,
          p.getSpecifications().stream().map(s -> new SpecificationDto(s.getLabel(), s.getValue())).toList(),
          p.getOemReference(), p.getManufacturerReference(), compatibilities == null ? List.of() : compatibilities,
          CategoryRef.of(p.getCategory()), CategoryRef.of(p.getSubcategory()), BrandResponse.of(p.getBrand()), VendorRef.of(p.getVendor()),
          p.isActive(), p.isFeatured(), p.isNewArrival(), p.isBestSeller(), p.getRating(), p.getReviewCount(),
          p.getCreatedAt(), p.getUpdatedAt());
    }
  }

  static int discount(Product p) {
    if (!p.isOnPromotion() || p.getPrice().signum() == 0) return 0;
    return p.getPrice().subtract(p.getPromoPrice()).multiply(BigDecimal.valueOf(100)).divide(p.getPrice(), 0, RoundingMode.HALF_UP).intValue();
  }

  static String mainImage(Product p) {
    if (p.getMainImageUrl() != null && !p.getMainImageUrl().isBlank()) return p.getMainImageUrl();
    return p.getImages().stream().filter(i -> i != null && !i.isBlank()).findFirst().orElse(null);
  }
}
