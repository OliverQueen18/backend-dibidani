package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.OrderDtos.*;
import ml.dibidani.domain.PaymentStatus;
import ml.dibidani.service.InvoiceService;
import ml.dibidani.service.OrderService;
import ml.dibidani.service.PaymentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
  private final OrderService orders;
  private final PaymentService payments;
  private final InvoiceService invoices;

  public OrderController(OrderService orders, PaymentService payments, InvoiceService invoices) {
    this.orders = orders;
    this.payments = payments;
    this.invoices = invoices;
  }

  /** Ancienne API conservée : les montants sont recalculés côté serveur. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse create(Authentication a, @Valid @RequestBody CreateOrderRequest r) { return orders.create(a.getName(), r); }

  /** Lance (ou relance après échec) le paiement en ligne d'une commande. */
  @PostMapping("/{number}/pay")
  public PlaceOrderResult pay(Authentication a, @PathVariable String number, @Valid @RequestBody(required = false) PaymentRequest r) {
    var order = orders.prepareRetry(a.getName(), number, r == null ? new PaymentRequest(null, null, null) : r);
    return afterPlacement(order.orderNumber(), order.id(), a.getName(), payments, orders);
  }

  @PostMapping("/{number}/cancel")
  public OrderResponse cancel(Authentication a, @PathVariable String number, @Valid @RequestBody CancelRequest r) {
    return orders.cancelByCustomer(a.getName(), number, r.reason());
  }

  @GetMapping
  public List<OrderResponse> mine(Authentication a) { return orders.mine(a.getName()); }

  @GetMapping("/summaries")
  public List<OrderSummary> summaries(Authentication a, @RequestParam(defaultValue = "50") int limit) { return orders.mineSummaries(a.getName(), limit); }

  @GetMapping("/{number}")
  public OrderResponse get(Authentication a, @PathVariable String number) { return orders.get(a.getName(), number); }

  @GetMapping(value = "/{number}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> invoice(Authentication a, @PathVariable String number) {
    return pdf(number, invoices.pdf(orders.idOf(a.getName(), number)));
  }

  static ResponseEntity<byte[]> pdf(String number, byte[] body) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("facture-" + number + ".pdf").build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .contentType(MediaType.APPLICATION_PDF).body(body);
  }

  /** Après création : démarre le paiement en ligne et indique au client l'action suivante. */
  static PlaceOrderResult afterPlacement(String number, Long orderId, String email, PaymentService payments, OrderService orders) {
    var payment = payments.start(orderId);
    var order = orders.get(email, number);
    String next;
    String message;
    if (payment != null && payment.status() == PaymentStatus.PENDING && payment.checkoutUrl() != null) {
      next = "REDIRECT";
      message = "Vous allez être redirigé vers la page de paiement.";
    } else if (payment != null && payment.method().isOnline() && payment.status() == PaymentStatus.PENDING) {
      next = "AWAIT_CONFIRMATION";
      message = "Validez le paiement sur votre téléphone. La commande sera mise à jour automatiquement.";
    } else if (payment != null && payment.status() == PaymentStatus.FAILED) {
      next = "RETRY_PAYMENT";
      message = payment.failureReason() != null ? payment.failureReason() : "Le paiement a échoué. Vous pouvez réessayer.";
    } else {
      next = "CONFIRMED";
      message = payment != null && payment.status() == PaymentStatus.SUCCESS ? "Paiement reçu, merci !" : "Commande enregistrée, merci !";
    }
    return new PlaceOrderResult(order, payment, next, message);
  }
}
