package ml.dibidani.service;

import ml.dibidani.api.dto.CmsDtos.BlockRequest;
import ml.dibidani.api.dto.CmsDtos.BlockResponse;
import ml.dibidani.api.dto.CmsDtos.CmsResponse;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.SiteBlock;
import ml.dibidani.domain.SiteBlockKind;
import ml.dibidani.repository.SiteBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CmsService {
  private final SiteBlockRepository blocks;
  private final ShopService shop;

  public CmsService(SiteBlockRepository blocks, ShopService shop) {
    this.blocks = blocks;
    this.shop = shop;
  }

  public CmsResponse published() {
    return new CmsResponse(shop.editable(), list(SiteBlockKind.HERO, true), list(SiteBlockKind.TESTIMONIAL, true),
        list(SiteBlockKind.PARTNER, true), list(SiteBlockKind.PAGE, true),
        list(SiteBlockKind.SOCIAL, true), list(SiteBlockKind.MENU, true), list(SiteBlockKind.HOME, true),
        list(SiteBlockKind.FAQ, true));
  }

  public CmsResponse admin() {
    return new CmsResponse(shop.editable(), list(SiteBlockKind.HERO, false), list(SiteBlockKind.TESTIMONIAL, false),
        list(SiteBlockKind.PARTNER, false), list(SiteBlockKind.PAGE, false),
        list(SiteBlockKind.SOCIAL, false), list(SiteBlockKind.MENU, false), list(SiteBlockKind.HOME, false),
        list(SiteBlockKind.FAQ, false));
  }

  public BlockResponse publicPage(String slug) {
    return BlockResponse.of(blocks.findByKindAndSlugAndActiveTrue(SiteBlockKind.PAGE, slug)
        .orElseThrow(() -> new NotFoundException("Page introuvable")));
  }

  public List<BlockResponse> list(SiteBlockKind kind, boolean publishedOnly) {
    var rows = publishedOnly
        ? blocks.findByKindAndActiveTrueOrderByDisplayOrderAscIdAsc(kind)
        : blocks.findByKindOrderByDisplayOrderAscIdAsc(kind);
    return rows.stream().map(BlockResponse::of).toList();
  }

  public BlockResponse get(Long id) {
    return BlockResponse.of(find(id));
  }

  @Transactional
  public BlockResponse create(BlockRequest r) {
    var b = new SiteBlock();
    apply(b, r);
    return BlockResponse.of(blocks.save(b));
  }

  @Transactional
  public BlockResponse update(Long id, BlockRequest r) {
    var b = find(id);
    apply(b, r);
    return BlockResponse.of(b);
  }

  @Transactional
  public void delete(Long id) {
    blocks.delete(find(id));
  }

  private SiteBlock find(Long id) {
    return blocks.findById(id).orElseThrow(() -> new NotFoundException("Contenu introuvable"));
  }

  private void apply(SiteBlock b, BlockRequest r) {
    SiteBlockKind kind = r.kind();
    b.setKind(kind);
    String slug = Slugs.blankToNull(r.slug());
    if (kind == SiteBlockKind.PAGE) {
      slug = Slugs.resolve(r.slug(), r.title(), candidate ->
          b.getId() == null
              ? blocks.existsByKindAndSlug(kind, candidate)
              : blocks.existsByKindAndSlugAndIdNot(kind, candidate, b.getId()));
    }
    if (kind == SiteBlockKind.SOCIAL && slug == null) {
      throw new IllegalArgumentException("Choisissez le réseau (WhatsApp, Facebook, Instagram, TikTok, YouTube ou LinkedIn).");
    }
    if (kind == SiteBlockKind.MENU && slug == null) {
      throw new IllegalArgumentException("Choisissez l'emplacement du menu (en-tête, pied Dibidani ou pied Aide).");
    }
    if (kind == SiteBlockKind.HOME && slug == null) {
      throw new IllegalArgumentException("Choisissez la section de la page d'accueil.");
    }
    if (kind == SiteBlockKind.FAQ && slug == null) {
      slug = "faq";
    }
    b.setSlug(slug);
    b.setTitle(r.title().trim());
    b.setSubtitle(Slugs.blankToNull(r.subtitle()));
    b.setBody(Slugs.blankToNull(r.body()));
    b.setImageUrl(Slugs.blankToNull(r.imageUrl()));
    b.setLinkUrl(Slugs.blankToNull(r.linkUrl()));
    b.setCtaLabel(Slugs.blankToNull(r.ctaLabel()));
    b.setMeta(Slugs.blankToNull(r.meta()));
    b.setDisplayOrder(r.displayOrder());
    b.setActive(r.active() == null || r.active());
    if (kind == SiteBlockKind.SOCIAL) syncSocialSetting(slug, r.linkUrl());
  }

  /** Les icônes publiques et le WhatsApp commande restent alignés avec les liens du back-office. */
  private void syncSocialSetting(String slug, String url) {
    String key = switch (slug) {
      case "facebook" -> "facebookUrl";
      case "instagram" -> "instagramUrl";
      case "tiktok" -> "tiktokUrl";
      case "youtube" -> "youtubeUrl";
      case "linkedin" -> "linkedinUrl";
      default -> null;
    };
    if (key != null) shop.update(java.util.Map.of(key, url == null ? "" : url.trim()));
  }
}
