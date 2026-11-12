package com.crowded.notifications.notification.application.service;

import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.domain.exception.CauseNotFoundException;
import com.crowded.notifications.notification.domain.exception.UnsupportedNotificationTypeException;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationType;
import com.crowded.notifications.notification.domain.model.OfficialChange;
import com.crowded.notifications.notification.domain.model.Recipient;
import com.crowded.notifications.notification.domain.repository.ConnectionRepository;
import com.crowded.notifications.notification.domain.repository.NotificationRepository;
import com.crowded.notifications.notification.domain.repository.OfficialChangeRepository;
import com.crowded.notifications.notification.domain.service.RecipientSelector;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifies the participants connected to an event of an official change (FR-013, FR-075), once per
 * participant however often the request arrives (NFR-003).
 */
@Service
public class ParticipantNotifier {

  private static final Logger log = LoggerFactory.getLogger(ParticipantNotifier.class);

  private final OfficialChangeRepository changes;
  private final ConnectionRepository connections;
  private final NotificationRepository notifications;
  private final Clock clock;

  ParticipantNotifier(
      OfficialChangeRepository changes,
      ConnectionRepository connections,
      NotificationRepository notifications,
      Clock clock) {
    this.changes = changes;
    this.connections = connections;
    this.notifications = notifications;
    this.clock = clock;
  }

  /**
   * Stores one notification per participant who wants it, in one transaction.
   *
   * @return how many notifications were stored; none for a request already processed
   * @throws UnsupportedNotificationTypeException for a type other than an official change
   * @throws CauseNotFoundException when the event has no such change
   */
  @Transactional
  public int notify(NotifyParticipantsCommand command) {
    if (command.type() != NotificationType.OFFICIAL_CHANGE) {
      throw new UnsupportedNotificationTypeException(command.type());
    }
    OfficialChange change =
        changes
            .findById(command.causeId())
            .filter(found -> found.isOf(command.eventId()))
            .orElseThrow(() -> new CauseNotFoundException(command.eventId(), command.causeId()));
    List<Recipient> recipients =
        RecipientSelector.select(
            connections.findByEvent(command.eventId(), command.type()), command.userIds());
    Instant now = clock.instant();
    int stored = 0;
    for (Recipient recipient : recipients) {
      Notification notification =
          Notification.create(
              command.messageId(),
              recipient,
              command.eventId(),
              command.type(),
              change.getId(),
              change.describeFor(recipient),
              now);
      if (notifications.addIfAbsent(notification)) {
        stored++;
      }
    }
    log.info(
        "Message {}: {} notification(s) of {} {} for {} recipient(s) of event {}",
        command.messageId(),
        stored,
        command.type(),
        change.getId(),
        recipients.size(),
        command.eventId());
    return stored;
  }
}
