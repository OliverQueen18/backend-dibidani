package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "site_blocks")
public class SiteBlock extends BaseEntity {
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SiteBlockKind kind;

  @Column(length = 80)
  private String slug;

  @Column(nullable = false)
  private String title;

  @Column(length = 500)
  private String subtitle;

  @Column(columnDefinition = "text")
  private String body;

  private String imageUrl;
  private String linkUrl;

  @Column(length = 80)
  private String ctaLabel;

  /** Initials (témoignage) ou couleur (partenaire). */
  @Column(length = 120)
  private String meta;

  @Column(nullable = false)
  private Integer displayOrder = 0;

  @Column(nullable = false)
  private boolean active = true;

  public SiteBlockKind getKind() { return kind; }
  public void setKind(SiteBlockKind v) { kind = v; }
  public String getSlug() { return slug; }
  public void setSlug(String v) { slug = v; }
  public String getTitle() { return title; }
  public void setTitle(String v) { title = v; }
  public String getSubtitle() { return subtitle; }
  public void setSubtitle(String v) { subtitle = v; }
  public String getBody() { return body; }
  public void setBody(String v) { body = v; }
  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String v) { imageUrl = v; }
  public String getLinkUrl() { return linkUrl; }
  public void setLinkUrl(String v) { linkUrl = v; }
  public String getCtaLabel() { return ctaLabel; }
  public void setCtaLabel(String v) { ctaLabel = v; }
  public String getMeta() { return meta; }
  public void setMeta(String v) { meta = v; }
  public Integer getDisplayOrder() { return displayOrder; }
  public void setDisplayOrder(Integer v) { displayOrder = v == null ? 0 : v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
}
