package ml.dibidani.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "product_image_jobs")
public class ProductImageJob extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private ProductImageAsset asset;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ProductImageJobStatus status = ProductImageJobStatus.PENDING;
  @Column(nullable = false) private int progress;
  @Column(length = 500) private String message;
  @Column(columnDefinition = "text") private String optionsJson;
  private Instant startedAt;
  private Instant finishedAt;
  @Column(length = 255) private String createdBy;

  public ProductImageAsset getAsset() { return asset; }
  public void setAsset(ProductImageAsset v) { asset = v; }
  public ProductImageJobStatus getStatus() { return status; }
  public void setStatus(ProductImageJobStatus v) { status = v; }
  public int getProgress() { return progress; }
  public void setProgress(int v) { progress = v; }
  public String getMessage() { return message; }
  public void setMessage(String v) { message = v; }
  public String getOptionsJson() { return optionsJson; }
  public void setOptionsJson(String v) { optionsJson = v; }
  public Instant getStartedAt() { return startedAt; }
  public void setStartedAt(Instant v) { startedAt = v; }
  public Instant getFinishedAt() { return finishedAt; }
  public void setFinishedAt(Instant v) { finishedAt = v; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String v) { createdBy = v; }
}
