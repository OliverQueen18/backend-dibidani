package ml.dibidani.domain;

public enum VehicleType {
  CAR("Voiture"),
  MOTORCYCLE("Moto"),
  TRUCK("Camion / utilitaire");

  private final String label;

  VehicleType(String label) { this.label = label; }

  public String label() { return label; }
}
