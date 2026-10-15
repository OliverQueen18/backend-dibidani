package ml.dibidani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record AdminBootstrapProperties(String email, String password, String name, String phone) {

  public String normalizedEmail() {
    return email == null ? "" : email.trim().toLowerCase();
  }

  public String displayName() {
    return name == null || name.isBlank() ? "Administrateur Dibidani" : name.trim();
  }

  public String normalizedPhone() {
    if (phone == null || phone.isBlank()) return null;
    return phone.trim();
  }

  public boolean credentialsPresent() {
    return !normalizedEmail().isBlank() && password != null && !password.isBlank();
  }
}
