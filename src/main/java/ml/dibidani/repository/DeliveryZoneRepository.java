package ml.dibidani.repository;

import ml.dibidani.domain.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {
  List<DeliveryZone> findByActiveTrueOrderByCityAscCommuneAscDisplayOrderAscDistrictAsc();
  List<DeliveryZone> findAllByOrderByCityAscCommuneAscDisplayOrderAscDistrictAsc();
  Optional<DeliveryZone> findByIdAndActiveTrue(Long id);
  boolean existsByCityIgnoreCaseAndCommuneIgnoreCaseAndDistrictIgnoreCase(String city, String commune, String district);
  boolean existsByCityIgnoreCaseAndCommuneIgnoreCaseAndDistrictIgnoreCaseAndIdNot(String city, String commune, String district, Long id);
}
