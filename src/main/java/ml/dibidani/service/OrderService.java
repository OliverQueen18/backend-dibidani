package ml.dibidani.service;

import ml.dibidani.api.dto.CartDtos.LineStatus;
import ml.dibidani.api.dto.OrderDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.common.Tokens;
import ml.dibidani.domain.*;
import ml.dibidani.repository.CustomerOrderRepository;
import ml.dibidani.service.PricingService.Line;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OrderService {
  private static final ZoneId BAMAKO = ZoneId.of("Africa/Bamako");

  private final CustomerOrderRepository orders;
  private final PricingService pricing;
  private final PromoCodeService promos;
  private final DeliveryService delivery;
  private final PaymentService payments;
  private final OrderWorkflow workflow;
  private final OrderMapper mapper;
  private final UserService users;

  public OrderService(CustomerOrderRepository orders, PricingService pricing, PromoCodeService promos,
                      DeliveryService delivery, PaymentService payments, OrderWorkflow workflow, OrderMapper mapper, UserService users) {
    this.orders = orders;
    this.pricing = pricing;
    this.promos = promos;
    this.delivery = delivery;
    this.payments = payments;
    this.workflow = workflow;
    this.mapper = mapper;
    this.users = users;
  }

  /** Données de livraison et de paiement déjà validées par l'appelant (checkout ou ancienne API). */
  public record PlaceCommand(AppUser customer, List<Line> lines, String promoCode,
                             String customerName, String customerEmail, String customerPhone,
                             DeliveryMethod deliveryMethod, DeliveryZone zone, String address, String district, String landmark,
                             PaymentMethod paymentMethod, PaymentProvider paymentProvider, String payerPhone, boolean flatFeeFallback) {}

  /**
   * Crée la commande à partir des seules données serveur : prix relus, promo consommée sous verrou,
   * frais de livraison recalculés. Le stock n'est pas débité : Dibidani confirme après les vendeurs.
   */
  @Transactional
  public CustomerOrder place(PlaceCommand c) {
    if (c.lines().isEmpty()) throw new IllegalArgumentException("Votre panier est vide");

    var priced = pricing.price(c.lines(), null, c.zone() == null ? null : c.zone().getId(), c.deliveryMethod());
    for (var pl : priced.lines()) {
      if (pl.view().status() != LineStatus.AVAILABLE) throw new ConflictException(pl.view().message() + " Vérifiez votre panier.");
    }
    BigDecimal subtotal = priced.view().subtotal();

    BigDecimal discount = BigDecimal.ZERO;
    PromoCode promo = null;
    if (c.promoCode() != null && !c.promoCode().isBlank()) {
      var eval = promos.consume(c.promoCode(), subtotal);
      promo = eval.promo();
      discount = eval.discount();
    }
    BigDecimal net = subtotal.subtract(discount).max(BigDecimal.ZERO);
    var quote = delivery.quote(c.zone() == null ? null : c.zone().getId(), c.deliveryMethod(), net, promo != null && promo.getType() == PromoType.FREE_DELIVERY);
    if (quote.fee() == null && c.flatFeeFallback() && c.deliveryMethod() == DeliveryMethod.STANDARD) quote = delivery.flatQuote(net, promo != null && promo.getType() == PromoType.FREE_DELIVERY);
    if (quote.fee() == null) throw new IllegalArgumentException("Choisissez votre quartier de livraison");

    var o = new CustomerOrder();
    o.setCustomer(c.customer());
    o.setOrderNumber(newOrderNumber());
    o.setTrackingToken(newTrackingToken());
    o.setTrackingCode(o.getOrderNumber());
    o.setCustomerName(c.customerName());
    o.setCustomerEmail(c.customerEmail());
    o.setCustomerPhone(c.customerPhone());
    o.setDeliveryMethod(c.deliveryMethod());
    if (c.deliveryMethod() == DeliveryMethod.PICKUP) {
      o.setDeliveryAddress(quote.zoneName());
      o.setDeliveryCity(c.zone() == null ? null : c.zone().getCity());
    } else {
      o.setDeliveryZone(c.zone());
      o.setDeliveryZoneName(c.zone() == null ? null : c.zone().getName());
      o.setDeliveryAddress(c.address());
      o.setDeliveryDistrict(c.zone() != null ? c.zone().getDistrict() : c.district());
      o.setDeliveryCommune(c.zone() == null ? null : Slugs.blankToNull(c.zone().getCommune()));
      o.setDeliveryCity(c.zone() == null ? "Bamako" : c.zone().getCity());
      o.setDeliveryLandmark(c.landmark());
    }
    o.setEstimatedDelivery(quote.estimatedTime());
    o.setPaymentMethod(c.paymentMethod().name());
    o.setPaymentProvider(c.paymentProvider() == null ? null : c.paymentProvider().name());
    o.setPaymentStatus(PaymentStatus.PENDING);
    o.setPromoCode(promo == null ? null : promo.getCode());

    for (var pl : priced.lines()) {
      var v = pl.view();
      Product p = pl.product();
      var item = new OrderItem();
      item.setOrder(o);
      item.setProduct(p);
      item.setVariant(pl.variant());
      item.setProductName(p.getName());
      item.setProductSku(v.sku());
      item.setVariantName(v.variantName());
      item.setVendorId(p.getVendor() == null ? null : p.getVendor().getId());
      item.setVendorName(p.getVendor() == null ? null : p.getVendor().getName());
      item.setImageUrl(v.imageUrl());
      item.setQuantity(v.quantity());
      item.setUnitPrice(v.unitPrice());
      item.setLineTotal(v.lineTotal());
      o.getItems().add(item);
    }
    o.setSubtotal(subtotal);
    o.setDiscountTotal(discount);
    o.setDeliveryFee(quote.fee());
    o.setTotal(net.add(quote.fee()));

    workflow.start(o, null, "Commande reçue. Vérifier la disponibilité auprès des vendeurs avant de confirmer au client.");
    orders.save(o);
    delivery.createFor(o);
    payments.create(o, c.paymentMethod(), c.paymentProvider(), c.payerPhone());
    if (c.paymentMethod().isOnline() || c.paymentMethod() == PaymentMethod.BANK_TRANSFER) {
      workflow.transition(o, OrderStatus.PENDING_PAYMENT, null, OrderWorkflow.SYSTEM, c.paymentMethod().isOnline() ? "En attente du paiement en ligne" : "En attente du virement");
    }
    return o;
  }

  /** Ancienne API POST /orders : conservée, recalculée côté serveur comme le checkout. */
  @Transactional
  public OrderResponse create(String email, CreateOrderRequest r) {
    AppUser user = users.byEmail(email);
    PaymentMethod method = legacyMethod(r.paymentMethod());
    PaymentProvider provider = legacyProvider(r.paymentMethod());
    DeliveryMethod dm = r.deliveryMethod() == null ? DeliveryMethod.STANDARD : r.deliveryMethod();
    DeliveryZone zone = r.deliveryZoneId() != null ? delivery.activeZone(r.deliveryZoneId()) : delivery.findActiveByDistrict(r.deliveryDistrict());
    if (zone == null && dm == DeliveryMethod.EXPRESS) throw new IllegalArgumentException("Choisissez votre quartier pour la livraison express");
    provider = payments.validateChoice(method, provider, dm);
    var lines = r.items().stream().map(i -> new Line(null, i.productId(), i.variantId(), i.quantity())).toList();
    var o = place(new PlaceCommand(user, lines, r.promoCode(), user.getFullName(), user.getEmail(), r.customerPhone().trim(),
        dm, zone, r.deliveryAddress().trim(), Slugs.blankToNull(r.deliveryDistrict()), null, method, provider, r.customerPhone().trim(), true));
    return mapper.customer(o);
  }

  @Transactional
  public OrderResponse prepareRetry(String email, String number, PaymentRequest r) {
    var o = owned(email, number);
    PaymentMethod method = r.method() == null || r.method().isBlank() ? null : legacyMethod(r.method());
    PaymentProvider provider = r.provider() != null ? r.provider() : r.method() == null ? null : legacyProvider(r.method());
    payments.prepareRetry(o, method, provider, r.phone());
    return mapper.customer(o);
  }

  public Long idOf(String email, String number) { return owned(email, number).getId(); }

  /** Annulation par le client tant que la commande n'est pas confirmée. */
  @Transactional
  public OrderResponse cancelByCustomer(String email, String number, String reason) {
    var o = owned(email, number);
    if (o.getStatus() != OrderStatus.NEW && o.getStatus() != OrderStatus.PENDING_PAYMENT && o.getStatus() != OrderStatus.PAYMENT_FAILED) {
      throw new ConflictException("Cette commande est déjà en cours de traitement : contactez le service client");
    }
    workflow.transition(o, OrderStatus.CANCELLED, null, o.getCustomerName() + " (client)", reason);
    return mapper.customer(o);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> mine(String email) {
    var u = users.byEmail(email);
    return orders.findByCustomerIdOrderByCreatedAtDesc(u.getId()).stream().map(mapper::customer).toList();
  }

  @Transactional(readOnly = true)
  public List<OrderSummary> mineSummaries(String email, int limit) {
    var u = users.byEmail(email);
    return orders.findByCustomerIdOrderByCreatedAtDesc(u.getId(), PageRequest.of(0, Math.max(1, Math.min(limit, 100)))).stream().map(mapper::summary).toList();
  }

  @Transactional(readOnly = true)
  public OrderResponse get(String email, String number) { return mapper.customer(owned(email, number)); }

  @Transactional(readOnly = true)
  public CustomerOrder ownedOrder(String email, String number) { return owned(email, number); }

  @Transactional(readOnly = true)
  public TrackingResponse tracking(String token) {
    if (token == null || token.length() < 20 || token.length() > 64) throw new NotFoundException("Commande introuvable");
    return mapper.tracking(orders.findByTrackingToken(token).orElseThrow(() -> new NotFoundException("Commande introuvable")));
  }

  public boolean trackingExists(String token) { return token != null && token.length() <= 64 && orders.existsByTrackingToken(token); }

  /** Une commande d'un autre client répond 404 pour ne pas révéler son existence. */
  private CustomerOrder owned(String email, String number) {
    var o = orders.findByOrderNumber(number).orElseThrow(() -> new NotFoundException("Commande introuvable"));
    if (!o.getCustomer().getEmail().equalsIgnoreCase(email)) throw new NotFoundException("Commande introuvable");
    return o;
  }

  /** CMD-AAAAMMJJ-XXXXXX (date de Bamako, suffixe aléatoire lisible). */
  private String newOrderNumber() {
    String day = LocalDate.now(BAMAKO).format(DateTimeFormatter.BASIC_ISO_DATE);
    String n;
    do { n = "CMD-" + day + "-" + Tokens.readable(6); } while (orders.existsByOrderNumber(n));
    return n;
  }

  private String newTrackingToken() {
    String t;
    do { t = Tokens.secure(); } while (orders.existsByTrackingToken(t));
    return t;
  }

  static PaymentMethod legacyMethod(String raw) {
    String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    try { return PaymentMethod.valueOf(v); } catch (IllegalArgumentException ignored) { }
    if (v.contains("ORANGE") || v.contains("MOOV") || v.contains("MOBILE")) return PaymentMethod.MOBILE_MONEY;
    if (v.contains("CARD") || v.contains("CARTE")) return PaymentMethod.BANK_CARD;
    if (v.contains("TRANSFER") || v.contains("VIREMENT")) return PaymentMethod.BANK_TRANSFER;
    if (v.contains("STORE") || v.contains("MAGASIN")) return PaymentMethod.CASH_AT_STORE;
    if (v.equals("COD") || v.contains("CASH") || v.contains("LIVRAISON")) return PaymentMethod.CASH_ON_DELIVERY;
    throw new IllegalArgumentException("Mode de paiement inconnu : " + raw);
  }

  static PaymentProvider legacyProvider(String raw) {
    String v = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
    if (v.contains("ORANGE")) return PaymentProvider.ORANGE_MONEY;
    if (v.contains("MOOV")) return PaymentProvider.MOOV_MONEY;
    return null;
  }
}
