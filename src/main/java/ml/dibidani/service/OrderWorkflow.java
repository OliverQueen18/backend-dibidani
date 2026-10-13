package ml.dibidani.service;

import ml.dibidani.common.ConflictException;
import ml.dibidani.domain.*;
import ml.dibidani.repository.PaymentRepository;
import ml.dibidani.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

import static ml.dibidani.domain.OrderStatus.*;

/**
 * Machine à états des commandes. Chaque changement est historisé (ancien statut, nouveau statut, date, auteur, commentaire)
 * et déclenche ses effets : suivi de livraison, encaissement, remboursement. Pas de stock d'entrepôt.
 */
@Service
public class OrderWorkflow {
  public static final String SYSTEM = "Système";
  public static final String GATEWAY = "Passerelle de paiement";

  private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);
  static {
    TRANSITIONS.put(NEW, EnumSet.of(PENDING_PAYMENT, CONFIRMED, CANCELLED));
    TRANSITIONS.put(PENDING_PAYMENT, EnumSet.of(PAID, PAYMENT_FAILED, CONFIRMED, CANCELLED));
    TRANSITIONS.put(PAYMENT_FAILED, EnumSet.of(PENDING_PAYMENT, CANCELLED));
    TRANSITIONS.put(PAID, EnumSet.of(CONFIRMED, CANCELLED, REFUNDED));
    TRANSITIONS.put(CONFIRMED, EnumSet.of(PREPARING, CANCELLED));
    TRANSITIONS.put(PREPARING, EnumSet.of(READY_FOR_DELIVERY, CANCELLED));
    TRANSITIONS.put(READY_FOR_DELIVERY, EnumSet.of(OUT_FOR_DELIVERY, DELIVERED, CANCELLED));
    TRANSITIONS.put(OUT_FOR_DELIVERY, EnumSet.of(DELIVERED, RETURNED, READY_FOR_DELIVERY));
    TRANSITIONS.put(DELIVERED, EnumSet.of(RETURNED));
    TRANSITIONS.put(RETURNED, EnumSet.of(REFUNDED));
    TRANSITIONS.put(CANCELLED, EnumSet.of(REFUNDED));
    TRANSITIONS.put(REFUNDED, EnumSet.noneOf(OrderStatus.class));
  }

  private final StockService stock;
  private final DeliveryService delivery;
  private final PaymentRepository payments;
  private final PaymentTransactionRepository transactions;

  public OrderWorkflow(StockService stock, DeliveryService delivery, PaymentRepository payments, PaymentTransactionRepository transactions) {
    this.stock = stock;
    this.delivery = delivery;
    this.payments = payments;
    this.transactions = transactions;
  }

  /** Transitions proposées au back-office ; le remboursement exige un paiement encaissé. */
  public List<OrderStatus> allowed(CustomerOrder o) {
    return TRANSITIONS.getOrDefault(o.getStatus(), Set.of()).stream()
        .filter(s -> s != REFUNDED || o.getPaymentStatus() == PaymentStatus.SUCCESS)
        .filter(s -> s != DELIVERED || o.getStatus() != READY_FOR_DELIVERY || o.getDeliveryMethod() == DeliveryMethod.PICKUP)
        .toList();
  }

  public boolean canTransition(CustomerOrder o, OrderStatus to) { return allowed(o).contains(to); }

  /** Premier enregistrement de l'historique, à la création. */
  public void start(CustomerOrder o, AppUser actor, String comment) {
    o.setStatus(NEW);
    o.setStockReleased(true);
    o.getHistory().add(entry(o, null, NEW, actor, actor == null ? SYSTEM : actor.getFullName(), comment));
  }

  @Transactional
  public void transition(CustomerOrder o, OrderStatus to, AppUser actor, String actorName, String comment) {
    OrderStatus from = o.getStatus();
    if (from == to) throw new ConflictException("La commande est déjà « " + to.label() + " »");
    if (!canTransition(o, to)) throw new ConflictException("Passage impossible de « " + from.label() + " » à « " + to.label() + " »");
    if ((to == CANCELLED || to == RETURNED) && (comment == null || comment.isBlank()) && actor != null) {
      throw new IllegalArgumentException("Indiquez le motif de " + (to == CANCELLED ? "l'annulation" : "du retour"));
    }
    o.setStatus(to);
    String by = actor != null ? actor.getFullName() : actorName != null ? actorName : SYSTEM;
    o.getHistory().add(entry(o, from, to, actor, by, comment == null || comment.isBlank() ? null : comment.trim()));
    applyEffects(o, from, to, by);
  }

  private void applyEffects(CustomerOrder o, OrderStatus from, OrderStatus to, String by) {
    switch (to) {
      case CANCELLED, RETURNED -> releaseStock(o, to == CANCELLED ? StockMovementType.CANCELLATION : StockMovementType.RETURN, by);
      case REFUNDED -> refundPayment(o, by);
      case DELIVERED -> collectCashOnDelivery(o, by);
      default -> { }
    }
    if (to == CANCELLED) cancelPendingPayment(o);
    delivery.onOrderStatus(o, to);
  }

  /** Remise en stock unique, même si la commande passe ensuite par d'autres statuts. */
  private void releaseStock(CustomerOrder o, StockMovementType type, String by) {
    if (o.isStockReleased()) return;
    for (OrderItem i : o.getItems()) {
      stock.move(i.getProduct(), i.getVariant(), type, i.getQuantity(),
          type == StockMovementType.CANCELLATION ? "Annulation de commande" : "Retour de commande", o.getOrderNumber(), by);
    }
    o.setStockReleased(true);
  }

  private void cancelPendingPayment(CustomerOrder o) {
    payments.findByOrderId(o.getId()).filter(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.FAILED).ifPresent(p -> {
      p.setStatus(PaymentStatus.CANCELLED);
      o.setPaymentStatus(PaymentStatus.CANCELLED);
      record(p, TransactionType.MANUAL, PaymentStatus.CANCELLED, "Paiement annulé avec la commande");
    });
  }

  /** Le remboursement est constaté ici ; le reversement opérateur se fait depuis l'espace marchand tant qu'aucune API n'est branchée. */
  private void refundPayment(CustomerOrder o, String by) {
    var p = payments.findByOrderId(o.getId()).filter(x -> x.getStatus() == PaymentStatus.SUCCESS)
        .orElseThrow(() -> new ConflictException("Aucun paiement encaissé à rembourser"));
    p.setStatus(PaymentStatus.REFUNDED);
    o.setPaymentStatus(PaymentStatus.REFUNDED);
    record(p, TransactionType.REFUND, PaymentStatus.REFUNDED, "Remboursement enregistré par " + by);
  }

  /** Espèces à la livraison ou en magasin : la remise de la commande vaut encaissement. */
  private void collectCashOnDelivery(CustomerOrder o, String by) {
    PaymentMethod m = o.paymentMethodEnum();
    if (m != PaymentMethod.CASH_ON_DELIVERY && m != PaymentMethod.CASH_AT_STORE) return;
    payments.findByOrderId(o.getId()).filter(p -> p.getStatus() == PaymentStatus.PENDING).ifPresent(p -> {
      p.setStatus(PaymentStatus.SUCCESS);
      p.setPaidAt(Instant.now());
      o.setPaymentStatus(PaymentStatus.SUCCESS);
      record(p, TransactionType.MANUAL, PaymentStatus.SUCCESS, "Encaissé à la remise de la commande (" + by + ")");
    });
  }

  private void record(Payment p, TransactionType type, PaymentStatus status, String message) {
    var t = new PaymentTransaction();
    t.setPayment(p);
    t.setType(type);
    t.setStatus(status);
    t.setAmount(p.getAmount());
    t.setProvider(p.getProvider() == null ? PaymentProvider.MANUAL : p.getProvider());
    t.setProviderReference(p.getProviderReference());
    t.setMessage(message);
    transactions.save(t);
  }

  private static OrderStatusHistory entry(CustomerOrder o, OrderStatus from, OrderStatus to, AppUser actor, String by, String comment) {
    var h = new OrderStatusHistory();
    h.setOrder(o);
    h.setFromStatus(from);
    h.setToStatus(to);
    h.setChangedBy(actor);
    h.setChangedByName(by);
    h.setComment(comment);
    return h;
  }
}
