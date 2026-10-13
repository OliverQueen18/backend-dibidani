package ml.dibidani.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {
  @Column(nullable = false) private String fullName;
  @Column(unique = true, nullable = false) private String email;
  @Column private String phone;
  @JsonIgnore @Column(nullable = false) private String passwordHash;
  @Column(length = 20) private String oauthProvider;
  @Column(length = 255) private String oauthSubject;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role = Role.CUSTOMER;
  @Column(nullable = false) private boolean active = true;
  private Instant lastLoginAt;
  @Column(nullable = false) private int failedLoginAttempts;
  private Instant lockedUntil;
  /** Incrémentée à chaque changement de mot de passe, de rôle ou désactivation : invalide les jetons d'accès déjà émis. */
  @Column(nullable = false) private int tokenVersion;
  private Instant passwordChangedAt;

  public String getFullName() { return fullName; }
  public void setFullName(String v) { fullName = v; }
  public String getEmail() { return email; }
  public void setEmail(String v) { email = v; }
  public String getPhone() { return phone; }
  public void setPhone(String v) { phone = v; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String v) { passwordHash = v; passwordChangedAt = Instant.now(); }
  public String getOauthProvider() { return oauthProvider; }
  public void setOauthProvider(String v) { oauthProvider = v; }
  public String getOauthSubject() { return oauthSubject; }
  public void setOauthSubject(String v) { oauthSubject = v; }
  public Role getRole() { return role; }
  public void setRole(Role v) { role = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
  public Instant getLastLoginAt() { return lastLoginAt; }
  public void setLastLoginAt(Instant v) { lastLoginAt = v; }
  public int getFailedLoginAttempts() { return failedLoginAttempts; }
  public void setFailedLoginAttempts(int v) { failedLoginAttempts = v; }
  public Instant getLockedUntil() { return lockedUntil; }
  public void setLockedUntil(Instant v) { lockedUntil = v; }
  public int getTokenVersion() { return tokenVersion; }
  public void revokeTokens() { tokenVersion++; }
  public Instant getPasswordChangedAt() { return passwordChangedAt; }

  public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }
}
