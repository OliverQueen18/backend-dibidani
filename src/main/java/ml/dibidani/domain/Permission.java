package ml.dibidani.domain;

/** Permission fine du back-office (autorité Spring {@code PERM_<nom>}), dérivée du rôle de l'utilisateur. */
public enum Permission {
  DASHBOARD_VIEW("Tableau de bord"),
  REPORTS_VIEW("Rapports et exports"),
  CATALOG_VIEW("Consulter le catalogue"),
  CATALOG_MANAGE("Gérer le catalogue"),
  STOCK_VIEW("Consulter les stocks"),
  STOCK_MANAGE("Mouvements de stock"),
  PROMOTIONS_MANAGE("Codes promo"),
  ADS_MANAGE("Publicités"),
  ORDERS_MANAGE("Traitement des commandes"),
  DELIVERIES_MANAGE("Livraisons et zones"),
  PAYMENTS_MANAGE("Paiements et encaissements"),
  CUSTOMERS_VIEW("Consulter les clients"),
  CUSTOMERS_MANAGE("Activer / désactiver des clients"),
  USERS_MANAGE("Utilisateurs et rôles"),
  SETTINGS_MANAGE("Paramètres de la boutique"),
  AUDIT_VIEW("Journal d'audit");

  private final String label;

  Permission(String label) { this.label = label; }

  public String label() { return label; }
  public String authority() { return "PERM_" + name(); }
}
