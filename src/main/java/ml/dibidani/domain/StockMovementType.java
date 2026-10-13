package ml.dibidani.domain;

/** Nature d'un mouvement de stock. SALE, RETURN et CANCELLATION sont réservés au workflow de commande. */
public enum StockMovementType {
  PURCHASE("Achat / réception", 1, true),
  SALE("Vente", -1, false),
  RETURN("Retour client", 1, false),
  CANCELLATION("Annulation de commande", 1, false),
  ADJUSTMENT("Ajustement d'inventaire", 0, true),
  LOSS("Perte", -1, true),
  DAMAGE("Casse / avarie", -1, true);

  private final String label;
  private final int direction;
  private final boolean manual;

  StockMovementType(String label, int direction, boolean manual) {
    this.label = label;
    this.direction = direction;
    this.manual = manual;
  }

  public String label() { return label; }

  /** +1 entrée, -1 sortie, 0 : signe libre (inventaire). */
  public int direction() { return direction; }

  /** Saisissable depuis l'écran de stock du back-office. */
  public boolean manual() { return manual; }
}
