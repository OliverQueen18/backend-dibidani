package ml.dibidani.payment;

import ml.dibidani.config.PaymentProperties;
import ml.dibidani.config.PaymentProperties.MoovMoney;
import ml.dibidani.domain.PaymentMethod;
import ml.dibidani.domain.PaymentProvider;
import ml.dibidani.domain.PaymentStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Moov Money (Moov Africa Mali), paiement par validation sur le téléphone du client.
 * Le contrat API est fourni par Moov à la signature marchande : l'URL, le chemin d'initiation et les identifiants
 * sont donc entièrement configurables (MOOV_MONEY_*), sans aucune valeur par défaut.
 * Les notifications doivent être signées en HMAC-SHA256 du corps (en-tête X-Moov-Signature) avec MOOV_MONEY_WEBHOOK_SECRET.
 * Le mapping des champs ci-dessous est à aligner sur la documentation Moov lors de l'intégration.
 */
@Component
public class MoovMoneyGateway implements PaymentGateway {
  public static final String SIGNATURE_HEADER = "x-moov-signature";

  private final MoovMoney config;
  private final ObjectMapper json;
  private final RestClient http = RestClient.create();

  public MoovMoneyGateway(PaymentProperties props, ObjectMapper json) {
    this.config = props.moovMoney();
    this.json = json;
  }

  @Override public PaymentProvider provider() { return PaymentProvider.MOOV_MONEY; }
  @Override public Set<PaymentMethod> methods() { return Set.of(PaymentMethod.MOBILE_MONEY); }
  @Override public boolean isConfigured() { return config != null && config.configured(); }
  @Override public String displayName() { return "Moov Money"; }

  @Override
  public InitiationResult initiate(InitiationRequest r) {
    if (!isConfigured()) throw new IllegalStateException("Moov Money n'est pas configuré");
    if (r.payerPhone() == null || r.payerPhone().isBlank()) {
      return new InitiationResult(PaymentStatus.FAILED, null, null, null, "Numéro Moov Money obligatoire", null);
    }
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("merchantId", config.merchantId());
      body.put("reference", r.paymentReference());
      body.put("amount", r.amount().setScale(0, RoundingMode.HALF_UP).longValueExact());
      body.put("currency", r.currency());
      body.put("msisdn", r.payerPhone().replaceAll("[^0-9]", ""));
      body.put("description", "Commande " + r.orderNumber());
      body.put("callbackUrl", r.notifyUrl());
      String raw = http.post().uri(config.baseUrl() + config.initiatePath())
          .header("Authorization", "Bearer " + config.apiKey())
          .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
          .body(json.writeValueAsString(body)).retrieve().body(String.class);
      JsonNode node = json.readTree(raw == null ? "{}" : raw);
      String reference = node.path("transactionId").asString(node.path("reference").asString(r.paymentReference()));
      return new InitiationResult(PaymentStatus.PENDING, reference, null, null, "Validez le paiement sur votre téléphone Moov", raw);
    } catch (RestClientException e) {
      return new InitiationResult(PaymentStatus.FAILED, null, null, null, "Moov Money est momentanément indisponible", null);
    }
  }

  @Override
  public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
    if (!isConfigured()) throw new WebhookVerificationException("Moov Money n'est pas configuré");
    if (!Hmac.matches(Hmac.sha256Hex(config.webhookSecret(), rawBody), headers.get(SIGNATURE_HEADER))) {
      throw new WebhookVerificationException("Signature invalide");
    }
    JsonNode node = json.readTree(rawBody);
    String reference = node.path("transactionId").asString(node.path("reference").asString(null));
    PaymentStatus status = switch (node.path("status").asString("").toUpperCase()) {
      case "SUCCESS", "SUCCESSFUL", "COMPLETED" -> PaymentStatus.SUCCESS;
      case "PENDING" -> PaymentStatus.PENDING;
      case "CANCELLED", "CANCELED" -> PaymentStatus.CANCELLED;
      default -> PaymentStatus.FAILED;
    };
    var amount = node.hasNonNull("amount") ? node.get("amount").decimalValue() : null;
    return new WebhookEvent(reference, null, status, amount, node.path("message").asString(null), rawBody);
  }
}
