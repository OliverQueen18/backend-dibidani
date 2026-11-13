package ml.dibidani.repository;

import ml.dibidani.domain.ProductImageJob;
import ml.dibidani.domain.ProductImageJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductImageJobRepository extends JpaRepository<ProductImageJob, Long> {
  @Query(value = "select * from product_image_jobs where status='PENDING' order by created_at, id limit 1 for update skip locked", nativeQuery = true)
  Optional<ProductImageJob> claimNext();

  @org.springframework.data.jpa.repository.Modifying
  @Query(value = "update product_image_jobs set status='FAILED', message='Traitement interrompu. Réessayez.', finished_at=now() where status='PROCESSING' and started_at < :before", nativeQuery = true)
  int expireInterrupted(@Param("before") java.time.Instant before);
  Optional<ProductImageJob> findFirstByAssetIdOrderByCreatedAtDesc(Long assetId);
  List<ProductImageJob> findByAssetIdOrderByCreatedAtDesc(Long assetId);
  long countByStatusIn(List<ProductImageJobStatus> statuses);

  @Query("select j from ProductImageJob j join fetch j.asset a left join fetch a.product left join fetch a.owner where j.id = :id")
  Optional<ProductImageJob> findWithAsset(@Param("id") Long id);
}
