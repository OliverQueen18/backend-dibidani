package ml.dibidani.repository;

import ml.dibidani.domain.SiteBlock;
import ml.dibidani.domain.SiteBlockKind;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SiteBlockRepository extends JpaRepository<SiteBlock, Long> {
  List<SiteBlock> findByKindAndActiveTrueOrderByDisplayOrderAscIdAsc(SiteBlockKind kind);
  List<SiteBlock> findByKindOrderByDisplayOrderAscIdAsc(SiteBlockKind kind);
  Optional<SiteBlock> findByKindAndSlugAndActiveTrue(SiteBlockKind kind, String slug);
  Optional<SiteBlock> findByKindAndSlug(SiteBlockKind kind, String slug);
  boolean existsByKindAndSlugAndIdNot(SiteBlockKind kind, String slug, Long id);
  boolean existsByKindAndSlug(SiteBlockKind kind, String slug);
}
