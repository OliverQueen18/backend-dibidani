package ml.dibidani.service;

import ml.dibidani.api.dto.DeliveryDtos.DeliveryResponse;
import ml.dibidani.api.dto.OrderDtos.*;
import ml.dibidani.config.AppProperties;
import ml.dibidani.domain.*;
import ml.dibidani.repository.DeliveryRepository;
import ml.dibidani.repository.PaymentRepository;
import org.springframework.stereotype.Component;
import java.util.*;

import static ml.dibidani.domain.OrderStatus.*;

@Component
public class OrderMapper {
  private static final List<OrderStatus> TIMELINE = List.of(NEW, CONFIRMED, PREPARING, READY_FOR_DELIVERY, OUT_FOR_DELIVERY, DELIVERED);

  private final PaymentRepository payments;
  private final DeliveryRepository deliveries;
  private final OrderWorkflow workflow;
  private final AppProperties app;

  public OrderMapper(PaymentRepository payments, DeliveryRepository deliveries, OrderWorkflow workflow, AppProperties app) {
    this.payments = payments;
    this.deliveries = deliveries;
    this.workflow = workflow;
    this.app = app;
  }

  public OrderResponse customer(CustomerOrder o) { return build(o, false); }
  public OrderResponse admin(CustomerOrder o) { return build(o, true); }

  public PaymentSummary payment(Payment p) {
    if (p == null) return null;
    return new PaymentSummary(p.getId(), p.getMethod(), p.getMethod().label(), p.getProvider(), p.getStatus(), p.getAmount(), p.getCurrency(),
        p.getProviderReference(), p.getStatus() == PaymentStatus.PENDING ? p.getCheckoutUrl() : null, p.getPaidAt(), p.getFailureReason());
  }

  public OrderSummary summary(CustomerOrder o) {
    return new OrderSummary(o.getId(), o.getOrderNumber(), o.getStatus(), o.getStatus().label(), o.getPaymentStatus(), o.getPaymentMethod(),
        methodLabel(o), o.getDeliveryMethod(), o.getTotal(), o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
        o.getCustomerName(), o.getCustomerPhone(), zone(o), o.getCreatedAt());
  }

  public String qrCodeUrl(CustomerOrder o) { return "/api/v1/public/tracking/" + o.getTrackingToken() + "/qr.png"; }

