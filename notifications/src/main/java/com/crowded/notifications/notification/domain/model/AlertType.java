package com.crowded.notifications.notification.domain.model;

/**
 * Kind of an operational alert, as Core records it in {@code alert_type}, with the title its
 * notification shows in European Portuguese.
 */
public enum AlertType {
  ENTRANCE_CLOSURE("Entrada fechada"),
  CAPACITY_REACHED("Lotação esgotada"),
  SCHEDULE_DELAY("Atraso no horário"),
  WEATHER_WARNING("Aviso meteorológico"),
  OTHER("Alerta da organização");

  private final String title;

  AlertType(String title) {
    this.title = title;
  }

  /** The notification's title for an alert of this kind. */
  public String title() {
    return title;
  }
}
