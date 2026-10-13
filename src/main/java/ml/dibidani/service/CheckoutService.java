package ml.dibidani.service;

import ml.dibidani.api.dto.AccountDtos.AddressResponse;
import ml.dibidani.api.dto.CheckoutDtos.*;
import ml.dibidani.api.dto.DeliveryDtos.ZoneResponse;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.*;
import ml.dibidani.repository.CheckoutSessionRepository;
import ml.dibidani.repository.CustomerAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Checkout en étapes sauvegardé côté serveur. Les montants affichés et facturés sont toujours recalculés. */
@Service
@Transactional
public class CheckoutService {
  private final CheckoutSessionRepository sessions;
  private final CustomerAddressRepository addresses;
  private final CartService carts;
  private final DeliveryService delivery;
  private final PaymentService payments;
  private final OrderService orders;
  private final UserService users;

  public CheckoutService(CheckoutSessionRepository sessions, CustomerAddressRepository addresses, CartService carts, DeliveryService delivery,
                         PaymentService payments, OrderService orders, UserService users) {
    this.sessions = sessions;
    this.addresses = addresses;
    this.carts = carts;
    this.delivery = delivery;
    this.payments = payments;
    this.orders = orders;
    this.users = users;
  }

  public CheckoutView view(String email) {
    AppUser user = users.byEmail(email);
    CheckoutSession s = session(user);
    if (s.getDeliveryZone() != null && !s.getDeliveryZone().isActive()) {
      s.setDeliveryZone(null);
      s.setStep(Math.min(s.getStep(), 2));
    }
    var cart = carts.view(email, s.getDeliveryZone() == null ? null : s.getDeliveryZone().getId(), s.getDeliveryMethod());
    BigDecimal net = cart.subtotal().subtract(cart.discount());
    boolean freeByPromo = cart.promo() != null && cart.promo().applied() && cart.promo().type() == PromoType.FREE_DELIVERY;
    var options = delivery.options(s.getDeliveryZone() == null ? null : s.getDeliveryZone().getId(), net, freeByPromo);

    List<String> blockers = new ArrayList<>();
    if (cart.items().isEmpty()) blockers.add("Votre panier est vide.");
    else if (!cart.canCheckout()) blockers.add("Certains articles de votre panier ne sont plus disponibles.");
    if (!customerComplete(s)) blockers.add("Renseignez vos informations.");
    if (s.getDeliveryMethod() != DeliveryMethod.PICKUP && !addressComplete(s)) blockers.add("Renseignez votre adresse de livraison.");
    if (s.getDeliveryMethod() == null) blockers.add("Choisissez un mode de livraison.");
    if (s.getPaymentMethod() == null) blockers.add("Choisissez un mode de paiement.");

    var saved = addresses.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(user.getId()).stream().map(AddressResponse::of).toList();
    return new CheckoutView(sessionView(s), cart, options, payments.options(), saved, blockers.isEmpty(), blockers);
  }

  public CheckoutView customer(String email, CustomerStep r) {
    CheckoutSession s = session(users.byEmail(email));
    s.setFullName(r.fullName().trim());
    s.setPhone(r.phone().trim());
    s.setEmail(Slugs.blankToNull(r.email()));
    s.setStep(Math.max(s.getStep(), 2));
    return view(email);
  }

  public CheckoutView address(String email, AddressStep r) {
    AppUser user = users.byEmail(email);
    CheckoutSession s = session(user);
    if (r.addressId() != null) {
      var a = addresses.findByIdAndUserId(r.addressId(), user.getId()).orElseThrow(() -> new NotFoundException("Adresse introuvable"));
      if (a.getDeliveryZone() == null || !a.getDeliveryZone().isActive()) throw new IllegalArgumentException("Le quartier de cette adresse n'est plus desservi : modifiez-la");
      s.setAddressId(a.getId());
      s.setAddressLine(a.getAddressLine());
      s.setDeliveryZone(a.getDeliveryZone());
      s.setLandmark(a.getLandmark());
    } else {
      if (r.addressLine() == null || r.addressLine().isBlank()) throw new IllegalArgumentException("L'adresse est obligatoire");
      if (r.deliveryZoneId() == null) throw new IllegalArgumentException("Choisissez votre quartier");
      DeliveryZone zone = delivery.activeZone(r.deliveryZoneId());
      s.setAddressId(null);
      s.setAddressLine(r.addressLine().trim());
      s.setDeliveryZone(zone);
      s.setLandmark(Slugs.blankToNull(r.landmark()));
      if (Boolean.TRUE.equals(r.saveAddress())) {
        var a = new CustomerAddress();
        a.setUser(user);
        a.setLabel(Slugs.blankToNull(r.label()) == null ? "Adresse de livraison" : r.label().trim());
        a.setFullName(s.getFullName() != null ? s.getFullName() : user.getFullName());
        a.setPhone(s.getPhone() != null ? s.getPhone() : user.getPhone() == null ? "" : user.getPhone());
        a.setAddressLine(s.getAddressLine());
        a.setDeliveryZone(zone);
        a.setLandmark(s.getLandmark());
        a.setDefaultAddress(addresses.countByUserId(user.getId()) == 0);
        s.setAddressId(addresses.save(a).getId());
      }
    }
    if (s.getDeliveryMethod() == DeliveryMethod.EXPRESS && s.getDeliveryZone().getExpressPrice() == null) s.setDeliveryMethod(null);
    s.setStep(Math.max(s.getStep(), 3));
    return view(email);
  }

