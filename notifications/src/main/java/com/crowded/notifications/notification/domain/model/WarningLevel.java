package com.crowded.notifications.notification.domain.model;

/** Level of an IPMA weather warning, as Core stores it. */
public enum WarningLevel {
  YELLOW("amarelo"),
  ORANGE("laranja"),
  RED("vermelho");

  private final String label;

  WarningLevel(String label) {
    this.label = label;
  }

  /** "laranja", as in "Aviso laranja". */
  public String label() {
    return label;
  }
}
