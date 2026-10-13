package ml.dibidani.domain;

import java.util.EnumSet;
import java.util.Set;
import static ml.dibidani.domain.Permission.*;

/**
 * Rôles applicatifs (autorité Spring {@code ROLE_<nom>}). SELLER est un compte boutique sans accès au back-office.
 * COURIER est réservé à la future application Livreur.
 */
public enum Role {
  ADMIN("Administrateur", "Accès complet, y compris utilisateurs, rôles, paramètres et journal d'audit.", EnumSet.allOf(Permission.class)),
  MANAGER("Gestionnaire général", "Gère l'activité quotidienne sans accès aux utilisateurs, aux paramètres ni à l'audit.",
      EnumSet.complementOf(EnumSet.of(USERS_MANAGE, SETTINGS_MANAGE, AUDIT_VIEW, CUSTOMERS_MANAGE))),
  CATALOG_MANAGER("Responsable catalogue", "Produits, catégories, marques, vendeurs, unités, véhicules, promotions et publicités.",
      EnumSet.of(DASHBOARD_VIEW, CATALOG_VIEW, CATALOG_MANAGE, PROMOTIONS_MANAGE, ADS_MANAGE, STOCK_VIEW)),
  ORDER_MANAGER("Responsable commandes", "Commandes, livraisons, paiements et clients.",
      EnumSet.of(DASHBOARD_VIEW, ORDERS_MANAGE, DELIVERIES_MANAGE, PAYMENTS_MANAGE, CUSTOMERS_VIEW, REPORTS_VIEW, CATALOG_VIEW)),
  STOCK_MANAGER("Responsable stock", "Réceptions, inventaires, pertes et alertes de stock.",
      EnumSet.of(DASHBOARD_VIEW, STOCK_VIEW, STOCK_MANAGE, CATALOG_VIEW)),
  COURIER("Livreur", "Réservé à la future application Livreur ; aucun accès au back-office.", EnumSet.noneOf(Permission.class)),
  SELLER("Vendeur", "Compte commerçant : boutique liée, sans accès au back-office Dibidani.", EnumSet.noneOf(Permission.class)),
  CUSTOMER("Client", "Compte client de la boutique en ligne.", EnumSet.noneOf(Permission.class));

  private final String label;
  private final String description;
  private final Set<Permission> permissions;

  Role(String label, String description, Set<Permission> permissions) {
    this.label = label;
    this.description = description;
    this.permissions = permissions;
  }

  public String label() { return label; }
  public String description() { return description; }
  public Set<Permission> permissions() { return permissions; }
  public boolean isStaff() { return !permissions.isEmpty(); }
}
