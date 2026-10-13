package ml.dibidani.repository;

import ml.dibidani.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
  boolean existsBySku(String sku);
}
