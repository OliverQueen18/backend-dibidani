package ml.dibidani.service;

import ml.dibidani.api.dto.AdvertisementDtos.AdvertisementRequest;
import ml.dibidani.api.dto.AdvertisementDtos.AdvertisementResponse;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.AdPosition;
import ml.dibidani.domain.Advertisement;
import ml.dibidani.repository.AdvertisementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdvertisementService {
  private final AdvertisementRepository ads;

  public AdvertisementService(AdvertisementRepository ads) { this.ads = ads; }

  /** Publicités visibles : active, startDate <= maintenant <= endDate. */
  public List<AdvertisementResponse> visible(AdPosition position) {
    return ads.findVisible(position, Instant.now()).stream().map(AdvertisementResponse::of).toList();
  }

  public List<AdvertisementResponse> adminList(AdPosition position) {
    var list = position == null ? ads.findAllByOrderByPositionAscDisplayOrderAsc() : ads.findByPositionOrderByDisplayOrderAsc(position);
    return list.stream().map(AdvertisementResponse::of).toList();
  }

  public AdvertisementResponse adminGet(Long id) { return AdvertisementResponse.of(find(id)); }

  @Transactional
  public AdvertisementResponse create(AdvertisementRequest r) {
    var a = new Advertisement();
    apply(a, r);
    return AdvertisementResponse.of(ads.save(a));
  }

  @Transactional
  public AdvertisementResponse update(Long id, AdvertisementRequest r) {
    var a = find(id);
    apply(a, r);
    return AdvertisementResponse.of(a);
  }

  @Transactional
  public void delete(Long id) { ads.delete(find(id)); }

  private Advertisement find(Long id) { return ads.findById(id).orElseThrow(() -> new NotFoundException("Publicité introuvable")); }

  private void apply(Advertisement a, AdvertisementRequest r) {
    a.setTitle(r.title().trim());
    a.setDescription(Slugs.blankToNull(r.description()));
    a.setImageUrl(Slugs.blankToNull(r.imageUrl()));
    a.setMobileImageUrl(Slugs.blankToNull(r.mobileImageUrl()));
    a.setTargetUrl(Slugs.blankToNull(r.targetUrl()));
    a.setCtaLabel(Slugs.blankToNull(r.ctaLabel()));
    a.setPosition(r.position());
    a.setType(r.type());
    a.setStartDate(r.startDate());
    a.setEndDate(r.endDate());
    a.setDisplayOrder(r.displayOrder());
    a.setActive(r.active() == null || r.active());
  }
}
