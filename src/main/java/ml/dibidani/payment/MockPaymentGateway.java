package ml.dibidani.payment;

import ml.dibidani.common.Tokens;
import ml.dibidani.config.PaymentProperties;
import ml.dibidani.domain.PaymentMethod;
import ml.dibidani.domain.PaymentProvider;
import ml.dibidani.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Passerelle de test, fonctionnelle de bout en bout. Scénarios selon les 4 derniers chiffres du téléphone payeur :
 * 0000 : refusé ; 1111 : en attente (validation simulée par webhook) ; autre : accepté immédiatement.
 * Le webhook est signé en HMAC-SHA256 (en-tête X-Dibidani-Signature) avec PAYMENT_MOCK_WEBHOOK_SECRET ;
 * sans secret configuré, un secret aléatoire est généré au démarrage et le webhook reste inutilisable de l'extérieur.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {
  private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
  public static final String SIGNATURE_HEADER = "x-dibidani-signature";

  private final boolean enabled;
  private final String webhookSecret;
  private final ObjectMapper json;

  public MockPaymentGateway(PaymentProperties props, ObjectMapper json) {
    this.enabled = props.mock() != null && props.mock().enabled();
    String secret = props.mock() == null ? null : props.mock().webhookSecret();
    this.webhookSecret = secret == null || secret.isBlank() ? Tokens.secure() : secret;
    this.json = json;
    if (enabled) log.warn("Passerelle de paiement de TEST active (PAYMENT_MOCK_ENABLED=true) : à désactiver en production.");
  }

  @Override public PaymentProvider provider() { return PaymentProvider.MOCK; }
  @Override public Set<PaymentMethod> methods() { return Set.of(PaymentMethod.MOBILE_MONEY, PaymentMethod.BANK_CARD); }
  @Override public boolean isConfigured() { return enabled; }
  @Override public String displayName() { return "Paiement de test"; }

  @Override
  public InitiationResult initiate(InitiationRequest r) {
    String reference = "MOCK-" + Tokens.readable(12);
    String phone = r.payerPhone() == null ? "" : r.payerPhone().replaceAll("[^0-9]", "");
    String payload = "{\"reference\":\"" + reference + "\",\"order\":\"" + r.orderNumber() + "\"}";
    if (phone.endsWith("0000")) return new InitiationResult(PaymentStatus.FAILED, reference, null, null, "Paiement refusé (solde insuffisant simulé)", payload);
    if (phone.endsWith("1111")) return new InitiationResult(PaymentStatus.PENDING, reference, null, null, "Validez le paiement sur votre téléphone", payload);
    return new InitiationResult(PaymentStatus.SUCCESS, reference, null, null, "Paiement accepté", payload);
  }

  /** Corps attendu : {"reference":"MOCK-…","status":"SUCCESS|FAILED|CANCELLED","amount":12345}. */
  @Override
  public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
    if (!enabled) throw new WebhookVerificationException("Passerelle de test désactivée");
    String expected = Hmac.sha256Hex(webhookSecret, rawBody);
    if (!Hmac.matches(expected, headers.get(SIGNATURE_HEADER))) throw new WebhookVerificationException("Signature invalide");
    JsonNode node = json.readTree(rawBody);
    String reference = node.path("reference").asString(null);
    PaymentStatus status = switch (node.path("status").asString("").toUpperCase()) {
      case "SUCCESS" -> PaymentStatus.SUCCESS;
      case "CANCELLED" -> PaymentStatus.CANCELLED;
      default -> PaymentStatus.FAILED;
    };
    BigDecimal amount = node.hasNonNull("amount") ? node.get("amount").decimalValue() : null;
    return new WebhookEvent(reference, null, status, amount, node.path("message").asString(null), rawBody);
  }
}
