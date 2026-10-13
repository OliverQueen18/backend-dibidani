package ml.dibidani.service;

import ml.dibidani.api.dto.OrderDtos.MarkPaidRequest;
import ml.dibidani.api.dto.OrderDtos.PaymentSummary;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.PaymentDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.common.Tokens;
import ml.dibidani.config.AppProperties;
import ml.dibidani.domain.*;
import ml.dibidani.payment.MockPaymentGateway;
import ml.dibidani.payment.PaymentGateway;
import ml.dibidani.payment.PaymentGateway.*;
import ml.dibidani.repository.CustomerOrderRepository;
import ml.dibidani.repository.PaymentRepository;
import ml.dibidani.repository.PaymentTransactionRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestration des paiements. Les appels aux opérateurs se font hors transaction (aucun verrou base tenu pendant un appel réseau) ;
 * l'état est ensuite appliqué dans une transaction courte, de façon idempotente.
 */
@Service
public class PaymentService {
  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final Map<PaymentProvider, PaymentGateway> gateways;
  private final PaymentRepository payments;
  private final PaymentTransactionRepository transactions;
  private final CustomerOrderRepository orders;
  private final OrderWorkflow workflow;
  private final OrderMapper mapper;
  private final ShopService shop;
  private final AppProperties app;
  private final TransactionTemplate tx;

  public PaymentService(List<PaymentGateway> gateways, PaymentRepository payments, PaymentTransactionRepository transactions,
                        CustomerOrderRepository orders, OrderWorkflow workflow, OrderMapper mapper, ShopService shop, AppProperties app,
                        TransactionTemplate tx) {
    this.gateways = gateways.stream().collect(Collectors.toMap(PaymentGateway::provider, Function.identity()));
    this.payments = payments;
    this.transactions = transactions;
    this.orders = orders;
    this.workflow = workflow;
    this.mapper = mapper;
    this.shop = shop;
    this.app = app;
    this.tx = tx;
  }

  private boolean mockEnabled() { return gateways.get(PaymentProvider.MOCK).isConfigured(); }

  /** Modes proposés au client, selon les passerelles réellement configurées. */
  public List<PaymentOption> options() {
    boolean mock = mockEnabled();
    List<ProviderOption> mobile = new ArrayList<>();
    for (PaymentProvider p : List.of(PaymentProvider.ORANGE_MONEY, PaymentProvider.MOOV_MONEY)) {
      PaymentGateway g = gateways.get(p);
      if (g.isConfigured()) mobile.add(new ProviderOption(p, g.displayName(), false));
      else if (mock) mobile.add(new ProviderOption(p, g.displayName(), true));
    }
    String transfer = shop.get("bankTransferDetails");
    return List.of(
        new PaymentOption(PaymentMethod.CASH_ON_DELIVERY, PaymentMethod.CASH_ON_DELIVERY.label(), false, true, List.of(), false,
            "Payez en espèces à la réception de votre commande."),
        new PaymentOption(PaymentMethod.MOBILE_MONEY, PaymentMethod.MOBILE_MONEY.label(), true, !mobile.isEmpty(), mobile, true,
            "Orange Money ou Moov Money : validez le paiement sur votre téléphone."),
        new PaymentOption(PaymentMethod.BANK_CARD, PaymentMethod.BANK_CARD.label(), true, mock,
            mock ? List.of(new ProviderOption(PaymentProvider.MOCK, "Carte (test)", true)) : List.of(), false,
            mock ? "Paiement par carte en mode test." : "Bientôt disponible."),
        new PaymentOption(PaymentMethod.BANK_TRANSFER, PaymentMethod.BANK_TRANSFER.label(), false, transfer != null, List.of(), false,
            transfer != null ? transfer : "Non disponible."),
        new PaymentOption(PaymentMethod.CASH_AT_STORE, PaymentMethod.CASH_AT_STORE.label(), false, true, List.of(), false,
            "Réglez au retrait de votre commande au marché."));
  }

  /** Valide le couple mode / opérateur choisi au checkout et renvoie l'opérateur effectif à enregistrer. */
  public PaymentProvider validateChoice(PaymentMethod method, PaymentProvider provider, DeliveryMethod delivery) {
    var option = options().stream().filter(o -> o.method() == method).findFirst().orElseThrow();
    if (!option.available()) throw new IllegalArgumentException(method.label() + " n'est pas disponible actuellement");
    if (method == PaymentMethod.CASH_AT_STORE && delivery != null && delivery != DeliveryMethod.PICKUP) {
      throw new IllegalArgumentException("Le paiement en magasin est réservé au retrait au marché");
    }
    if (method == PaymentMethod.CASH_ON_DELIVERY && delivery == DeliveryMethod.PICKUP) {
      throw new IllegalArgumentException("Pour un retrait au marché, choisissez le paiement en magasin");
    }
    if (!method.isOnline()) return PaymentProvider.MANUAL;
    if (method == PaymentMethod.BANK_CARD) return PaymentProvider.MOCK;
    if (provider == null) throw new IllegalArgumentException("Choisissez votre opérateur Mobile Money");
    if (option.providers().stream().noneMatch(p -> p.provider() == provider)) throw new IllegalArgumentException("Opérateur indisponible");
    return provider;
  }

