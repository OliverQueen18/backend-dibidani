package ml.dibidani.service;

import ml.dibidani.api.dto.VendorDtos.VendorRequest;
import ml.dibidani.api.dto.VendorDtos.VendorResponse;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.Vendor;
import ml.dibidani.repository.ProductRepository;
import ml.dibidani.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Vendeurs : fiches catalogue, éventuellement liées à un compte SELLER. */
@Service
@Transactional(readOnly = true)
public class VendorService {
  private final VendorRepository vendors;
  private final ProductRepository products;

  public VendorService(VendorRepository vendors, ProductRepository products) {
    this.vendors = vendors;
    this.products = products;
  }

  public List<VendorResponse> publicList() { return vendors.findByActiveTrueOrderByNameAsc().stream().map(this::toResponse).toList(); }

  public VendorResponse publicBySlug(String slug) {
    return toResponse(vendors.findBySlugAndActiveTrue(slug).orElseThrow(() -> new NotFoundException("Vendeur introuvable")));
  }

  public List<VendorResponse> adminList() { return vendors.findAllByOrderByNameAsc().stream().map(this::toResponse).toList(); }

  public VendorResponse adminGet(Long id) { return toResponse(find(id)); }

  @Transactional
  public VendorResponse create(VendorRequest r) {
    var v = new Vendor();
    apply(v, r);
    return toResponse(vendors.save(v));
  }

  @Transactional
  public VendorResponse update(Long id, VendorRequest r) {
    var v = find(id);
    apply(v, r);
    return toResponse(vendors.save(v));
  }

  /** Suppression définitive : interdite tant que des produits restent rattachés. */
  @Transactional
  public void delete(Long id) {
    var v = find(id);
    long used = products.countByVendorId(v.getId());
    if (used > 0) {
      throw new ConflictException(used == 1
          ? "Un produit est encore rattaché au vendeur « " + v.getName() + " »"
          : used + " produits sont encore rattachés au vendeur « " + v.getName() + " »");
    }
    vendors.delete(v);
  }

  Vendor find(Long id) { return vendors.findById(id).orElseThrow(() -> new NotFoundException("Vendeur introuvable")); }

  private void apply(Vendor v, VendorRequest r) {
    String slug = Slugs.resolve(r.slug(), r.name(), s -> v.getId() == null ? vendors.existsBySlug(s) : vendors.existsBySlugAndIdNot(s, v.getId()));
    boolean taken = v.getId() == null ? vendors.existsBySlug(slug) : vendors.existsBySlugAndIdNot(slug, v.getId());
    if (taken) throw new ConflictException("Le slug « " + slug + " » est déjà utilisé");
    v.setName(r.name().trim());
    v.setSlug(slug);
    v.setDescription(Slugs.blankToNull(r.description()));
    v.setLogoUrl(Slugs.blankToNull(r.logoUrl()));
    v.setPhone(Slugs.blankToNull(r.phone()));
    v.setWhatsapp(Slugs.blankToNull(r.whatsapp()));
    v.setAddress(Slugs.blankToNull(r.address()));
    v.setDistrict(Slugs.blankToNull(r.district()));
    v.setCity(r.city() == null || r.city().isBlank() ? "Bamako" : r.city().trim());
    v.setActive(r.active() == null || r.active());
  }

  private VendorResponse toResponse(Vendor v) { return VendorResponse.of(v, products.countByVendorIdAndActiveTrue(v.getId())); }
}
