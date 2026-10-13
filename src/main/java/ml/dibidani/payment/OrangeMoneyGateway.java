package ml.dibidani.payment;

import ml.dibidani.config.PaymentProperties;
import ml.dibidani.config.PaymentProperties.OrangeMoney;
import ml.dibidani.domain.PaymentMethod;
import ml.dibidani.domain.PaymentProvider;
import ml.dibidani.domain.PaymentStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Orange Money Web Payment (redirection vers la page de paiement Orange).
 * Flux : jeton OAuth2 client_credentials, création du paiement (payment_url, pay_token, notif_token), puis
 * notification serveur à notif_url contenant notif_token, comparé au jeton reçu à la création.
 * Toutes les valeurs viennent de l'environnement (ORANGE_MONEY_*) ; la passerelle reste inactive tant qu'elles manquent.
 * À valider en sandbox avec le contrat marchand Orange Mali avant mise en production.
 */
@Component
public class OrangeMoneyGateway implements PaymentGateway {
  private final OrangeMoney config;
  private final ObjectMapper json;
  private final RestClient http = RestClient.create();

  public OrangeMoneyGateway(PaymentProperties props, ObjectMapper json) {
    this.config = props.orangeMoney();
    this.json = json;
  }

  @Override public PaymentProvider provider() { return PaymentProvider.ORANGE_MONEY; }
  @Override public Set<PaymentMethod> methods() { return Set.of(PaymentMethod.MOBILE_MONEY); }
  @Override public boolean isConfigured() { return config != null && config.configured(); }
  @Override public String displayName() { return "Orange Money"; }

  @Override
  public InitiationResult initiate(InitiationRequest r) {
    if (!isConfigured()) throw new IllegalStateException("Orange Money n'est pas configuré");
    try {
      String token = accessToken();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("merchant_key", config.merchantKey());
      body.put("currency", config.currency() == null || config.currency().isBlank() ? "XOF" : config.currency());
      body.put("order_id", r.paymentReference());
      body.put("amount", r.amount().setScale(0, java.math.RoundingMode.HALF_UP).longValueExact());
      body.put("return_url", r.returnUrl());
      body.put("cancel_url", r.cancelUrl());
      body.put("notif_url", r.notifyUrl());
      body.put("lang", "fr");
      body.put("reference", r.orderNumber());
      String raw = http.post().uri(config.baseUrl() + config.webpayPath())
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
          .body(json.writeValueAsString(body)).retrieve().body(String.class);
      JsonNode node = json.readTree(raw == null ? "{}" : raw);
      String payToken = node.path("pay_token").asString(null);
      String paymentUrl = node.path("payment_url").asString(null);
      if (payToken == null || paymentUrl == null) {
        return new InitiationResult(PaymentStatus.FAILED, null, null, null, "Orange Money a refusé la demande de paiement", raw);
      }
      return new InitiationResult(PaymentStatus.PENDING, payToken, node.path("notif_token").asString(null), paymentUrl,
          "Finalisez le paiement sur la page Orange Money", raw);
    } catch (RestClientException e) {
      return new InitiationResult(PaymentStatus.FAILED, null, null, null, "Orange Money est momentanément indisponible", null);
    }
  }

  /** Notification Orange : {"status":"SUCCESS","notif_token":"…","txnid":"…"} ; l'authenticité repose sur notif_token. */
  @Override
  public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
    if (!isConfigured()) throw new WebhookVerificationException("Orange Money n'est pas configuré");
    JsonNode node = json.readTree(rawBody);
    String notifToken = node.path("notif_token").asString(null);
    if (notifToken == null || notifToken.isBlank()) throw new WebhookVerificationException("notif_token absent");
    PaymentStatus status = switch (node.path("status").asString("").toUpperCase()) {
      case "SUCCESS" -> PaymentStatus.SUCCESS;
      case "PENDING", "INITIATED" -> PaymentStatus.PENDING;
      case "EXPIRED", "CANCELLED" -> PaymentStatus.CANCELLED;
      default -> PaymentStatus.FAILED;
    };
    return new WebhookEvent(null, notifToken, status, null, node.path("txnid").asString(null), rawBody);
  }

  private String accessToken() {
    String basic = Base64.getEncoder().encodeToString((config.clientId() + ":" + config.clientSecret()).getBytes(StandardCharsets.UTF_8));
    String raw = http.post().uri(config.baseUrl() + "/oauth/v3/token")
        .header("Authorization", "Basic " + basic)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
        .body("grant_type=client_credentials").retrieve().body(String.class);
    String token = json.readTree(raw == null ? "{}" : raw).path("access_token").asString(null);
    if (token == null) throw new RestClientException("Jeton Orange Money absent");
    return token;
  }
}
