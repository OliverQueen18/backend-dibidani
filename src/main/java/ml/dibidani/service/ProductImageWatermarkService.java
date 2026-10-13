package ml.dibidani.service;

import ml.dibidani.common.NotFoundException;
import ml.dibidani.domain.PhotoStudioSettings;
import ml.dibidani.repository.PhotoStudioSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProductImageWatermarkService {
  private static final Set<String> POS = Set.of("BOTTOM_RIGHT", "BOTTOM_LEFT", "TOP_RIGHT", "TOP_LEFT", "BOTTOM_CENTER");
  private static final Set<String> ENGINES = Set.of("local", "api", "rembg");
  private final PhotoStudioSettingsRepository settings;

  public ProductImageWatermarkService(PhotoStudioSettingsRepository settings) { this.settings = settings; }

  public PhotoStudioSettings current() {
    return settings.findById(1L).orElseThrow(() -> new NotFoundException("Réglages du studio introuvables"));
  }

  public Map<String, Object> processOptions(boolean removeBackground, boolean whiteBackground, boolean enhance,
                                            boolean watermark, boolean recenter, Boolean shadow, Double fillRatio,
                                            Double zoom, Double offsetX, Double offsetY) {
    PhotoStudioSettings s = current();
    double fill = fillRatio != null ? fillRatio : s.getFillRatio();
    if (!recenter) fill = 0.98;
    return Map.ofEntries(
        Map.entry("removeBackground", removeBackground),
        Map.entry("whiteBackground", whiteBackground),
        Map.entry("enhance", enhance),
        Map.entry("watermark", watermark && s.isWatermarkEnabled()),
        Map.entry("shadow", shadow != null ? shadow : s.isShadowEnabled()),
        Map.entry("fillRatio", clamp(fill, 0.55, 0.92)),
        Map.entry("zoom", clamp(zoom == null ? 1 : zoom, 0.6, 2.2)),
        Map.entry("offsetX", clamp(offsetX == null ? 0 : offsetX, -0.4, 0.4)),
        Map.entry("offsetY", clamp(offsetY == null ? 0 : offsetY, -0.4, 0.4)),
        Map.entry("engine", s.getEngine()),
        Map.entry("watermarkPosition", s.getWatermarkPosition()),
        Map.entry("watermarkSize", s.getWatermarkSize()),
        Map.entry("watermarkOpacity", s.getWatermarkOpacity()),
        Map.entry("watermarkMargin", s.getWatermarkMargin()),
        Map.entry("watermarkVariant", s.getWatermarkVariant())
    );
  }

  @Transactional
  public PhotoStudioSettings update(String engine, Boolean watermarkEnabled, String position, Double size,
                                    Double opacity, Double margin, String variant, Double fillRatio, Boolean shadow) {
    PhotoStudioSettings s = current();
    if (engine != null && !engine.isBlank()) {
      String e = engine.trim().toLowerCase(Locale.ROOT);
      if (!ENGINES.contains(e)) throw new IllegalArgumentException("Moteur inconnu (local, rembg ou api)");
      s.setEngine(e);
    }
    if (watermarkEnabled != null) s.setWatermarkEnabled(watermarkEnabled);
    if (position != null && !position.isBlank()) {
      String p = position.trim().toUpperCase(Locale.ROOT);
      if (!POS.contains(p)) throw new IllegalArgumentException("Position de signature invalide");
      s.setWatermarkPosition(p);
    }
    if (size != null) s.setWatermarkSize(clamp(size, 0.03, 0.18));
    if (opacity != null) s.setWatermarkOpacity(clamp(opacity, 0.2, 1));
    if (margin != null) s.setWatermarkMargin(clamp(margin, 0.01, 0.08));
    if (variant != null && !variant.isBlank()) s.setWatermarkVariant(variant.trim());
    if (fillRatio != null) s.setFillRatio(clamp(fillRatio, 0.55, 0.92));
    if (shadow != null) s.setShadowEnabled(shadow);
    return settings.save(s);
  }

  private static double clamp(double v, double min, double max) { return Math.min(max, Math.max(min, v)); }
}