  private PaymentGateway gatewayFor(Payment p) {
    PaymentGateway real = gateways.get(p.getProvider());
    if (real != null && real.isConfigured() && real.methods().contains(p.getMethod())) return real;
    PaymentGateway mock = gateways.get(PaymentProvider.MOCK);
    if (mock.isConfigured()) return mock;
    throw new IllegalArgumentException("Aucune passerelle disponible pour " + p.getMethod().label());
  }

  /** Crée le paiement en attente d'une nouvelle commande (dans la transaction de création). */
  public Payment create(CustomerOrder order, PaymentMethod method, PaymentProvider provider, String payerPhone) {
    var p = new Payment();
    p.setOrder(order);
    p.setMethod(method);
    p.setProvider(provider);
    p.setAmount(order.getTotal());
    p.setPayerPhone(Slugs.blankToNull(payerPhone));
    p.setStatus(PaymentStatus.PENDING);
    return payments.save(p);
  }

  /**
   * Lance le paiement en ligne d'une commande en attente. Sans effet pour les paiements hors ligne.
   * Renvoie l'état du paiement après l'appel à la passerelle.
   */
  public PaymentSummary start(Long orderId) {
    record Snapshot(Long paymentId, InitiationRequest request, PaymentGateway gateway) {}
    Snapshot snap = tx.execute(s -> {
      var p = payments.lockByOrderId(orderId).orElseThrow(() -> new NotFoundException("Paiement introuvable"));
      var o = p.getOrder();
      if (!p.getMethod().isOnline() || p.getStatus() != PaymentStatus.PENDING || o.getStatus() != OrderStatus.PENDING_PAYMENT) return null;
      String reference = o.getOrderNumber() + "-" + Tokens.readable(4);
      String base = app.publicBaseUrl();
      var request = new InitiationRequest(o.getOrderNumber(), reference, p.getAmount(), p.getCurrency(), p.getMethod(), p.getPayerPhone(),
          base + "/paiement/retour?commande=" + o.getOrderNumber(), base + "/paiement/retour?commande=" + o.getOrderNumber() + "&annule=1",
          base + "/api/v1/payments/webhooks/" + slug(p.getProvider()));
      return new Snapshot(p.getId(), request, gatewayFor(p));
    });
    if (snap == null) return current(orderId);

    InitiationResult result;
    try {
      result = snap.gateway().initiate(snap.request());
    } catch (RuntimeException e) {
      log.warn("Échec d'initiation du paiement {} : {}", snap.request().orderNumber(), e.getMessage());
      result = new InitiationResult(PaymentStatus.FAILED, null, null, null, "Le service de paiement est indisponible, réessayez plus tard", null);
    }
    InitiationResult r = result;
    tx.executeWithoutResult(s -> {
      var p = payments.lockByOrderId(orderId).orElseThrow();
      if (p.getStatus() != PaymentStatus.PENDING) return;
      p.setProviderReference(r.providerReference());
      p.setProviderToken(r.providerToken());
      p.setCheckoutUrl(r.checkoutUrl());
      record(p, TransactionType.INITIATE, r.status(), snap.gateway().provider(), r.providerReference(), r.message(), r.rawPayload());
      apply(p, r.status(), r.message(), snap.gateway() instanceof MockPaymentGateway ? "Paiement de test" : OrderWorkflow.GATEWAY);
    });
    return current(orderId);
  }

