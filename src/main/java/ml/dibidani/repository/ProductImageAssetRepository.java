package ml.dibidani.repository;

import ml.dibidani.domain.ProductImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface ProductImageAssetRepository extends JpaRepository<ProductImageAsset, Long> {
  List<ProductImageAsset> findByProductIdOrderByCreatedAtAsc(Long productId);
  List<ProductImageAsset> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
  long countByOwnerIdAndCreatedAtGreaterThanEqual(Long ownerId, Instant from);
  List<ProductImageAsset> findByConfirmedFalseAndCreatedAtBefore(Instant before);
}
