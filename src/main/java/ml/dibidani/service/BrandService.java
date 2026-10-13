package ml.dibidani.service;

import ml.dibidani.api.dto.BrandDtos.BrandRequest;
import ml.dibidani.api.dto.BrandDtos.BrandResponse;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.Brand;
import ml.dibidani.repository.BrandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BrandService {
  private final BrandRepository brands;

  public BrandService(BrandRepository brands) { this.brands = brands; }

  public List<BrandResponse> publicList() { return brands.findByActiveTrueOrderByNameAsc().stream().map(BrandResponse::of).toList(); }
  public List<BrandResponse> adminList() { return brands.findAllByOrderByNameAsc().stream().map(BrandResponse::of).toList(); }

  @Transactional
  public BrandResponse create(BrandRequest r) {
    if (brands.existsByNameIgnoreCase(r.name().trim())) throw new ConflictException("Cette marque existe déjà");
    var b = new Brand();
    apply(b, r);
    return BrandResponse.of(brands.save(b));
  }

  @Transactional
  public BrandResponse update(Long id, BrandRequest r) {
    var b = find(id);
    if (brands.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) throw new ConflictException("Cette marque existe déjà");
    apply(b, r);
    return BrandResponse.of(b);
  }

  @Transactional
  public BrandResponse deactivate(Long id) {
    var b = find(id);
    b.setActive(false);
    return BrandResponse.of(b);
  }

  Brand find(Long id) { return brands.findById(id).orElseThrow(() -> new NotFoundException("Marque introuvable")); }

  private void apply(Brand b, BrandRequest r) {
    b.setName(r.name().trim());
    b.setLogoUrl(Slugs.blankToNull(r.logoUrl()));
    b.setActive(r.active() == null || r.active());
  }
}
