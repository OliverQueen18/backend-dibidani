package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "photo_studio_settings")
public class PhotoStudioSettings extends BaseEntity {
  @Column(nullable = false, length = 40) private String engine = "local";
  @Column(nullable = false) private boolean watermarkEnabled = true;
  @Column(nullable = false, length = 40) private String watermarkPosition = "BOTTOM_RIGHT";
  @Column(nullable = false) private double watermarkSize = 0.06;
  @Column(nullable = false) private double watermarkOpacity = 0.65;
  @Column(nullable = false) private double watermarkMargin = 0.025;
  @Column(nullable = false, length = 40) private String watermarkVariant = "favicon";
  @Column(nullable = false) private double fillRatio = 0.82;
  @Column(nullable = false) private boolean shadowEnabled;

  public String getEngine() { return engine; }
  public void setEngine(String v) { engine = v; }
  public boolean isWatermarkEnabled() { return watermarkEnabled; }
  public void setWatermarkEnabled(boolean v) { watermarkEnabled = v; }
  public String getWatermarkPosition() { return watermarkPosition; }
  public void setWatermarkPosition(String v) { watermarkPosition = v; }
  public double getWatermarkSize() { return watermarkSize; }
  public void setWatermarkSize(double v) { watermarkSize = v; }
  public double getWatermarkOpacity() { return watermarkOpacity; }
  public void setWatermarkOpacity(double v) { watermarkOpacity = v; }
  public double getWatermarkMargin() { return watermarkMargin; }
  public void setWatermarkMargin(double v) { watermarkMargin = v; }
  public String getWatermarkVariant() { return watermarkVariant; }
  public void setWatermarkVariant(String v) { watermarkVariant = v; }
  public double getFillRatio() { return fillRatio; }
  public void setFillRatio(double v) { fillRatio = v; }
  public boolean isShadowEnabled() { return shadowEnabled; }
  public void setShadowEnabled(boolean v) { shadowEnabled = v; }
}
