package com.crowded.notifications.notification.infrastructure.persistence.repository;

import java.util.UUID;

/** A connection to an event with the participant's choices for one notification type. */
public interface ConnectionRow {

  UUID getUserId();

  boolean getTicket();

  Boolean getEventSetting();

  Boolean getOverallSetting();
}
