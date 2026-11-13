package ml.dibidani.api.dto;

import ml.dibidani.domain.ProductImageAsset;
import ml.dibidani.domain.ProductImageJob;
import ml.dibidani.domain.PhotoStudioSettings;
import java.time.Instant;
import java.util.List;

public final class PhotoStudioDtos {
  private PhotoStudioDtos() {}

  public record ProcessRequest(
      Long assetId,
      Long productId,
      Boolean removeBackground,
      Boolean whiteBackground,
      Boolean enhance,
      Boolean watermark,
      Boolean recenter,
      Boolean shadow,
      Double fillRatio,
      Double zoom,
      Double offsetX,
      Double offsetY,
      String model, String background, String backgroundColor, String format,
      Integer width, Integer height, Integer quality, Boolean refineEdges,
      Integer rotation, Double brightness, Double contrast) {}

  public record BatchRequest(List<ProcessRequest> images) {}
  public record BatchItem(Long assetId, JobResponse job, String error) {}

  public record ConfirmRequest(Long productId, boolean main) {}

  public record VitrineRequest(String imageUrl) {}

  public record VitrineResponse(String mainImageUrl, List<String> images) {}

  public record SettingsRequest(
      String engine,
      Boolean watermarkEnabled,
      String watermarkPosition,
      Double watermarkSize,
      Double watermarkOpacity,
      Double watermarkMargin,
      String watermarkVariant,
      Double fillRatio,
      Boolean shadowEnabled) {}

  public record AssetResponse(
      Long id, Long productId, Long vendorId,
      String status, boolean confirmed,
      String masterUrl, String catalogUrl, String thumbUrl, String jpegUrl,
      String originalMime, Integer originalWidth, Integer originalHeight, Integer originalBytes,
      String engine, String processingVersion, Instant createdAt,
      String model, String format, Integer width, Integer height, Long bytes) {
    public static AssetResponse of(ProductImageAsset a) {
      return new AssetResponse(
          a.getId(),
          a.getProduct() == null ? null : a.getProduct().getId(),
          a.getVendor() == null ? null : a.getVendor().getId(),
          a.getStatus().name(), a.isConfirmed(),
          a.getMasterUrl(), a.getCatalogUrl(), a.getThumbUrl(), a.getJpegUrl(),
          a.getOriginalMime(), a.getOriginalWidth(), a.getOriginalHeight(), a.getOriginalBytes(),
          a.getEngine(), a.getProcessingVersion(), a.getCreatedAt(),
          a.getModel(), a.getOutputFormat(), a.getOutputWidth(), a.getOutputHeight(), a.getOutputBytes());
    }
  }

  public record JobResponse(
      Long id, Long assetId, String status, int progress, String message,
      Instant createdAt, Instant startedAt, Instant finishedAt, AssetResponse asset) {
    public static JobResponse of(ProductImageJob j) {
      return new JobResponse(j.getId(), j.getAsset().getId(), j.getStatus().name(), j.getProgress(), j.getMessage(),
          j.getCreatedAt(), j.getStartedAt(), j.getFinishedAt(), AssetResponse.of(j.getAsset()));
    }
  }

  public record LookupResponse(
      Long productId, String name, String sku, String barcode, String slug,
      String mainImageUrl, List<String> images, boolean owned,
      List<String> supplierHints) {
    public static LookupResponse empty() {
      return new LookupResponse(null, null, null, null, null, null, List.of(), false, List.of());
    }
  }

  public record SettingsResponse(
      String engine, boolean watermarkEnabled, String watermarkPosition,
      double watermarkSize, double watermarkOpacity, double watermarkMargin,
      String watermarkVariant, double fillRatio, boolean shadowEnabled,
      boolean processorUp) {
    public static SettingsResponse of(PhotoStudioSettings s, boolean processorUp) {
      return new SettingsResponse(s.getEngine(), s.isWatermarkEnabled(), s.getWatermarkPosition(),
          s.getWatermarkSize(), s.getWatermarkOpacity(), s.getWatermarkMargin(), s.getWatermarkVariant(),
          s.getFillRatio(), s.isShadowEnabled(), processorUp);
    }
  }
}
