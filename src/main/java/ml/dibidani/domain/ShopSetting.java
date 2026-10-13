package ml.dibidani.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Surcharge d'un paramètre boutique (app.shop.*) modifiée depuis le back-office. */
@Entity
@Table(name = "shop_settings")
public class ShopSetting {
  @Id @Column(name = "setting_key") private String key;
  @Column(name = "setting_value", columnDefinition = "text") private String value;
  @Column(nullable = false) private Instant updatedAt = Instant.now();

  protected ShopSetting() {}

  public ShopSetting(String key, String value) {
    this.key = key;
    this.value = value;
  }

  @PreUpdate void touch() { updatedAt = Instant.now(); }

  public String getKey() { return key; }
  public String getValue() { return value; }
  public void setValue(String v) { value = v; }
  public Instant getUpdatedAt() { return updatedAt; }
}
