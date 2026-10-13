package ml.dibidani.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Refresh token opaque : seul son empreinte SHA-256 est stockée. Rotation à chaque usage, réutilisation = révocation de la famille. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private AppUser user;
  @Column(nullable = false, unique = true, length = 64) private String tokenHash;
  @Column(nullable = false, length = 64) private String familyId;
  @Column(nullable = false) private Instant expiresAt;
  private Instant revokedAt;
  private String revokedReason;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "replaced_by_id") private RefreshToken replacedBy;
  private String createdIp;
  private String userAgent;

  public AppUser getUser() { return user; }
  public void setUser(AppUser v) { user = v; }
  public String getTokenHash() { return tokenHash; }
  public void setTokenHash(String v) { tokenHash = v; }
  public String getFamilyId() { return familyId; }
  public void setFamilyId(String v) { familyId = v; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant v) { expiresAt = v; }
  public Instant getRevokedAt() { return revokedAt; }
  public String getRevokedReason() { return revokedReason; }
  public RefreshToken getReplacedBy() { return replacedBy; }
  public void setReplacedBy(RefreshToken v) { replacedBy = v; }
  public String getCreatedIp() { return createdIp; }
  public void setCreatedIp(String v) { createdIp = v; }
  public String getUserAgent() { return userAgent; }
  public void setUserAgent(String v) { userAgent = v; }

  public void revoke(String reason) {
    if (revokedAt != null) return;
    revokedAt = Instant.now();
    revokedReason = reason;
  }

  public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
}
