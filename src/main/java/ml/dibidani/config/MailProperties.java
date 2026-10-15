package ml.dibidani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String from, String fromName, boolean mockEnabled) {

  public String sender() {
    return from == null || from.isBlank() ? "contact@dibidani.com" : from.trim();
  }

  public String senderName() {
    return fromName == null || fromName.isBlank() ? "Dibidani" : fromName.trim();
  }
}
