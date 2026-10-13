package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "product_image_assets")
public class ProductImageAsset extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY) private Product product;
  @ManyToOne(fetch = FetchType.LAZY) private Vendor vendor;
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private AppUser owner;
  @Column(nullable = false, length = 500) private String originalPath;
  @Column(length = 80) private String originalMime;
  private Integer originalWidth;
  private Integer originalHeight;
  private Integer originalBytes;
  @Column(length = 64) private String sha256;
  @Column(length = 500) private String masterUrl;
  @Column(length = 500) private String catalogUrl;
  @Column(length = 500) private String thumbUrl;
  @Column(length = 500) private String jpegUrl;
  @Column(length = 32) private String processingVersion;
  @Column(length = 40) private String engine;
  @Column(columnDefinition = "text") private String paramsJson;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ProductImageStatus status = ProductImageStatus.UPLOADED;
  @Column(nullable = false) private boolean confirmed;

  public Product getProduct() { return product; }
  public void setProduct(Product v) { product = v; }
  public Vendor getVendor() { return vendor; }
  public void setVendor(Vendor v) { vendor = v; }
  public AppUser getOwner() { return owner; }
  public void setOwner(AppUser v) { owner = v; }
  public String getOriginalPath() { return originalPath; }
  public void setOriginalPath(String v) { originalPath = v; }
  public String getOriginalMime() { return originalMime; }
  public void setOriginalMime(String v) { originalMime = v; }
  public Integer getOriginalWidth() { return originalWidth; }
  public void setOriginalWidth(Integer v) { originalWidth = v; }
  public Integer getOriginalHeight() { return originalHeight; }
  public void setOriginalHeight(Integer v) { originalHeight = v; }
  public Integer getOriginalBytes() { return originalBytes; }
  public void setOriginalBytes(Integer v) { originalBytes = v; }
  public String getSha256() { return sha256; }
  public void setSha256(String v) { sha256 = v; }
  public String getMasterUrl() { return masterUrl; }
  public void setMasterUrl(String v) { masterUrl = v; }
  public String getCatalogUrl() { return catalogUrl; }
  public void setCatalogUrl(String v) { catalogUrl = v; }
  public String getThumbUrl() { return thumbUrl; }
  public void setThumbUrl(String v) { thumbUrl = v; }
  public String getJpegUrl() { return jpegUrl; }
  public void setJpegUrl(String v) { jpegUrl = v; }
  public String getProcessingVersion() { return processingVersion; }
  public void setProcessingVersion(String v) { processingVersion = v; }
  public String getEngine() { return engine; }
  public void setEngine(String v) { engine = v; }
  public String getParamsJson() { return paramsJson; }
  public void setParamsJson(String v) { paramsJson = v; }
  public ProductImageStatus getStatus() { return status; }
  public void setStatus(ProductImageStatus v) { status = v; }
  public boolean isConfirmed() { return confirmed; }
  public void setConfirmed(boolean v) { confirmed = v; }

  public String publicUrl() {
    if (masterUrl != null && !masterUrl.isBlank()) return masterUrl;
    if (jpegUrl != null && !jpegUrl.isBlank()) return jpegUrl;
    return catalogUrl;
  }
}
