package ml.dibidani.api;

import ml.dibidani.api.dto.DeliveryDtos.DeliveryOption;
import ml.dibidani.api.dto.DeliveryDtos.ZoneResponse;
import ml.dibidani.api.dto.OrderDtos.TrackingResponse;
import ml.dibidani.api.dto.PaymentDtos.PaymentOption;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.config.AppProperties;
import ml.dibidani.service.DeliveryService;
import ml.dibidani.service.OrderService;
import ml.dibidani.service.PaymentService;
import ml.dibidani.service.QrCodeService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/** Données publiques du parcours d'achat : quartiers livrés, moyens de paiement, suivi par QR code. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicCommerceController {
  private final DeliveryService delivery;
  private final PaymentService payments;
  private final OrderService orders;
  private final QrCodeService qr;
  private final AppProperties app;

  public PublicCommerceController(DeliveryService delivery, PaymentService payments, OrderService orders, QrCodeService qr, AppProperties app) {
    this.delivery = delivery;
    this.payments = payments;
    this.orders = orders;
    this.qr = qr;
    this.app = app;
  }

  @GetMapping("/delivery-zones")
  public List<ZoneResponse> zones() { return delivery.publicZones(); }

  @GetMapping("/delivery-options")
  public List<DeliveryOption> options(@RequestParam Long zoneId, @RequestParam(defaultValue = "0") BigDecimal subtotal) {
    return delivery.options(zoneId, subtotal.max(BigDecimal.ZERO), false);
  }

  @GetMapping("/payment-methods")
  public List<PaymentOption> paymentMethods() { return payments.options(); }

  @GetMapping("/tracking/{token}")
  public ResponseEntity<TrackingResponse> tracking(@PathVariable String token) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex").body(orders.tracking(token));
  }

  /** QR code de l'URL publique de suivi : il ne contient que le jeton aléatoire, jamais l'identifiant de la commande. */
  @GetMapping(value = "/tracking/{token}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> qrCode(@PathVariable String token, @RequestParam(defaultValue = "320") int size) {
    if (!orders.trackingExists(token)) throw new NotFoundException("Commande introuvable");
    return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
        .contentType(MediaType.IMAGE_PNG).body(qr.png(app.trackingUrl(token), Math.max(120, Math.min(size, 1024))));
  }
}
