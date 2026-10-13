package ml.dibidani.repository;

import ml.dibidani.domain.AppUser;
import ml.dibidani.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {
  Optional<AppUser> findByEmailIgnoreCase(String email);
  Optional<AppUser> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);

  @Query(value = """
      select * from app_users
      where regexp_replace(coalesce(phone, ''), '[^0-9]', '', 'g') like concat('%', :digits)
      order by id
      limit 1
      """, nativeQuery = true)
  Optional<AppUser> findByPhoneDigits(@Param("digits") String digits);
  boolean existsByEmailIgnoreCase(String email);
  long countByRole(Role role);
  long countByRoleAndCreatedAtGreaterThanEqual(Role role, Instant from);
  long countByRoleAndActiveTrue(Role role);
  List<AppUser> findByRoleAndActiveTrueOrderByFullNameAsc(Role role);
  List<AppUser> findByRoleInOrderByFullNameAsc(Collection<Role> roles);
}
