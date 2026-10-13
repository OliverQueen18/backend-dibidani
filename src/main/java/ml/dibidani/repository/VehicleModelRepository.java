package ml.dibidani.repository;

import ml.dibidani.domain.VehicleModel;
import ml.dibidani.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {
  List<VehicleModel> findByBrandIdAndVehicleTypeAndActiveTrueOrderByNameAsc(Long brandId, VehicleType type);
  List<VehicleModel> findByBrandIdAndActiveTrueOrderByNameAsc(Long brandId);
  List<VehicleModel> findByBrandIdOrderByNameAsc(Long brandId);
  List<VehicleModel> findAllByOrderByNameAsc();
  boolean existsByBrandIdAndSlug(Long brandId, String slug);
  boolean existsByBrandIdAndSlugAndIdNot(Long brandId, String slug, Long id);
}
