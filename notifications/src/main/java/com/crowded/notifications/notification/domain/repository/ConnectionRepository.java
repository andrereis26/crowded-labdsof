package com.crowded.notifications.notification.domain.repository;

import com.crowded.notifications.notification.domain.model.Connection;
import com.crowded.notifications.notification.domain.model.NotificationType;
import java.util.List;
import java.util.UUID;

/** The participants' connections to events, which Core writes. */
public interface ConnectionRepository {

  /**
   * Every connection to {@code eventId} through a ticket or the access code, with the participant's
   * choices for {@code type}; empty when there is none.
   */
  List<Connection> findByEvent(UUID eventId, NotificationType type);
}
