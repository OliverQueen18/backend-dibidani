package ml.dibidani.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_otps")
public class PasswordResetOtp extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY) private AppUser user;
  @Column(nullable = false) private String codeHash;
  @Column(nullable = false) private Instant expiresAt;
  @Column(nullable = false) private int attempts;
  private Instant consumedAt;
  @Column(nullable = false) private boolean emailSent;
  @Column(nullable = false) private boolean smsSent;
  @Column(length = 64) private String createdIp;

  public AppUser getUser() { return user; }
  public void setUser(AppUser v) { user = v; }
  public String getCodeHash() { return codeHash; }
  public void setCodeHash(String v) { codeHash = v; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant v) { expiresAt = v; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int v) { attempts = v; }
  public Instant getConsumedAt() { return consumedAt; }
  public void setConsumedAt(Instant v) { consumedAt = v; }
  public boolean isEmailSent() { return emailSent; }
  public void setEmailSent(boolean v) { emailSent = v; }
  public boolean isSmsSent() { return smsSent; }
  public void setSmsSent(boolean v) { smsSent = v; }
  public String getCreatedIp() { return createdIp; }
  public void setCreatedIp(String v) { createdIp = v; }

  public boolean isOpen(Instant now) {
    return consumedAt == null && expiresAt != null && expiresAt.isAfter(now);
  }
}