  public CheckoutView delivery(String email, DeliveryStep r) {
    CheckoutSession s = session(users.byEmail(email));
    if (r.method() != DeliveryMethod.PICKUP && s.getDeliveryZone() == null) throw new IllegalArgumentException("Renseignez d'abord votre adresse");
    if (r.method() == DeliveryMethod.EXPRESS && s.getDeliveryZone().getExpressPrice() == null) {
      throw new IllegalArgumentException("La livraison express n'est pas disponible dans votre quartier");
    }
    s.setDeliveryMethod(r.method());
    if (s.getPaymentMethod() != null) {
      try {
        payments.validateChoice(s.getPaymentMethod(), s.getPaymentProvider(), r.method());
      } catch (IllegalArgumentException e) {
        s.setPaymentMethod(null);
        s.setPaymentProvider(null);
      }
    }
    s.setStep(Math.max(s.getStep(), 4));
    return view(email);
  }

  public CheckoutView payment(String email, PaymentStep r) {
    CheckoutSession s = session(users.byEmail(email));
    PaymentProvider provider = payments.validateChoice(r.method(), r.provider(), s.getDeliveryMethod());
    if (r.method() == PaymentMethod.MOBILE_MONEY && (r.phone() == null || r.phone().isBlank()) && s.getPhone() == null) {
      throw new IllegalArgumentException("Indiquez le numéro Mobile Money à débiter");
    }
    s.setPaymentMethod(r.method());
    s.setPaymentProvider(r.method() == PaymentMethod.MOBILE_MONEY ? provider : r.method().isOnline() ? PaymentProvider.MOCK : null);
    s.setPaymentPhone(Slugs.blankToNull(r.phone()));
    s.setStep(Math.max(s.getStep(), 5));
    return view(email);
  }

  /**
   * Crée la commande depuis le panier serveur, puis vide panier et brouillon. Le paiement en ligne est lancé ensuite
   * par l'appelant (hors de cette transaction).
   */
  public CustomerOrder confirm(String email) {
    AppUser user = users.byEmail(email);
    CheckoutSession s = session(user);
    if (!customerComplete(s)) throw new IllegalArgumentException("Renseignez vos informations");
    if (s.getDeliveryMethod() == null) throw new IllegalArgumentException("Choisissez un mode de livraison");
    if (s.getDeliveryMethod() != DeliveryMethod.PICKUP && !addressComplete(s)) throw new IllegalArgumentException("Renseignez votre adresse de livraison");
    if (s.getPaymentMethod() == null) throw new IllegalArgumentException("Choisissez un mode de paiement");
    PaymentProvider provider = payments.validateChoice(s.getPaymentMethod(), s.getPaymentProvider(), s.getDeliveryMethod());

    Cart cart = carts.cartOf(email);
    if (cart.getItems().isEmpty()) throw new ConflictException("Votre panier est vide");
    String payer = s.getPaymentPhone() != null ? s.getPaymentPhone() : s.getPhone();
    var order = orders.place(new OrderService.PlaceCommand(user, carts.lines(cart), cart.getPromoCode(),
        s.getFullName(), s.getEmail() != null ? s.getEmail() : user.getEmail(), s.getPhone(),
        s.getDeliveryMethod(), s.getDeliveryMethod() == DeliveryMethod.PICKUP ? null : s.getDeliveryZone(),
        s.getAddressLine(), null, s.getLandmark(), s.getPaymentMethod(), provider, payer, false));
    carts.empty(cart);
    sessions.delete(s);
    return order;
  }

  private CheckoutSession session(AppUser user) {
    return sessions.findByUserId(user.getId()).orElseGet(() -> {
      var s = new CheckoutSession();
      s.setUser(user);
      s.setFullName(user.getFullName());
      s.setPhone(user.getPhone());
      s.setEmail(user.getEmail());
      addresses.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(user.getId()).stream()
          .filter(a -> a.isDefaultAddress() && a.getDeliveryZone() != null && a.getDeliveryZone().isActive()).findFirst()
          .ifPresent(a -> {
            s.setAddressId(a.getId());
            s.setAddressLine(a.getAddressLine());
            s.setDeliveryZone(a.getDeliveryZone());
            s.setLandmark(a.getLandmark());
          });
      return sessions.save(s);
    });
  }

  private static boolean customerComplete(CheckoutSession s) { return s.getFullName() != null && s.getPhone() != null && !s.getPhone().isBlank(); }
  private static boolean addressComplete(CheckoutSession s) { return s.getAddressLine() != null && s.getDeliveryZone() != null; }

  private static SessionView sessionView(CheckoutSession s) {
    return new SessionView(s.getStep(), s.getFullName(), s.getPhone(), s.getEmail(), s.getAddressId(), s.getAddressLine(),
        ZoneResponse.of(s.getDeliveryZone()), s.getLandmark(), s.getDeliveryMethod(), s.getPaymentMethod(), s.getPaymentProvider(), s.getPaymentPhone(),
        customerComplete(s), addressComplete(s) || s.getDeliveryMethod() == DeliveryMethod.PICKUP, s.getDeliveryMethod() != null, s.getPaymentMethod() != null);
  }
}
