package ml.dibidani.notify;

import ml.dibidani.config.SmsGatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client REST de SMS Gateway for Android™ (https://sms-gate.app). */
@Component
public class SmsGatewayClient {
  private static final Logger log = LoggerFactory.getLogger(SmsGatewayClient.class);

  private final SmsGatewayProperties props;
  private final RestClient http = RestClient.create();

  public SmsGatewayClient(SmsGatewayProperties props) { this.props = props; }

  public boolean configured() { return props.configured(); }

  public boolean mockEnabled() { return props.mockEnabled(); }

  public void send(String phone, String text) {
    String to = Phones.e164(phone);
    if (to == null) throw new IllegalArgumentException("Numéro de téléphone invalide");
    if (!props.configured()) throw new IllegalStateException("SMS Gateway n'est pas configuré");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("textMessage", Map.of("text", text));
    body.put("phoneNumbers", List.of(to));
    body.put("priority", 100);
    body.put("ttl", 600);
    if (SmsGatewayProperties.present(props.deviceId())) body.put("deviceId", props.deviceId().trim());
    Integer sim = parseSim(props.simNumber());
    if (sim != null) body.put("simNumber", sim);

    try {
      http.post()
          .uri(props.apiBase() + "/messages")
          .headers(h -> {
            if (SmsGatewayProperties.present(props.token())) h.setBearerAuth(props.token().trim());
            else h.setBasicAuth(props.username().trim(), props.password());
          })
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      log.warn("SMS Gateway a refusé l'envoi vers {} : {}", to, e.getMessage());
      throw e;
    }
  }

  private static Integer parseSim(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      int n = Integer.parseInt(raw.trim());
      return n > 0 ? n : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
