package ml.dibidani.domain;

public enum PaymentMethod {
  CASH_ON_DELIVERY("Paiement à la livraison", false),
  MOBILE_MONEY("Mobile Money", true),
  BANK_CARD("Carte bancaire", true),
  BANK_TRANSFER("Virement bancaire", false),
  CASH_AT_STORE("Paiement en magasin", false);

  private final String label;
  private final boolean online;

  PaymentMethod(String label, boolean online) {
    this.label = label;
    this.online = online;
  }

  public String label() { return label; }

  /** Paiement traité par une passerelle (sinon encaissé manuellement par l'équipe). */
  public boolean isOnline() { return online; }
}
