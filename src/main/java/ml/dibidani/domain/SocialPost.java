package ml.dibidani.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "social_posts")
public class SocialPost extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private Product product;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SocialChannel channel;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SocialPostStatus status = SocialPostStatus.PENDING;
  @Column(nullable = false, length = 2000) private String message;
  @Column(length = 500) private String productUrl;
  @Column(length = 1500) private String shareUrl;
  @Column(length = 255) private String externalId;
  @Column(length = 1000) private String errorMessage;
  @Column(nullable = false) private boolean mock;
  @ManyToOne(fetch = FetchType.LAZY) private AppUser publishedBy;
  private Instant publishedAt;

  public Product getProduct() { return product; }
  public void setProduct(Product v) { product = v; }
  public SocialChannel getChannel() { return channel; }
  public void setChannel(SocialChannel v) { channel = v; }
  public SocialPostStatus getStatus() { return status; }
  public void setStatus(SocialPostStatus v) { status = v; }
  public String getMessage() { return message; }
  public void setMessage(String v) { message = v; }
  public String getProductUrl() { return productUrl; }
  public void setProductUrl(String v) { productUrl = v; }
  public String getShareUrl() { return shareUrl; }
  public void setShareUrl(String v) { shareUrl = v; }
  public String getExternalId() { return externalId; }
  public void setExternalId(String v) { externalId = v; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String v) { errorMessage = v; }
  public boolean isMock() { return mock; }
  public void setMock(boolean v) { mock = v; }
  public AppUser getPublishedBy() { return publishedBy; }
  public void setPublishedBy(AppUser v) { publishedBy = v; }
  public Instant getPublishedAt() { return publishedAt; }
  public void setPublishedAt(Instant v) { publishedAt = v; }
}
