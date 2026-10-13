package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {
  private Long actorId;
  private String actorEmail;
  @Column(nullable = false, length = 80) private String action;
  @Column(length = 60) private String entityType;
  @Column(length = 80) private String entityId;
  @Column(nullable = false) private boolean success = true;
  @Column(length = 64) private String ip;
  @Column(length = 300) private String userAgent;
  @Column(length = 2000) private String details;

  public Long getActorId() { return actorId; }
  public void setActorId(Long v) { actorId = v; }
  public String getActorEmail() { return actorEmail; }
  public void setActorEmail(String v) { actorEmail = v; }
  public String getAction() { return action; }
  public void setAction(String v) { action = v; }
  public String getEntityType() { return entityType; }
  public void setEntityType(String v) { entityType = v; }
  public String getEntityId() { return entityId; }
  public void setEntityId(String v) { entityId = v; }
  public boolean isSuccess() { return success; }
  public void setSuccess(boolean v) { success = v; }
  public String getIp() { return ip; }
  public void setIp(String v) { ip = v; }
  public String getUserAgent() { return userAgent; }
  public void setUserAgent(String v) { userAgent = v; }
  public String getDetails() { return details; }
  public void setDetails(String v) { details = v; }
}
