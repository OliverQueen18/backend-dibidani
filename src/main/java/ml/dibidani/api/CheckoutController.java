package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.CheckoutDtos.*;
import ml.dibidani.api.dto.OrderDtos.PlaceOrderResult;
import ml.dibidani.service.CheckoutService;
import ml.dibidani.service.OrderService;
import ml.dibidani.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
  private final CheckoutService checkout;
  private final PaymentService payments;
  private final OrderService orders;

  public CheckoutController(CheckoutService checkout, PaymentService payments, OrderService orders) {
    this.checkout = checkout;
    this.payments = payments;
    this.orders = orders;
  }

  @GetMapping
  public CheckoutView get(Authentication a) { return checkout.view(a.getName()); }

  @PutMapping("/customer")
  public CheckoutView customer(Authentication a, @Valid @RequestBody CustomerStep r) { return checkout.customer(a.getName(), r); }

  @PutMapping("/address")
  public CheckoutView address(Authentication a, @Valid @RequestBody AddressStep r) { return checkout.address(a.getName(), r); }

  @PutMapping("/delivery")
  public CheckoutView delivery(Authentication a, @Valid @RequestBody DeliveryStep r) { return checkout.delivery(a.getName(), r); }

  @PutMapping("/payment")
  public CheckoutView payment(Authentication a, @Valid @RequestBody PaymentStep r) { return checkout.payment(a.getName(), r); }

  /** Crée la commande (transaction), puis lance le paiement en ligne s'il y a lieu (hors transaction). */
  @PostMapping("/confirm")
  @ResponseStatus(HttpStatus.CREATED)
  public PlaceOrderResult confirm(Authentication a, @Valid @RequestBody ConfirmRequest r) {
    var order = checkout.confirm(a.getName());
    return OrderController.afterPlacement(order.getOrderNumber(), order.getId(), a.getName(), payments, orders);
  }
}
