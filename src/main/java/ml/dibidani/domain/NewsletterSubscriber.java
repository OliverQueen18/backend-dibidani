package ml.dibidani.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "newsletter_subscribers")
public class NewsletterSubscriber extends BaseEntity {
  @Column(nullable = false, unique = true) private String email;
  @Column(nullable = false) private boolean active = true;

  public String getEmail() { return email; }
  public void setEmail(String v) { email = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
}
