package ml.dibidani.domain;

public enum DeliveryMethod {
  STANDARD("Livraison standard"),
  EXPRESS("Livraison express"),
  PICKUP("Retrait au marché");

  private final String label;

  DeliveryMethod(String label) { this.label = label; }

  public String label() { return label; }
}