  private OrderResponse build(CustomerOrder o, boolean admin) {
    var payment = payments.findByOrderId(o.getId()).orElse(null);
    var delivery = deliveries.findByOrderId(o.getId()).orElse(null);
    List<HistoryEntry> history = o.getHistory().stream()
        .map(h -> new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getToStatus().label(), admin ? h.getChangedByName() : null,
            admin ? h.getComment() : null, h.getCreatedAt()))
        .toList();
    List<StatusOption> transitions = admin ? workflow.allowed(o).stream().map(s -> new StatusOption(s, s.label())).toList() : List.of();
    boolean cancellable = o.getStatus() == NEW || o.getStatus() == PENDING_PAYMENT || o.getStatus() == PAYMENT_FAILED;
    var customer = o.getCustomer();
    return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getStatus().label(), o.getPaymentStatus(),
        o.getSubtotal(), o.getDiscountTotal(), o.getDeliveryFee(), o.getTotal(), o.getPromoCode(),
        admin ? customer.getId() : null, o.getCustomerName() != null ? o.getCustomerName() : customer.getFullName(),
        o.getCustomerEmail() != null ? o.getCustomerEmail() : customer.getEmail(), o.getCustomerPhone(),
        o.getDeliveryMethod(), o.getDeliveryMethod().label(), o.getDeliveryZone() == null ? null : o.getDeliveryZone().getId(), o.getDeliveryZoneName(),
        o.getDeliveryAddress(), o.getDeliveryDistrict(), o.getDeliveryCommune(), o.getDeliveryCity(), o.getDeliveryLandmark(), o.getEstimatedDelivery(),
        o.getPaymentMethod(), methodLabel(o), o.getPaymentProvider(),
        o.getTrackingCode(), o.getTrackingToken(), app.trackingUrl(o.getTrackingToken()), qrCodeUrl(o),
        o.getItems().stream().map(OrderItemResponse::of).toList(), o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(), history,
        payment(payment), admin ? DeliveryResponse.of(delivery) : publicDelivery(delivery), transitions, admin ? o.getAdminNote() : null,
        cancellable, o.getCreatedAt(), o.getUpdatedAt());
  }

  /** Le client voit le livreur assigné mais pas les notes internes. */
  private static DeliveryResponse publicDelivery(Delivery d) {
    if (d == null) return null;
    return new DeliveryResponse(d.getId(), d.getMethod(), d.getStatus(), null, d.getCourierName(), d.getCourierPhone(),
        d.getAssignedAt(), d.getPickedUpAt(), d.getDeliveredAt(), null);
  }

  /** Vue publique limitée : ni nom, ni téléphone, ni adresse exacte, ni montants. */
  public TrackingResponse tracking(CustomerOrder o) {
    Map<OrderStatus, java.time.Instant> reached = new EnumMap<>(OrderStatus.class);
    o.getHistory().forEach(h -> reached.putIfAbsent(h.getToStatus(), h.getCreatedAt()));
    int currentIndex = TIMELINE.indexOf(normalizedForTimeline(o.getStatus()));
    List<TrackingStep> steps = new ArrayList<>();
    for (int i = 0; i < TIMELINE.size(); i++) {
      OrderStatus s = TIMELINE.get(i);
      if (s == OUT_FOR_DELIVERY && o.getDeliveryMethod() == DeliveryMethod.PICKUP) continue;
      boolean done = currentIndex >= 0 && i <= currentIndex;
      steps.add(new TrackingStep(s, stepLabel(s, o.getDeliveryMethod()), done, i == currentIndex, reached.get(s)));
    }
    var items = o.getItems().stream().map(i -> new TrackingItem(i.getProductName(), i.getVariantName(), i.getQuantity(), i.getImageUrl())).toList();
    return new TrackingResponse(o.getOrderNumber(), o.getStatus(), o.getStatus().label(), !o.getStatus().isOpen(), steps,
        o.getDeliveryMethod(), o.getDeliveryMethod().label(), o.getDeliveryMethod() == DeliveryMethod.PICKUP ? null : o.getDeliveryDistrict(),
        o.getEstimatedDelivery(), methodLabel(o), o.getPaymentStatus() == PaymentStatus.SUCCESS, items,
        o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(), o.getCreatedAt(), o.getUpdatedAt());
  }

  /** Les statuts de paiement se rangent sur l'étape « Commande reçue » de la frise. */
  private static OrderStatus normalizedForTimeline(OrderStatus s) {
    return switch (s) {
      case PENDING_PAYMENT, PAYMENT_FAILED -> NEW;
      case PAID -> NEW;
      default -> s;
    };
  }

  private static String stepLabel(OrderStatus s, DeliveryMethod m) {
    return switch (s) {
      case NEW -> "Commande reçue";
      case CONFIRMED -> "Confirmée (vendeurs)";
      case READY_FOR_DELIVERY -> m == DeliveryMethod.PICKUP ? "Prête à retirer" : "Prête pour la livraison";
      case DELIVERED -> m == DeliveryMethod.PICKUP ? "Retirée" : "Livrée";
      default -> s.label();
    };
  }

  static String methodLabel(CustomerOrder o) {
    PaymentMethod m = o.paymentMethodEnum();
    return m != null ? m.label() : o.getPaymentMethod();
  }

  static String zone(CustomerOrder o) {
    if (o.getDeliveryMethod() == DeliveryMethod.PICKUP) return DeliveryMethod.PICKUP.label();
    return o.getDeliveryZoneName() != null ? o.getDeliveryZoneName() : o.getDeliveryDistrict();
  }
}
