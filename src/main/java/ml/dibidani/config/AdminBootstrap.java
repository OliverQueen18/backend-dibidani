package ml.dibidani.config;

import ml.dibidani.domain.AppUser;
import ml.dibidani.domain.Role;
import ml.dibidani.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Crée l'administrateur initial à partir de ADMIN_EMAIL / ADMIN_PASSWORD, y compris quand le seed démo est coupé. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminBootstrap implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
  private static final int MIN_PASSWORD_LENGTH = 8;

  private final AdminBootstrapProperties props;
  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final TransactionTemplate tx;

  public AdminBootstrap(AdminBootstrapProperties props, AppUserRepository users, PasswordEncoder encoder, TransactionTemplate tx) {
    this.props = props;
    this.users = users;
    this.encoder = encoder;
    this.tx = tx;
  }

  @Override
  public void run(String... args) {
    if (!props.credentialsPresent()) {
      if (users.countByRoleAndActiveTrue(Role.ADMIN) == 0) {
        log.warn("Aucun administrateur : définir ADMIN_EMAIL et ADMIN_PASSWORD pour créer le compte initial.");
      }
      return;
    }
    String email = props.normalizedEmail();
    if (props.password().length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalStateException("ADMIN_PASSWORD doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
    }
    tx.executeWithoutResult(status -> {
      var existing = users.findByEmailIgnoreCase(email);
      if (existing.isPresent()) {
        AppUser u = existing.get();
        if (u.getRole() != Role.ADMIN) {
          log.warn("Le compte {} existe déjà avec le rôle {} : administrateur par défaut non créé.", email, u.getRole());
        }
        return;
      }
      var admin = new AppUser();
      admin.setFullName(props.displayName());
      admin.setEmail(email);
      admin.setPhone(props.normalizedPhone());
      admin.setPasswordHash(encoder.encode(props.password()));
      admin.setRole(Role.ADMIN);
      admin.setActive(true);
      users.save(admin);
      log.info("Administrateur initial créé : {}", email);
    });
  }
}
