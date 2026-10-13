package ml.dibidani.domain;

public enum OrderStatus {
  NEW("À vérifier auprès des vendeurs"),
  PENDING_PAYMENT("En attente de paiement"),
  PAID("Payée — à confirmer"),
  CONFIRMED("Confirmée au client"),
  PREPARING("En préparation"),
  READY_FOR_DELIVERY("Prête pour la livraison"),
  OUT_FOR_DELIVERY("En cours de livraison"),
  DELIVERED("Livrée"),
  CANCELLED("Annulée"),
  PAYMENT_FAILED("Paiement échoué"),
  RETURNED("Retournée"),
  REFUNDED("Remboursée");

  private final String label;

  OrderStatus(String label) { this.label = label; }

  public String label() { return label; }

  /** Commande encore « vivante » côté client (ni terminée, ni annulée). */
  public boolean isOpen() {
    return switch (this) {
      case DELIVERED, CANCELLED, RETURNED, REFUNDED -> false;
      default -> true;
    };
  }

  /** Statuts comptés dans le chiffre d'affaires. */
  public boolean countsAsSale() {
    return switch (this) {
      case CONFIRMED, PREPARING, READY_FOR_DELIVERY, OUT_FOR_DELIVERY, DELIVERED -> true;
      default -> false;
    };
  }
}
