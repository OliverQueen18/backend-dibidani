package ml.dibidani.api;

import jakarta.servlet.http.HttpServletRequest;
import ml.dibidani.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Notifications des opérateurs : authentifiées par signature (ou jeton de notification), jamais par session. */
@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {
  private final PaymentService payments;

  public PaymentWebhookController(PaymentService payments) { this.payments = payments; }

  @PostMapping("/{provider}")
  public ResponseEntity<Map<String, String>> receive(@PathVariable String provider, @RequestBody(required = false) String body, HttpServletRequest request) {
    Map<String, String> headers = new HashMap<>();
    for (String name : Collections.list(request.getHeaderNames())) headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
    payments.webhook(provider, body == null ? "" : body, headers);
    return ResponseEntity.ok(Map.of("status", "received"));
  }
}