  /** Nouvelle tentative après un échec, éventuellement avec un autre opérateur ou numéro. */
  @Transactional
  public void prepareRetry(CustomerOrder o, PaymentMethod method, PaymentProvider provider, String phone) {
    var p = payments.lockByOrderId(o.getId()).orElseThrow(() -> new NotFoundException("Paiement introuvable"));
    if (p.getStatus() == PaymentStatus.SUCCESS) throw new ConflictException("Cette commande est déjà payée");
    if (o.getStatus() != OrderStatus.PENDING_PAYMENT && o.getStatus() != OrderStatus.PAYMENT_FAILED) {
      throw new ConflictException("Cette commande n'est plus en attente de paiement");
    }
    PaymentMethod m = method != null ? method : p.getMethod();
    if (!m.isOnline()) throw new IllegalArgumentException("Choisissez un paiement en ligne");
    p.setMethod(m);
    p.setProvider(validateChoice(m, provider != null ? provider : p.getProvider(), o.getDeliveryMethod()));
    if (phone != null && !phone.isBlank()) p.setPayerPhone(phone.trim());
    p.setStatus(PaymentStatus.PENDING);
    p.setFailureReason(null);
    p.setCheckoutUrl(null);
    p.setProviderToken(null);
    o.setPaymentMethod(m.name());
    o.setPaymentProvider(p.getProvider().name());
    o.setPaymentStatus(PaymentStatus.PENDING);
    if (o.getStatus() == OrderStatus.PAYMENT_FAILED) workflow.transition(o, OrderStatus.PENDING_PAYMENT, null, OrderWorkflow.SYSTEM, "Nouvelle tentative de paiement");
  }

  /** Webhook opérateur : authentifié par la passerelle, idempotent, montant contrôlé. */
  public void webhook(String providerSlug, String body, Map<String, String> headers) {
    PaymentProvider provider = switch (providerSlug) {
      case "mock" -> PaymentProvider.MOCK;
      case "orange-money" -> PaymentProvider.ORANGE_MONEY;
      case "moov-money" -> PaymentProvider.MOOV_MONEY;
      default -> throw new NotFoundException("Opérateur inconnu");
    };
    PaymentGateway gateway = gateways.get(provider);
    WebhookEvent event = gateway.parseWebhook(body, headers);
    tx.executeWithoutResult(s -> {
      Optional<Payment> found = Optional.empty();
      if (event.providerReference() != null) {
        found = payments.lockByProviderReference(provider, event.providerReference());
        if (found.isEmpty() && provider == PaymentProvider.MOCK) {
          found = findByReferenceAnyProvider(event.providerReference());
        }
      }
      if (found.isEmpty() && event.providerToken() != null) found = findByToken(event.providerToken());
      var p = found.orElseThrow(() -> new NotFoundException("Paiement introuvable"));
      if (event.providerToken() != null && !constantTimeEquals(p.getProviderToken(), event.providerToken())) {
        throw new WebhookVerificationException("Jeton de notification invalide");
      }
      if (event.amount() != null && event.amount().compareTo(p.getAmount()) != 0) {
        record(p, TransactionType.WEBHOOK, PaymentStatus.FAILED, provider, event.providerReference(), "Montant notifié incohérent : " + event.amount(), event.rawPayload());
        log.warn("Webhook {} : montant {} différent de {}", p.getOrder().getOrderNumber(), event.amount(), p.getAmount());
        return;
      }
      if (p.getStatus() != PaymentStatus.PENDING) return;
      record(p, TransactionType.WEBHOOK, event.status(), provider, event.providerReference(), event.message(), event.rawPayload());
      apply(p, event.status(), event.message(), OrderWorkflow.GATEWAY);
    });
  }

  /** Les paiements MOCK sont enregistrés avec l'opérateur choisi par le client (ORANGE_MONEY…) mais une référence MOCK-. */
  private Optional<Payment> findByReferenceAnyProvider(String reference) {
    for (PaymentProvider p : PaymentProvider.values()) {
      var found = payments.lockByProviderReference(p, reference);
      if (found.isPresent()) return found;
    }
    return Optional.empty();
  }

  private Optional<Payment> findByToken(String token) {
    Specification<Payment> spec = (root, q, cb) -> cb.equal(root.get("providerToken"), token);
    return payments.findAll(spec).stream().findFirst().flatMap(p -> payments.lockByOrderId(p.getOrder().getId()));
  }

