package ml.dibidani.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "advertisements")
public class Advertisement extends BaseEntity {
  @Column(nullable = false) private String title;
  @Column(columnDefinition = "text") private String description;
  private String imageUrl;
  private String mobileImageUrl;
  private String targetUrl;
  private String ctaLabel;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private AdPosition position;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private AdType type = AdType.BANNER;
  @Column(nullable = false) private Instant startDate;
  @Column(nullable = false) private Instant endDate;
  @Column(nullable = false) private Integer displayOrder = 0;
  @Column(nullable = false) private boolean active = true;

  public String getTitle() { return title; }
  public void setTitle(String v) { title = v; }
  public String getDescription() { return description; }
  public void setDescription(String v) { description = v; }
  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String v) { imageUrl = v; }
  public String getMobileImageUrl() { return mobileImageUrl; }
  public void setMobileImageUrl(String v) { mobileImageUrl = v; }
  public String getTargetUrl() { return targetUrl; }
  public void setTargetUrl(String v) { targetUrl = v; }
  public String getCtaLabel() { return ctaLabel; }
  public void setCtaLabel(String v) { ctaLabel = v; }
  public AdPosition getPosition() { return position; }
  public void setPosition(AdPosition v) { position = v; }
  public AdType getType() { return type; }
  public void setType(AdType v) { type = v; }
  public Instant getStartDate() { return startDate; }
  public void setStartDate(Instant v) { startDate = v; }
  public Instant getEndDate() { return endDate; }
  public void setEndDate(Instant v) { endDate = v; }
  public Integer getDisplayOrder() { return displayOrder; }
  public void setDisplayOrder(Integer v) { displayOrder = v == null ? 0 : v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
}
