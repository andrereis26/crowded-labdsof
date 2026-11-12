package com.crowded.notifications.notification.domain.service;

import com.crowded.notifications.notification.domain.model.Connection;
import com.crowded.notifications.notification.domain.model.Recipient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chooses who to notify among the participants connected to an event (FR-013, FR-041, FR-065).
 * Pure, so every rule is a unit test.
 */
public final class RecipientSelector {

  private RecipientSelector() {}

  /**
   * One recipient per participant who wants the notification, in the order of {@code connections}.
   * A participant connected more than once, such as with two tickets, is notified once, as a ticket
   * holder if any connection is a ticket.
   *
   * @param only the users to notify; empty for every connected participant
   */
  public static List<Recipient> select(Collection<Connection> connections, Collection<UUID> only) {
    Map<UUID, Boolean> ticketByUser = new LinkedHashMap<>();
    for (Connection connection : connections) {
      if (connection.wantsNotification()
          && (only.isEmpty() || only.contains(connection.userId()))) {
        ticketByUser.merge(connection.userId(), connection.ticket(), Boolean::logicalOr);
      }
    }
    List<Recipient> recipients = new ArrayList<>();
    ticketByUser.forEach((userId, ticket) -> recipients.add(new Recipient(userId, ticket)));
    return recipients;
  }
}
