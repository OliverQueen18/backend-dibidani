package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.StatsDtos.*;
import ml.dibidani.api.dto.UserDtos.*;
import ml.dibidani.domain.Role;
import ml.dibidani.service.AuditService;
import ml.dibidani.service.ShopService;
import ml.dibidani.service.StatsService;
import ml.dibidani.service.StorageService;
import ml.dibidani.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final StatsService stats;
  private final UserService users;
  private final ShopService shop;
  private final StorageService storage;
  private final AuditService audit;

  public AdminController(StatsService stats, UserService users, ShopService shop, StorageService storage, AuditService audit) {
    this.stats = stats;
    this.users = users;
    this.shop = shop;
    this.storage = storage;
    this.audit = audit;
  }

  @GetMapping("/dashboard")
  @PreAuthorize("hasAuthority('PERM_DASHBOARD_VIEW')")
  public DashboardResponse dashboard(@RequestParam(required = false) Integer days,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "false") boolean all) {
    return stats.dashboard(days, from, to, all);
  }

  @GetMapping("/reports")
  @PreAuthorize("hasAuthority('PERM_REPORTS_VIEW')")
  public ReportResponse report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(defaultValue = "false") boolean all) {
    return stats.report(from, to, all);
  }

  @GetMapping(value = "/reports/export.csv", produces = "text/csv")
  @PreAuthorize("hasAuthority('PERM_REPORTS_VIEW')")
  public ResponseEntity<byte[]> exportReport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "false") boolean all) {
    var r = stats.report(from, to, all);
    var csv = new StringBuilder("\uFEFF");
    csv.append("Rapport Dibidani;").append(r.from()).append(';').append(r.to()).append('\n');
    csv.append("Chiffre d'affaires;").append(plain(r.revenue())).append('\n');
    csv.append("Commandes;").append(r.orders()).append('\n');
    csv.append("Panier moyen;").append(plain(r.averageBasket())).append('\n');
    csv.append("Articles vendus;").append(r.unitsSold()).append('\n');
    csv.append("Nouveaux clients;").append(r.newCustomers()).append("\n\n");
    csv.append("Date;Commandes;Chiffre d'affaires\n");
    r.salesByDay().forEach(d -> csv.append(d.date()).append(';').append(d.orders()).append(';').append(plain(d.revenue())).append('\n'));
    section(csv, "Ventes par catégorie", r.salesByCategory());
    section(csv, "Ventes par vendeur", r.salesByVendor());
    section(csv, "Ventes par mode de paiement", r.salesByPaymentMethod());
    section(csv, "Ventes par quartier", r.salesByZone());
    section(csv, "Commandes par statut", r.ordersByStatus());
    csv.append("\nMeilleurs produits\nProduit;SKU;Quantité;Chiffre d'affaires\n");
    r.topProducts().forEach(p -> csv.append(cell(p.name())).append(';').append(cell(p.sku())).append(';').append(p.quantity()).append(';').append(plain(p.revenue())).append('\n'));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rapport-dibidani-" + r.from() + "-" + r.to() + ".csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static void section(StringBuilder csv, String title, List<LabelValue> rows) {
    csv.append('\n').append(title).append("\nLibellé;Commandes;Montant\n");
    rows.forEach(v -> csv.append(cell(v.label())).append(';').append(v.count()).append(';').append(plain(v.amount())).append('\n'));
  }

  /** Neutralise l'injection de formules à l'ouverture dans un tableur. */
  private static String cell(String v) {
    if (v == null) return "";
    String s = v.replace("\"", "\"\"");
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
    return "\"" + s + "\"";
  }

  private static String plain(BigDecimal v) { return v == null ? "0" : v.stripTrailingZeros().toPlainString(); }

  // ---------- Clients ----------

  @GetMapping("/customers")
  @PreAuthorize("hasAuthority('PERM_CUSTOMERS_VIEW')")
  public PageResponse<CustomerRow> customers(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
    return stats.customers(q, page, size);
  }

  @GetMapping("/customers/{id}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMERS_VIEW')")
  public CustomerDetail customer(@PathVariable Long id) { return stats.customer(id); }

  @PostMapping("/customers/{id}/active")
  @PreAuthorize("hasAuthority('PERM_CUSTOMERS_MANAGE')")
  public CustomerRow setCustomerActive(@PathVariable Long id, @RequestParam boolean active) { return stats.setActive(id, active); }

  // ---------- Utilisateurs, rôles, paramètres : administrateur uniquement ----------

  @GetMapping("/users")
  @PreAuthorize("hasAuthority('PERM_USERS_MANAGE')")
  public PageResponse<UserResponse> users(@RequestParam(required = false) String q, @RequestParam(required = false) Role role,
                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return users.page(q, role, page, size);
  }

  @PostMapping("/users")
  @PreAuthorize("hasAuthority('PERM_USERS_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse createUser(@Valid @RequestBody CreateUserRequest r) { return users.create(r); }

  @PutMapping("/users/{id}")
  @PreAuthorize("hasAuthority('PERM_USERS_MANAGE')")
  public UserResponse updateUser(Authentication a, @PathVariable Long id, @Valid @RequestBody UpdateUserRequest r) { return users.update(id, r, a.getName()); }

  @GetMapping("/roles")
  @PreAuthorize("hasAuthority('PERM_USERS_MANAGE') or hasAuthority('PERM_DASHBOARD_VIEW')")
  public List<RoleResponse> roles() { return users.roles(); }

  @GetMapping("/couriers")
  @PreAuthorize("hasAuthority('PERM_DELIVERIES_MANAGE')")
  public List<UserResponse> couriers() { return users.couriers(); }

  @GetMapping("/settings")
  @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
  public Map<String, String> settings() { return shop.editable(); }

  @PutMapping("/settings")
  @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
  public Map<String, String> updateSettings(@RequestBody Map<String, String> changes) { return shop.update(changes); }

  @GetMapping("/audit")
  @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
  public PageResponse<ml.dibidani.service.AuditService.AuditRow> audit(
      @RequestParam(required = false) String q, @RequestParam(required = false) String action, @RequestParam(required = false) String actor,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
    return audit.page(q, action, actor, from, to, page, size);
  }

  @GetMapping("/uploads")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasAuthority('PERM_ADS_MANAGE') or hasAuthority('PERM_SETTINGS_MANAGE')")
  public List<StorageService.MediaItem> uploads() throws IOException {
    return storage.listPublic();
  }

  @PostMapping(value = "/uploads", consumes = "multipart/form-data")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasAuthority('PERM_ADS_MANAGE') or hasAuthority('PERM_SETTINGS_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
    return Map.of("url", storage.store(file));
  }

  @DeleteMapping("/uploads")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE') or hasAuthority('PERM_ADS_MANAGE') or hasAuthority('PERM_SETTINGS_MANAGE')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUpload(@RequestParam String url) throws IOException {
    storage.deletePublic(url);
  }

}
