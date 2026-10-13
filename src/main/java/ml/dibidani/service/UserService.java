package ml.dibidani.service;

import ml.dibidani.api.dto.UserDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.AppUser;
import ml.dibidani.domain.Role;
import ml.dibidani.repository.AppUserRepository;
import ml.dibidani.search.SearchText;
import jakarta.persistence.criteria.Predicate;
import ml.dibidani.api.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {
  private final AppUserRepository users;
  private final PasswordEncoder encoder;

  public UserService(AppUserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  public AppUser byEmail(String email) {
    return users.findByEmailIgnoreCase(email).orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
  }

  public AppUser byEmailOrNull(String email) { return email == null ? null : users.findByEmailIgnoreCase(email).orElse(null); }

  // ---------- Utilisateurs (back-office) ----------

  public PageResponse<UserResponse> page(String q, Role role, int page, int size) {
    Specification<AppUser> spec = (root, query, cb) -> {
      List<Predicate> and = new ArrayList<>();
      if (role != null) and.add(cb.equal(root.get("role"), role));
      if (q != null && !q.isBlank()) {
        for (String term : SearchText.terms(q)) {
          String like = SearchText.contains(term);
          and.add(cb.or(cb.like(SearchText.norm(cb, root.get("fullName")), like, SearchText.ESCAPE),
              cb.like(cb.lower(root.get("email")), like, SearchText.ESCAPE),
              cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like, SearchText.ESCAPE)));
        }
      }
      return cb.and(and.toArray(Predicate[]::new));
    };
    var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Order.desc("createdAt")));
    return PageResponse.of(users.findAll(spec, pageable).map(UserResponse::of));
  }

  @Transactional
  public UserResponse create(CreateUserRequest r) {
    if (r.role() == Role.CUSTOMER || r.role() == Role.SELLER) {
      throw new IllegalArgumentException("Créez ce compte depuis l'inscription publique");
    }
    String email = r.email().trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("Cette adresse e-mail est déjà utilisée");
    var u = new AppUser();
    u.setFullName(r.fullName().trim());
    u.setEmail(email);
    u.setPhone(Slugs.blankToNull(r.phone()));
    u.setRole(r.role());
    u.setPasswordHash(encoder.encode(r.password()));
    u.setActive(true);
    return UserResponse.of(users.save(u));
  }

  /** Un administrateur ne peut ni se rétrograder ni se désactiver, et il reste toujours au moins un administrateur actif. */
  @Transactional
  public UserResponse update(Long id, UpdateUserRequest r, String actorEmail) {
    var u = users.findById(id).orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
    boolean self = u.getEmail().equalsIgnoreCase(actorEmail);
    boolean losesAdmin = u.getRole() == Role.ADMIN && u.isActive() && (r.role() != Role.ADMIN || Boolean.FALSE.equals(r.active()));
    if (self && losesAdmin) throw new ConflictException("Vous ne pouvez pas retirer vos propres droits d'administrateur");
    if (losesAdmin && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) throw new ConflictException("Il doit rester au moins un administrateur actif");
    u.setFullName(r.fullName().trim());
    u.setPhone(Slugs.blankToNull(r.phone()));
    u.setRole(r.role());
    boolean roleChanged = r.role() != u.getRole();
    if (r.active() != null) u.setActive(r.active());
    boolean passwordChanged = r.password() != null && !r.password().isBlank();
    if (passwordChanged) u.setPasswordHash(encoder.encode(r.password()));
    if (roleChanged || passwordChanged || Boolean.FALSE.equals(r.active())) u.revokeTokens();
    return UserResponse.of(u);
  }

  public List<UserResponse> couriers() { return users.findByRoleAndActiveTrueOrderByFullNameAsc(Role.COURIER).stream().map(UserResponse::of).toList(); }

  public List<RoleResponse> roles() {
    return Arrays.stream(Role.values()).map(r -> new RoleResponse(r, r.label(), r.description(),
        r.permissions().stream().map(p -> p.label()).toList(), users.countByRole(r))).toList();
  }
}
