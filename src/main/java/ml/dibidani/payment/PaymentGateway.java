package ml.dibidani.payment;

import ml.dibidani.domain.PaymentMethod;
import ml.dibidani.domain.PaymentProvider;
import ml.dibidani.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Contrat commun aux opérateurs de paiement. Une passerelle n'accède jamais à la base :
 * le PaymentService enregistre les transactions et fait avancer la commande.
 */
public interface PaymentGateway {

  PaymentProvider provider();

  /** Modes de paiement pris en charge (MOBILE_MONEY, BANK_CARD…). */
  Set<PaymentMethod> methods();

  /** Vrai lorsque tous les identifiants nécessaires sont présents dans l'environnement. */
  boolean isConfigured();

  String displayName();

  InitiationResult initiate(InitiationRequest request);

  /**
   * Vérifie l'authenticité d'une notification (signature HMAC, jeton secret…) et la traduit.
   * Doit lever {@link WebhookVerificationException} si la notification n'est pas authentique.
   */
  WebhookEvent parseWebhook(String rawBody, Map<String, String> headers);

  record InitiationRequest(String orderNumber, String paymentReference, BigDecimal amount, String currency, PaymentMethod method,
                           String payerPhone, String returnUrl, String cancelUrl, String notifyUrl) {}

  /**
   * status PENDING + checkoutUrl : rediriger le client vers l'opérateur.
   * status PENDING sans URL : validation sur le téléphone du client (USSD / push), en attente du webhook.
   */
  record InitiationResult(PaymentStatus status, String providerReference, String providerToken, String checkoutUrl, String message, String rawPayload) {}

  /** Le paiement est retrouvé par providerReference, ou à défaut par providerToken. */
  record WebhookEvent(String providerReference, String providerToken, PaymentStatus status, BigDecimal amount, String message, String rawPayload) {}

  class WebhookVerificationException extends RuntimeException {
    public WebhookVerificationException(String message) { super(message); }
  }
}
