package ml.dibidani.repository;

import ml.dibidani.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
  List<CustomerAddress> findByUserIdOrderByDefaultAddressDescCreatedAtAsc(Long userId);
  Optional<CustomerAddress> findByIdAndUserId(Long id, Long userId);
  long countByUserId(Long userId);
}
