package com.crowded.notifications.notification.domain.model;

import java.util.UUID;

/**
 * A participant's connection to an event, through a ticket or the access code, with their choices
 * for one notification type.
 *
 * @param eventSetting the choice for this event; {@code null} when not set
 * @param overallSetting the overall choice; {@code null} when not set
 */
public record Connection(
    UUID userId, boolean ticket, Boolean eventSetting, Boolean overallSetting) {

  /**
   * Whether the participant wants this type for this event: the per-event choice overrides the
   * overall one, and every type is on by default.
   */
  public boolean wantsNotification() {
    if (eventSetting != null) {
      return eventSetting;
    }
    return overallSetting == null || overallSetting;
  }
}
