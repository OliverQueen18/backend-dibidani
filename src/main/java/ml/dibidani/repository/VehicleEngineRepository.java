package ml.dibidani.repository;

import ml.dibidani.domain.VehicleEngine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VehicleEngineRepository extends JpaRepository<VehicleEngine, Long> {
  List<VehicleEngine> findByModelIdAndActiveTrueOrderByYearFromAscNameAsc(Long modelId);
  List<VehicleEngine> findByModelIdOrderByYearFromAscNameAsc(Long modelId);
}
