package ml.dibidani.service;

import ml.dibidani.config.AppProperties;
import ml.dibidani.domain.OrderStatus;
import ml.dibidani.domain.PaymentStatus;
import ml.dibidani.repository.CustomerOrderRepository;
import ml.dibidani.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/** Annule les commandes dont le paiement n'est jamais arrivé. */
@Component
public class PaymentExpiryJob {
  private static final Logger log = LoggerFactory.getLogger(PaymentExpiryJob.class);
  private static final EnumSet<OrderStatus> AWAITING = EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED);

  private final CustomerOrderRepository orders;
  private final PaymentRepository payments;
  private final OrderWorkflow workflow;
  private final TransactionTemplate tx;
  private final int timeoutHours;

  public PaymentExpiryJob(CustomerOrderRepository orders, PaymentRepository payments, OrderWorkflow workflow,
                          PlatformTransactionManager txManager, AppProperties app) {
    this.orders = orders;
    this.payments = payments;
    this.workflow = workflow;
    this.tx = new TransactionTemplate(txManager);
    this.timeoutHours = app.orders() == null ? 48 : app.orders().paymentTimeoutHours();
  }

  @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
  public void expire() {
    if (timeoutHours <= 0) return;
    Instant before = Instant.now().minus(Duration.ofHours(timeoutHours));
    var ids = orders.findIdsByStatusInAndCreatedAtBefore(AWAITING, before, PageRequest.of(0, 200));
    for (Long id : ids) {
      try {
        tx.executeWithoutResult(s -> {
          var payment = payments.lockByOrderId(id).orElse(null);
          if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) return;
          var o = orders.findById(id).orElse(null);
          if (o == null || !AWAITING.contains(o.getStatus())) return;
          workflow.transition(o, OrderStatus.CANCELLED, null, OrderWorkflow.SYSTEM,
              "Annulation automatique : paiement non reçu sous " + timeoutHours + " h");
        });
      } catch (RuntimeException e) {
        log.warn("Expiration de la commande {} impossible : {}", id, e.getMessage());
      }
    }
    if (!ids.isEmpty()) log.info("{} commande(s) en attente de paiement examinée(s)", ids.size());
  }
}
