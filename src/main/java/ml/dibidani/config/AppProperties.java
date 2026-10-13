package ml.dibidani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String publicUrl, Orders orders) {

  public record Orders(int paymentTimeoutHours) {}

  public String publicBaseUrl() {
    String url = publicUrl == null || publicUrl.isBlank() ? "http://localhost:4200" : publicUrl.trim();
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  public String trackingUrl(String token) { return publicBaseUrl() + "/suivi/" + token; }
}
