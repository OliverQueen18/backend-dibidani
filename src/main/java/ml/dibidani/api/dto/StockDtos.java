package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.domain.Product;
import ml.dibidani.domain.ProductVariant;
import ml.dibidani.domain.StockMovement;
import ml.dibidani.domain.StockMovementType;
import java.time.Instant;
import java.util.List;

public final class StockDtos {
  private StockDtos() {}

  /**
   * Saisie manuelle. {@code quantity} est toujours un nombre d'unités positif, sauf pour ADJUSTMENT
   * où c'est le stock constaté (inventaire). SALE / RETURN / CANCELLATION sont refusés ici : ils
   * naissent exclusivement du workflow de commande.
   */
  public record AdjustRequest(
      @NotNull(message = "Produit obligatoire") Long productId,
      Long variantId,
      @NotNull(message = "Type obligatoire") StockMovementType type,
      @Min(value = 0, message = "Quantité invalide") @Max(value = 1_000_000) int quantity,
      @NotBlank(message = "Le commentaire est obligatoire") @Size(max = 1000) String comment,
      @Size(max = 80) String reference) {}

  public record ThresholdRequest(@Min(value = 0, message = "Seuil invalide") @Max(value = 1_000_000) int lowStockThreshold) {}

  public record VariantStock(Long id, String name, String sku, int stock, boolean active) {
    static VariantStock of(ProductVariant v) { return new VariantStock(v.getId(), v.getName(), v.getSku(), v.getStock(), v.isActive()); }
  }

  public record StockRow(Long productId, String sku, String name, String slug, String imageUrl, String unit, int stock, int lowStockThreshold,
                         String level, boolean active, String vendorName, List<VariantStock> variants) {
    public static StockRow of(Product p) {
      String level = p.getStock() <= 0 ? "OUT" : p.getStock() <= p.getLowStockThreshold() ? "LOW" : "OK";
      return new StockRow(p.getId(), p.getSku(), p.getName(), p.getSlug(), ProductDtos.mainImage(p), p.getUnit(), p.getStock(), p.getLowStockThreshold(),
          level, p.isActive(), p.getVendor() == null ? null : p.getVendor().getName(), p.getVariants().stream().map(VariantStock::of).toList());
    }
  }

  public record MovementResponse(Long id, Long productId, String productName, String sku, Long variantId, String variantName,
                                 StockMovementType type, String typeLabel, int quantityBefore, int quantity, int stockAfter,
                                 String comment, String reference, String createdBy, Instant createdAt) {
    public static MovementResponse of(StockMovement m) {
      var p = m.getProduct();
      var v = m.getVariant();
      return new MovementResponse(m.getId(), p.getId(), p.getName(), p.getSku(),
          v == null ? null : v.getId(), v == null ? m.getVariantName() : v.getName(),
          m.getType(), m.getType().label(), m.getQuantityBefore(), m.getQuantity(), m.getStockAfter(),
          m.getComment(), m.getReference(), m.getCreatedBy(), m.getCreatedAt());
    }
  }

  public record TypeOption(StockMovementType type, String label, boolean manual, int direction) {
    public static TypeOption of(StockMovementType t) { return new TypeOption(t, t.label(), t.manual(), t.direction()); }
  }

  public record TypeStat(StockMovementType type, String label, long count, long units) {}

  public record StockOverview(long products, long inStock, long low, long out, List<TypeStat> movements30d, List<TypeOption> types) {}
}
