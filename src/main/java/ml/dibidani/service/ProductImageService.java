package ml.dibidani.service;

import ml.dibidani.api.dto.PhotoStudioDtos.*;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.TooManyRequestsException;
import ml.dibidani.config.PhotoStudioProperties;
import ml.dibidani.domain.*;
import ml.dibidani.repository.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductImageService {
  private final ProductImageAssetRepository assets;
  private final ProductImageJobRepository jobs;
  private final ProductImageStorageService files;
  private final ProductImageWatermarkService watermark;
  private final ProductImageJobService jobRunner;
  private final ProductImageProcessingService processing;
  private final ProductRepository products;
  private final AppUserRepository users;
  private final VendorRepository vendors;
  private final PhotoStudioProperties props;
  private final ObjectMapper json;
  private final AuditService audit;
  private final TransactionTemplate tx;

  public ProductImageService(ProductImageAssetRepository assets, ProductImageJobRepository jobs,
                             ProductImageStorageService files, ProductImageWatermarkService watermark,
                             ProductImageJobService jobRunner, ProductImageProcessingService processing,
                             ProductRepository products, AppUserRepository users, VendorRepository vendors,
                             PhotoStudioProperties props, ObjectMapper json, AuditService audit, TransactionTemplate tx) {
    this.assets = assets;
    this.jobs = jobs;
    this.files = files;
    this.watermark = watermark;
    this.jobRunner = jobRunner;
    this.processing = processing;
    this.products = products;
    this.users = users;
    this.vendors = vendors;
    this.props = props;
    this.json = json;
    this.audit = audit;
    this.tx = tx;
  }

  @Transactional
  public AssetResponse upload(Authentication auth, MultipartFile file, Long productId) throws IOException {
    AppUser user = actor(auth);
    Vendor shop = shopOf(user);
    Product product = productId == null ? null : products.findById(productId).orElseThrow(() -> new NotFoundException("Produit introuvable"));
    if (product != null) assertCanEditProduct(user, shop, product);
    long recent = assets.countByOwnerIdAndCreatedAtGreaterThanEqual(user.getId(), Instant.now().minus(30, ChronoUnit.DAYS));
    if (recent >= props.getQuotaPerUser()) throw new TooManyRequestsException("Quota photos atteint pour ce mois");
    var stored = files.storeOriginal(file, props.getMaxBytes());
    ProductImageAsset a = new ProductImageAsset();
    a.setOwner(user);
    a.setVendor(product != null ? product.getVendor() : shop);
    a.setProduct(product);
    a.setOriginalPath(stored.relativePath());
    a.setOriginalMime(stored.mime());
    a.setOriginalWidth(stored.width());
    a.setOriginalHeight(stored.height());
    a.setOriginalBytes(stored.bytes());
    a.setSha256(stored.sha256());
    a.setStatus(ProductImageStatus.UPLOADED);
    assets.save(a);
    audit.record(user.getEmail(), "PHOTO_UPLOAD", "ProductImage", String.valueOf(a.getId()), true, null, null, stored.mime());
    return AssetResponse.of(a);
  }

  public JobResponse process(Authentication auth, ProcessRequest r) {
    if (r == null || r.assetId() == null) throw new IllegalArgumentException("Photo manquante");
    Long jobId = tx.execute(status -> {
      long busy = jobs.countByStatusIn(List.of(ProductImageJobStatus.PENDING, ProductImageJobStatus.PROCESSING));
      if (busy >= 8) throw new TooManyRequestsException("Le studio photo est saturé, réessayez dans un instant");
      ProductImageAsset asset = assets.findById(r.assetId()).orElseThrow(() -> new NotFoundException("Photo introuvable"));
      AppUser user = actor(auth);
      assertCanManageAsset(user, shopOf(user), asset);
      if (r.productId() != null) {
        Product p = products.findById(r.productId()).orElseThrow(() -> new NotFoundException("Produit introuvable"));
        assertCanEditProduct(user, shopOf(user), p);
        asset.setProduct(p);
        asset.setVendor(p.getVendor());
      }
      var options = watermark.processOptions(
          r.removeBackground() == null || r.removeBackground(),
          r.whiteBackground() == null || r.whiteBackground(),
          r.enhance() == null || r.enhance(),
          r.watermark() == null || r.watermark(),
          r.recenter() == null || r.recenter(),
          r.shadow(), r.fillRatio(), r.zoom(), r.offsetX(), r.offsetY());
      asset.setParamsJson(json.writeValueAsString(options));
      assets.save(asset);
      ProductImageJob job = new ProductImageJob();
      job.setAsset(asset);
      job.setStatus(ProductImageJobStatus.PENDING);
      job.setProgress(0);
      job.setMessage("Traitement en cours…");
      job.setOptionsJson(asset.getParamsJson());
      job.setCreatedBy(user.getEmail());
      jobs.saveAndFlush(job);
      return job.getId();
    });
    jobRunner.run(jobId);
    return JobResponse.of(jobs.findWithAsset(jobId).orElseThrow(() -> new NotFoundException("Tâche introuvable")));
  }

  @Transactional(readOnly = true)
  public JobResponse job(Authentication auth, Long id) {
    ProductImageJob job = jobs.findWithAsset(id).orElseThrow(() -> new NotFoundException("Tâche introuvable"));
    assertCanManageAsset(actor(auth), shopOf(actor(auth)), job.getAsset());
    return JobResponse.of(job);
  }

  @Transactional
  public AssetResponse confirm(Authentication auth, Long assetId, ConfirmRequest r) {
    ProductImageAsset asset = assets.findById(assetId).orElseThrow(() -> new NotFoundException("Photo introuvable"));
    AppUser user = actor(auth);
    assertCanManageAsset(user, shopOf(user), asset);
    if (asset.getMasterUrl() == null && asset.getJpegUrl() == null) {
      throw new IllegalArgumentException("Traitez la photo avant de l’enregistrer");
    }
    Long productId = r == null ? null : r.productId();
    if (productId != null) {
      Product p = products.findById(productId).orElseThrow(() -> new NotFoundException("Produit introuvable"));
      assertCanEditProduct(user, shopOf(user), p);
      applyGallery(p, asset.publicUrl(), r.main());
      products.save(p);
      asset.setProduct(p);
      asset.setVendor(p.getVendor());
    }
    asset.setConfirmed(true);
    asset.setStatus(ProductImageStatus.CONFIRMED);
    assets.save(asset);
    return AssetResponse.of(asset);
  }

  @Transactional
  public VitrineResponse setVitrine(Authentication auth, Long productId, VitrineRequest r) {
    Product p = products.findById(productId).orElseThrow(() -> new NotFoundException("Produit introuvable"));
    AppUser user = actor(auth);
    assertCanEditProduct(user, shopOf(user), p);
    String url = r == null || r.imageUrl() == null ? "" : r.imageUrl().trim();
    if (url.isBlank()) throw new IllegalArgumentException("Image requise");
    List<String> known = currentGallery(p);
    if (!known.contains(url)) throw new IllegalArgumentException("Cette image n’appartient pas au produit.");
    applyGallery(p, url, true);
    products.save(p);
    return new VitrineResponse(p.getMainImageUrl(), List.copyOf(p.getImages()));
  }

  @Transactional(readOnly = true)
  public List<AssetResponse> list(Authentication auth, Long productId) {
    Product p = products.findById(productId).orElseThrow(() -> new NotFoundException("Produit introuvable"));
    assertCanEditProduct(actor(auth), shopOf(actor(auth)), p);
    return assets.findByProductIdOrderByCreatedAtAsc(productId).stream().map(AssetResponse::of).toList();
  }

  @Transactional
  public void delete(Authentication auth, Long assetId) {
    ProductImageAsset asset = assets.findById(assetId).orElseThrow(() -> new NotFoundException("Photo introuvable"));
    AppUser user = actor(auth);
    assertCanManageAsset(user, shopOf(user), asset);
    files.deleteQuietly(asset.getOriginalPath());
    assets.delete(asset);
  }

  @Transactional(readOnly = true)
  public Resource original(Authentication auth, Long assetId) {
    ProductImageAsset asset = assets.findById(assetId).orElseThrow(() -> new NotFoundException("Photo introuvable"));
    assertCanManageAsset(actor(auth), shopOf(actor(auth)), asset);
    return new FileSystemResource(files.resolveOriginal(asset.getOriginalPath()));
  }

  public MediaType originalMedia(Long assetId) {
    ProductImageAsset asset = assets.findById(assetId).orElseThrow(() -> new NotFoundException("Photo introuvable"));
    String mime = asset.getOriginalMime();
    if (mime == null || mime.isBlank()) return MediaType.APPLICATION_OCTET_STREAM;
    try { return MediaType.parseMediaType(mime); } catch (Exception e) { return MediaType.APPLICATION_OCTET_STREAM; }
  }

  @Transactional(readOnly = true)
  public LookupResponse lookup(Authentication auth, String code) {
    if (code == null || code.isBlank()) throw new IllegalArgumentException("Code manquant");
    String c = code.trim();
    AppUser user = actor(auth);
    Vendor shop = shopOf(user);
    Product p = products.findFirstByBarcodeIgnoreCase(c)
        .or(() -> products.findBySku(c))
        .orElseThrow(() -> new NotFoundException("Aucun produit pour ce code"));
    boolean owned = staff(user) || (shop != null && p.getVendor() != null && shop.getId().equals(p.getVendor().getId()));
    List<String> hints = new ArrayList<>();
    if (props.isSupplierLookupEnabled()) hints.add("Recherche d’images fournisseurs non configurée.");
    return new LookupResponse(p.getId(), p.getName(), p.getSku(), p.getBarcode(), p.getSlug(),
        p.getMainImageUrl(), p.getImages(), owned, hints);
  }

  public SettingsResponse settings() {
    return SettingsResponse.of(watermark.current(), processing.processorUp());
  }

  public SettingsResponse saveSettings(SettingsRequest r) {
    var s = watermark.update(r.engine(), r.watermarkEnabled(), r.watermarkPosition(), r.watermarkSize(),
        r.watermarkOpacity(), r.watermarkMargin(), r.watermarkVariant(), r.fillRatio(), r.shadowEnabled());
    return SettingsResponse.of(s, processing.processorUp());
  }

  @Scheduled(cron = "0 20 3 * * *")
  public void purgeUnconfirmed() {
    Instant cutoff = Instant.now().minus(14, ChronoUnit.DAYS);
    for (ProductImageAsset a : assets.findByConfirmedFalseAndCreatedAtBefore(cutoff)) {
      files.deleteQuietly(a.getOriginalPath());
      assets.delete(a);
    }
  }

  private List<String> currentGallery(Product p) {
    List<String> gallery = new ArrayList<>();
    if (p.getMainImageUrl() != null && !p.getMainImageUrl().isBlank()) gallery.add(p.getMainImageUrl());
    p.getImages().stream().filter(i -> i != null && !i.isBlank() && !gallery.contains(i)).forEach(gallery::add);
    return gallery;
  }

  private void applyGallery(Product p, String url, boolean main) {
    if (url == null || url.isBlank()) return;
    List<String> gallery = currentGallery(p);
    if (!gallery.contains(url)) {
      if (gallery.size() >= 12) throw new IllegalArgumentException("12 images maximum par produit");
      if (main) gallery.add(0, url);
      else gallery.add(url);
    } else if (main) {
      gallery.remove(url);
      gallery.add(0, url);
    }
    p.setMainImageUrl(gallery.isEmpty() ? null : gallery.get(0));
    p.getImages().clear();
    p.getImages().addAll(gallery);
  }

  private AppUser actor(Authentication auth) {
    if (auth == null || auth.getName() == null) throw new SecurityException("Authentification requise");
    return users.findByEmailIgnoreCase(auth.getName()).orElseThrow(() -> new SecurityException("Compte introuvable"));
  }

  private Vendor shopOf(AppUser user) {
    return vendors.findByOwnerId(user.getId()).orElse(null);
  }

  private boolean staff(AppUser user) {
    return user.getRole() != null && user.getRole().permissions().contains(Permission.CATALOG_MANAGE);
  }

  private void assertCanEditProduct(AppUser user, Vendor shop, Product product) {
    if (staff(user)) return;
    if (user.getRole() == Role.SELLER && shop != null && product.getVendor() != null && shop.getId().equals(product.getVendor().getId())) return;
    throw new SecurityException("Accès refusé");
  }

  private void assertCanManageAsset(AppUser user, Vendor shop, ProductImageAsset asset) {
    if (staff(user)) return;
    if (asset.getOwner() != null && user.getId().equals(asset.getOwner().getId())) return;
    if (user.getRole() == Role.SELLER && shop != null && asset.getVendor() != null && shop.getId().equals(asset.getVendor().getId())) return;
    throw new SecurityException("Accès refusé");
  }

  public boolean canStudio(Authentication auth) {
    if (auth == null) return false;
    boolean catalog = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("PERM_CATALOG_MANAGE"::equals);
    boolean seller = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_SELLER"::equals);
    return catalog || seller;
  }
}
