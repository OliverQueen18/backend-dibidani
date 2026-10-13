package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.DeliveryDtos.AssignRequest;
import ml.dibidani.api.dto.DeliveryDtos.DeliveryResponse;
import ml.dibidani.api.dto.DeliveryDtos.DeliveryRow;
import ml.dibidani.api.dto.OrderDtos.*;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.PaymentDtos.*;
import ml.dibidani.domain.*;
import ml.dibidani.service.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Traitement des commandes, livraisons et paiements dans le back-office. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOrderController {
  private final AdminOrderService orders;
  private final DeliveryService delivery;
  private final PaymentService payments;
  private final InvoiceService invoices;
  private final UserService users;

  public AdminOrderController(AdminOrderService orders, DeliveryService delivery, PaymentService payments, InvoiceService invoices, UserService users) {
    this.orders = orders;
    this.delivery = delivery;
    this.payments = payments;
    this.invoices = invoices;
    this.users = users;
  }

  @GetMapping("/orders")
  @PreAuthorize("hasAuthority('PERM_ORDERS_MANAGE')")
  public PageResponse<OrderSummary> list(@RequestParam(required = false) OrderStatus status, @RequestParam(required = false) String group,
                                         @RequestParam(required = false) PaymentStatus paymentStatus, @RequestParam(required = false) String paymentMethod,
                                         @RequestParam(required = false) DeliveryMethod deliveryMethod, @RequestParam(required = false) Long customerId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    var filter = new AdminOrderService.Filter(status, group, paymentStatus, paymentMethod, deliveryMethod, customerId, q, from, to);
    return orders.page(filter, page, size);
  }

  @GetMapping("/orders/counts")
  @PreAuthorize("hasAuthority('PERM_ORDERS_MANAGE')")
  public Map<String, Long> counts() { return orders.counts(); }

  @GetMapping("/orders/statuses")
  @PreAuthorize("hasAuthority('PERM_ORDERS_MANAGE')")
  public List<StatusOption> statuses() { return orders.statuses(); }

  @GetMapping("/orders/{id}")
  @PreAuthorize("hasAuthority('PERM_ORDERS_MANAGE')")
  public OrderResponse detail(@PathVariable Long id) { return orders.detail(id); }

  @PostMapping("/orders/{id}/status")
  @PreAuthorize("hasAuthority('PERM_ORDERS_MANAGE')")
  public OrderResponse changeStatus(Authentication a, @PathVariable Long id, @Valid @RequestBody StatusChangeRequest r) {
    return orders.changeStatus(id, r, users.byEmail(a.getName()));
  }

  /** Ancienne API conservée : passe désormais par le workflow (transitions contrôlées et historisées). */
  @PatchMapping("/orders/{id}/status")
  @PreAuthorize("hasAuthority('PERM_ORDERS_MANAGE')")
  public OrderResponse legacyStatus(Authentication a, @PathVariable Long id, @RequestParam OrderStatus status) {
    return orders.changeStatus(id, new StatusChangeRequest(status, null), users.byEmail(a.getName()));
  }

  @PutMapping("/orders/{id}/note")
  @PreAuthorize("hasAuthority('PERM_ORDERS_MANAGE')")
  public OrderResponse note(@PathVariable Long id, @Valid @RequestBody NoteRequest r) { return orders.note(id, r); }

  @PreAuthorize("hasAuthority('PERM_DELIVERIES_MANAGE')")
  @PostMapping("/orders/{id}/delivery")
  public DeliveryResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest r) { return delivery.assign(id, r); }

  @PreAuthorize("hasAuthority('PERM_PAYMENTS_MANAGE')")
  @PostMapping("/orders/{id}/mark-paid")
  public OrderResponse markPaid(Authentication a, @PathVariable Long id, @Valid @RequestBody MarkPaidRequest r) {
    payments.markPaid(id, r, users.byEmail(a.getName()));
    return orders.detail(id);
  }

  @GetMapping("/orders/{id}/transactions")
  @PreAuthorize("hasAuthority('PERM_PAYMENTS_MANAGE')")
  public List<TransactionResponse> transactions(@PathVariable Long id) { return payments.transactionsForOrder(id); }

  @GetMapping(value = "/orders/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("hasAuthority('PERM_ORDERS_MANAGE')")
  public ResponseEntity<byte[]> invoice(@PathVariable Long id) {
    var o = orders.detail(id);
    return OrderController.pdf(o.orderNumber(), invoices.pdf(id));
  }

  // ---------- Livraisons ----------

  @GetMapping("/deliveries")
  @PreAuthorize("hasAuthority('PERM_DELIVERIES_MANAGE')")
  public PageResponse<DeliveryRow> deliveries(@RequestParam(required = false) DeliveryStatus status,
                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    var pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"));
    return PageResponse.of(delivery.page(status, pageable));
  }

  // ---------- Paiements ----------

  @GetMapping("/payments")
  @PreAuthorize("hasAuthority('PERM_PAYMENTS_MANAGE')")
  public PageResponse<PaymentRow> payments(@RequestParam(required = false) PaymentStatus status, @RequestParam(required = false) PaymentMethod method,
                                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return payments.page(status, method, page, size);
  }

  @GetMapping("/payments/stats")
  @PreAuthorize("hasAuthority('PERM_PAYMENTS_MANAGE')")
  public List<StatusStat> paymentStats() { return payments.stats(); }

  @GetMapping("/payments/{id}")
  @PreAuthorize("hasAuthority('PERM_PAYMENTS_MANAGE')")
  public PaymentDetail payment(@PathVariable Long id) { return payments.detail(id); }
}
