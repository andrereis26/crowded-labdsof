package com.crowded.notifications.notification.infrastructure.persistence.repository;

import java.util.UUID;

/** An {@code event_change} with what it changed, read from its JSON. */
public interface OfficialChangeRow {

  UUID getId();

  UUID getEventId();

  String getType();

  boolean getStartChanged();

  boolean getDoorsOpenChanged();

  String getPreviousVenueName();

  String getNewVenueName();

  String getNewAddress();

  Boolean getTicketStillValid();

  String getRefundInfo();

  String getMessage();
}
