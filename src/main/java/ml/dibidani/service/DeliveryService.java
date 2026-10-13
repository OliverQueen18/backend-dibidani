package ml.dibidani.service;

import ml.dibidani.api.dto.CartDtos.DeliveryQuote;
import ml.dibidani.api.dto.DeliveryDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.*;
import ml.dibidani.repository.AppUserRepository;
import ml.dibidani.repository.CustomerOrderRepository;
import ml.dibidani.repository.DeliveryRepository;
import ml.dibidani.repository.DeliveryZoneRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DeliveryService {
  private final DeliveryZoneRepository zones;
  private final DeliveryRepository deliveries;
  private final CustomerOrderRepository orders;
  private final AppUserRepository users;
  private final ShopService shop;

  public DeliveryService(DeliveryZoneRepository zones, DeliveryRepository deliveries, CustomerOrderRepository orders, AppUserRepository users, ShopService shop) {
    this.zones = zones;
    this.deliveries = deliveries;
    this.orders = orders;
    this.users = users;
    this.shop = shop;
  }

  public List<ZoneResponse> publicZones() { return zones.findByActiveTrueOrderByCityAscCommuneAscDisplayOrderAscDistrictAsc().stream().map(ZoneResponse::of).toList(); }
  public List<ZoneResponse> allZones() { return zones.findAllByOrderByCityAscCommuneAscDisplayOrderAscDistrictAsc().stream().map(ZoneResponse::of).toList(); }

  public DeliveryZone activeZone(Long id) {
    return zones.findByIdAndActiveTrue(id).orElseThrow(() -> new IllegalArgumentException("Ce quartier n'est pas desservi actuellement"));
  }

  /**
   * Frais de livraison calculés par le serveur. Sans quartier, seul le retrait est chiffrable (fee null sinon).
   * La livraison standard est offerte au-delà du seuil configuré ; un code FREE_DELIVERY l'offre quel que soit le mode.
   */
  public DeliveryQuote quote(Long zoneId, DeliveryMethod method, BigDecimal netSubtotal, boolean freeByPromo) {
    if (method == DeliveryMethod.PICKUP) {
      return new DeliveryQuote(null, shop.pickupAddress(), method, method.label(), BigDecimal.ZERO, shop.pickupHours(), false);
    }
    if (zoneId == null) return new DeliveryQuote(null, null, method, method.label(), null, null, false);
    DeliveryZone z = activeZone(zoneId);
    BigDecimal fee;
    String eta;
    if (method == DeliveryMethod.EXPRESS) {
      if (z.getExpressPrice() == null) throw new IllegalArgumentException("La livraison express n'est pas disponible pour " + z.getName());
      fee = z.getExpressPrice();
      eta = z.getEstimatedExpressTime();
    } else {
      fee = z.getStandardPrice();
      eta = z.getEstimatedStandardTime();
    }
    boolean free = freeByPromo || (method == DeliveryMethod.STANDARD && reachesThreshold(netSubtotal));
    return new DeliveryQuote(z.getId(), z.getName(), method, method.label(), free ? BigDecimal.ZERO : fee, eta, free);
  }

  /** Ancienne API sans quartier : forfait des paramètres boutique (gratuit au-delà du seuil). */
  public DeliveryQuote flatQuote(BigDecimal netSubtotal, boolean freeByPromo) {
    boolean free = freeByPromo || reachesThreshold(netSubtotal);
    return new DeliveryQuote(null, null, DeliveryMethod.STANDARD, DeliveryMethod.STANDARD.label(), free ? BigDecimal.ZERO : shop.deliveryFee(), null, free);
  }

  /** Rapproche un quartier saisi librement (ancienne API) d'une zone configurée, sans tenir compte des accents ni de la casse. */
  public DeliveryZone findActiveByDistrict(String district) {
    if (district == null || district.isBlank()) return null;
    String wanted = Slugs.slugify(district);
    return zones.findByActiveTrueOrderByCityAscCommuneAscDisplayOrderAscDistrictAsc().stream()
        .filter(z -> Slugs.slugify(z.getDistrict()).equals(wanted)).findFirst().orElse(null);
  }

  public List<DeliveryOption> options(Long zoneId, BigDecimal netSubtotal, boolean freeByPromo) {
    DeliveryZone z = zoneId == null ? null : zones.findByIdAndActiveTrue(zoneId).orElse(null);
    BigDecimal zero = BigDecimal.ZERO;
    var pickup = new DeliveryOption(DeliveryMethod.PICKUP, DeliveryMethod.PICKUP.label(), zero, shop.pickupHours(), true, shop.pickupAddress());
    if (z == null) {
      return List.of(
          new DeliveryOption(DeliveryMethod.STANDARD, DeliveryMethod.STANDARD.label(), null, null, false, "Choisissez d'abord votre quartier"),
          new DeliveryOption(DeliveryMethod.EXPRESS, DeliveryMethod.EXPRESS.label(), null, null, false, "Choisissez d'abord votre quartier"),
          pickup);
    }
    boolean freeStandard = freeByPromo || reachesThreshold(netSubtotal);
    var standard = new DeliveryOption(DeliveryMethod.STANDARD, DeliveryMethod.STANDARD.label(), freeStandard ? zero : z.getStandardPrice(),
        z.getEstimatedStandardTime(), true, freeStandard ? "Livraison offerte" : null);
    var express = z.getExpressPrice() == null
        ? new DeliveryOption(DeliveryMethod.EXPRESS, DeliveryMethod.EXPRESS.label(), null, null, false, "Non disponible dans ce quartier")
        : new DeliveryOption(DeliveryMethod.EXPRESS, DeliveryMethod.EXPRESS.label(), freeByPromo ? zero : z.getExpressPrice(), z.getEstimatedExpressTime(), true, null);
    return List.of(standard, express, pickup);
  }

  private boolean reachesThreshold(BigDecimal net) {
    BigDecimal threshold = shop.freeDeliveryThreshold();
    return threshold != null && threshold.signum() > 0 && net != null && net.compareTo(threshold) >= 0;
  }

  // ---------- Back-office : zones ----------

  @Transactional
  public ZoneResponse createZone(ZoneRequest r) {
    var z = new DeliveryZone();
    applyZone(z, r);
    return ZoneResponse.of(zones.save(z));
  }

  @Transactional
  public ZoneResponse updateZone(Long id, ZoneRequest r) {
    var z = zones.findById(id).orElseThrow(() -> new NotFoundException("Zone introuvable"));
    applyZone(z, r);
    return ZoneResponse.of(z);
  }

  /** Désactivation logique : les commandes passées gardent leur zone. */
  @Transactional
  public ZoneResponse deactivateZone(Long id) {
    var z = zones.findById(id).orElseThrow(() -> new NotFoundException("Zone introuvable"));
    z.setActive(false);
    return ZoneResponse.of(z);
  }

  private void applyZone(DeliveryZone z, ZoneRequest r) {
    String city = r.city().trim();
    String commune = r.commune() == null || r.commune().isBlank() ? "" : r.commune().trim();
    String district = r.district().trim();
    boolean taken = z.getId() == null
        ? zones.existsByCityIgnoreCaseAndCommuneIgnoreCaseAndDistrictIgnoreCase(city, commune, district)
        : zones.existsByCityIgnoreCaseAndCommuneIgnoreCaseAndDistrictIgnoreCaseAndIdNot(city, commune, district, z.getId());
    if (taken) throw new ConflictException("Le quartier « " + district + " » existe déjà pour cette commune");
    z.setCity(city);
    z.setCommune(commune);
    z.setDistrict(district);
    String name = Slugs.blankToNull(r.name());
    z.setName(name != null ? name : commune.isEmpty() ? district : district + " (" + commune + ")");
    z.setStandardPrice(r.standardPrice());
    z.setExpressPrice(r.expressPrice());
    z.setEstimatedStandardTime(Slugs.blankToNull(r.estimatedStandardTime()));
    z.setEstimatedExpressTime(Slugs.blankToNull(r.estimatedExpressTime()));
    z.setActive(r.active() == null || r.active());
    z.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
  }

  // ---------- Livraisons ----------

  @Transactional
  public Delivery createFor(CustomerOrder order) {
    var d = new Delivery();
    d.setOrder(order);
    d.setMethod(order.getDeliveryMethod());
    d.setStatus(DeliveryStatus.PENDING);
    return deliveries.save(d);
  }

  public Delivery forOrder(CustomerOrder order) { return deliveries.findByOrderId(order.getId()).orElse(null); }

  /** Synchronise le suivi logistique avec le statut de la commande. */
  @Transactional
  public void onOrderStatus(CustomerOrder order, OrderStatus status) {
    var d = deliveries.findByOrderId(order.getId()).orElseGet(() -> createFor(order));
    Instant now = Instant.now();
    switch (status) {
      case OUT_FOR_DELIVERY -> {
        d.setStatus(DeliveryStatus.PICKED_UP);
        d.setPickedUpAt(now);
      }
      case DELIVERED -> {
        d.setStatus(DeliveryStatus.DELIVERED);
        d.setDeliveredAt(now);
      }
      case CANCELLED -> d.setStatus(DeliveryStatus.CANCELLED);
      case RETURNED -> d.setStatus(DeliveryStatus.RETURNED);
      case READY_FOR_DELIVERY -> { if (d.getStatus() == DeliveryStatus.PICKED_UP) d.setStatus(d.getCourierName() == null ? DeliveryStatus.PENDING : DeliveryStatus.ASSIGNED); }
      default -> { }
    }
  }

  @Transactional
  public DeliveryResponse assign(Long orderId, AssignRequest r) {
    var order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Commande introuvable"));
    if (order.getDeliveryMethod() == DeliveryMethod.PICKUP) throw new ConflictException("Cette commande est à retirer au marché");
    if (!order.getStatus().isOpen()) throw new ConflictException("Cette commande est clôturée");
    var d = deliveries.findByOrderId(orderId).orElseGet(() -> createFor(order));
    if (r.courierId() != null) {
      var courier = users.findById(r.courierId()).filter(u -> u.getRole() == Role.COURIER && u.isActive())
          .orElseThrow(() -> new IllegalArgumentException("Livreur introuvable"));
      d.setCourier(courier);
      d.setCourierName(courier.getFullName());
      d.setCourierPhone(courier.getPhone());
    } else {
      d.setCourier(null);
      d.setCourierName(Slugs.blankToNull(r.courierName()));
      d.setCourierPhone(Slugs.blankToNull(r.courierPhone()));
    }
    d.setNotes(Slugs.blankToNull(r.notes()));
    if (d.getCourierName() != null && d.getStatus() == DeliveryStatus.PENDING) {
      d.setStatus(DeliveryStatus.ASSIGNED);
      d.setAssignedAt(Instant.now());
    }
    return DeliveryResponse.of(d);
  }

  public Page<DeliveryRow> page(DeliveryStatus status, Pageable pageable) {
    Specification<Delivery> spec = (root, q, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    return deliveries.findAll(spec, pageable).map(d -> {
      var o = d.getOrder();
      return new DeliveryRow(d.getId(), o.getId(), o.getOrderNumber(), o.getStatus().name(), d.getMethod(), d.getStatus(),
          o.getCustomerName(), o.getCustomerPhone(), o.getDeliveryZoneName() != null ? o.getDeliveryZoneName() : o.getDeliveryDistrict(),
          o.getDeliveryAddress(), d.getCourierName(), d.getCourierPhone(), o.getTotal(), o.getPaymentMethod(), o.getPaymentStatus().name(), d.getCreatedAt());
    });
  }
}
