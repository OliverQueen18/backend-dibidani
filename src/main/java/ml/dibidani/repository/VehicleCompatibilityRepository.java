package ml.dibidani.repository;

import ml.dibidani.domain.VehicleCompatibility;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VehicleCompatibilityRepository extends JpaRepository<VehicleCompatibility, Long> {
  List<VehicleCompatibility> findByProductIdOrderByIdAsc(Long productId);
  long countByEngineId(Long engineId);
}