  private static boolean constantTimeEquals(String a, String b) {
    return a != null && b != null && java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes());
  }

  /** Encaissement constaté par l'équipe : espèces, virement reçu, paiement en magasin. */
  @Transactional
  public void markPaid(Long orderId, MarkPaidRequest r, AppUser actor) {
    var p = payments.lockByOrderId(orderId).orElseThrow(() -> new NotFoundException("Paiement introuvable"));
    var o = p.getOrder();
    if (p.getStatus() == PaymentStatus.SUCCESS) throw new ConflictException("Ce paiement est déjà encaissé");
    if (p.getStatus() == PaymentStatus.REFUNDED || p.getStatus() == PaymentStatus.CANCELLED || o.getStatus() == OrderStatus.CANCELLED) {
      throw new ConflictException("Ce paiement est clôturé");
    }
    if (r.reference() != null && !r.reference().isBlank()) p.setProviderReference(r.reference().trim());
    if (p.getProvider() == null) p.setProvider(PaymentProvider.MANUAL);
    String message = "Encaissement manuel par " + actor.getFullName() + (r.comment() == null || r.comment().isBlank() ? "" : " : " + r.comment().trim());
    record(p, TransactionType.MANUAL, PaymentStatus.SUCCESS, PaymentProvider.MANUAL, p.getProviderReference(), message, null);
    p.setStatus(PaymentStatus.SUCCESS);
    p.setPaidAt(Instant.now());
    p.setFailureReason(null);
    o.setPaymentStatus(PaymentStatus.SUCCESS);
    if (o.getStatus() == OrderStatus.PENDING_PAYMENT || o.getStatus() == OrderStatus.PAYMENT_FAILED) {
      if (o.getStatus() == OrderStatus.PAYMENT_FAILED) workflow.transition(o, OrderStatus.PENDING_PAYMENT, actor, null, "Paiement reçu");
      workflow.transition(o, OrderStatus.PAID, actor, null, r.comment());
    }
  }

  private void apply(Payment p, PaymentStatus status, String message, String actorName) {
    var o = p.getOrder();
    switch (status) {
      case SUCCESS -> {
        p.setStatus(PaymentStatus.SUCCESS);
        p.setPaidAt(Instant.now());
        p.setFailureReason(null);
        o.setPaymentStatus(PaymentStatus.SUCCESS);
        if (o.getStatus() == OrderStatus.PENDING_PAYMENT) workflow.transition(o, OrderStatus.PAID, null, actorName, message);
      }
      case FAILED, CANCELLED -> {
        p.setStatus(PaymentStatus.FAILED);
        p.setFailureReason(message);
        o.setPaymentStatus(PaymentStatus.FAILED);
        if (o.getStatus() == OrderStatus.PENDING_PAYMENT) workflow.transition(o, OrderStatus.PAYMENT_FAILED, null, actorName, message);
      }
      default -> { }
    }
  }

  private void record(Payment p, TransactionType type, PaymentStatus status, PaymentProvider provider, String reference, String message, String raw) {
    var t = new PaymentTransaction();
    t.setPayment(p);
    t.setType(type);
    t.setStatus(status);
    t.setAmount(p.getAmount());
    t.setProvider(provider);
    t.setProviderReference(reference);
    t.setMessage(message == null ? null : message.length() > 1000 ? message.substring(0, 1000) : message);
    t.setRawPayload(raw == null ? null : raw.length() > 20000 ? raw.substring(0, 20000) : raw);
    transactions.save(t);
  }

  @Transactional(readOnly = true)
  public PaymentSummary current(Long orderId) { return mapper.payment(payments.findByOrderId(orderId).orElse(null)); }

  private static String slug(PaymentProvider p) {
    return switch (p) {
      case ORANGE_MONEY -> "orange-money";
      case MOOV_MONEY -> "moov-money";
      default -> "mock";
    };
  }

  // ---------- Back-office ----------

  @Transactional(readOnly = true)
  public PageResponse<PaymentRow> page(PaymentStatus status, PaymentMethod method, int page, int size) {
    Specification<Payment> spec = (root, q, cb) -> {
      List<Predicate> and = new ArrayList<>();
      if (status != null) and.add(cb.equal(root.get("status"), status));
      if (method != null) and.add(cb.equal(root.get("method"), method));
      return cb.and(and.toArray(Predicate[]::new));
    };
    var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Order.desc("createdAt")));
    return PageResponse.of(payments.findAll(spec, pageable).map(PaymentRow::of));
  }

  @Transactional(readOnly = true)
  public PaymentDetail detail(Long id) {
    var p = payments.findById(id).orElseThrow(() -> new NotFoundException("Paiement introuvable"));
    return new PaymentDetail(PaymentRow.of(p), p.getFailureReason(),
        transactions.findByPaymentIdOrderByCreatedAtAscIdAsc(p.getId()).stream().map(TransactionResponse::of).toList());
  }

  @Transactional(readOnly = true)
  public List<TransactionResponse> transactionsForOrder(Long orderId) {
    return payments.findByOrderId(orderId).map(p -> transactions.findByPaymentIdOrderByCreatedAtAscIdAsc(p.getId()).stream().map(TransactionResponse::of).toList())
        .orElse(List.of());
  }

  @Transactional(readOnly = true)
  public List<StatusStat> stats() {
    Map<PaymentStatus, StatusStat> byStatus = new EnumMap<>(PaymentStatus.class);
    for (Object[] row : payments.statsByStatus()) {
      var s = (PaymentStatus) row[0];
      byStatus.put(s, new StatusStat(s, ((Number) row[1]).longValue(), (java.math.BigDecimal) row[2]));
    }
    return Arrays.stream(PaymentStatus.values()).map(s -> byStatus.getOrDefault(s, new StatusStat(s, 0, java.math.BigDecimal.ZERO))).toList();
  }
}
