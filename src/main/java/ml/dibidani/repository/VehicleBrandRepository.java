package ml.dibidani.repository;

import ml.dibidani.domain.VehicleBrand;
import ml.dibidani.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface VehicleBrandRepository extends JpaRepository<VehicleBrand, Long> {
  Optional<VehicleBrand> findBySlug(String slug);
  boolean existsBySlug(String slug);
  boolean existsBySlugAndIdNot(String slug, Long id);
  List<VehicleBrand> findAllByOrderByDisplayOrderAscNameAsc();

  @Query("select distinct b from VehicleBrand b, VehicleModel m where m.brand = b and b.active = true and m.active = true and m.vehicleType = :type order by b.displayOrder, b.name")
  List<VehicleBrand> findActiveByType(@Param("type") VehicleType type);
}
