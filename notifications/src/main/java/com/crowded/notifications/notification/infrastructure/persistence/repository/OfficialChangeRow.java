package com.crowded.notifications.notification.infrastructure.persistence.repository;

import java.util.UUID;

/**
 * An {@code event_change} with the values it changed, read from its JSON as text. Instants are
 * ISO-8601, as Core writes them.
 */
public interface OfficialChangeRow {

  UUID getId();

  UUID getEventId();

  String getType();

  String getPreviousStart();

  String getNewStart();

  String getPreviousDoorsOpenAt();

  String getNewDoorsOpenAt();

  String getPreviousVenueName();

  String getNewVenueName();

  String getNewAddress();

  Boolean getTicketStillValid();

  String getRefundInfo();

  String getMessage();
}
