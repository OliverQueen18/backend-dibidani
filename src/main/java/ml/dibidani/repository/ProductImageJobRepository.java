package ml.dibidani.repository;

import ml.dibidani.domain.ProductImageJob;
import ml.dibidani.domain.ProductImageJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductImageJobRepository extends JpaRepository<ProductImageJob, Long> {
  Optional<ProductImageJob> findFirstByAssetIdOrderByCreatedAtDesc(Long assetId);
  List<ProductImageJob> findByAssetIdOrderByCreatedAtDesc(Long assetId);
  long countByStatusIn(List<ProductImageJobStatus> statuses);

  @Query("select j from ProductImageJob j join fetch j.asset a left join fetch a.product left join fetch a.owner where j.id = :id")
  Optional<ProductImageJob> findWithAsset(@Param("id") Long id);
}
