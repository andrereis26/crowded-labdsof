package com.crowded.notifications.notification.application.service;

import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.domain.exception.CauseNotFoundException;
import com.crowded.notifications.notification.domain.exception.UnsupportedNotificationTypeException;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationText;
import com.crowded.notifications.notification.domain.model.OfficialChange;
import com.crowded.notifications.notification.domain.model.Recipient;
import com.crowded.notifications.notification.domain.repository.ConnectionRepository;
import com.crowded.notifications.notification.domain.repository.NotificationAnnouncements;
import com.crowded.notifications.notification.domain.repository.NotificationRepository;
import com.crowded.notifications.notification.domain.repository.OfficialChangeRepository;
import com.crowded.notifications.notification.domain.repository.OperationalAlertRepository;
import com.crowded.notifications.notification.domain.repository.WeatherWarningRepository;
import com.crowded.notifications.notification.domain.service.RecipientSelector;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifies the participants connected to an event of an official change, an operational alert or an
 * IPMA weather warning, once per participant however often the request arrives.
 */
@Service
public class ParticipantNotifier {

  private static final Logger log = LoggerFactory.getLogger(ParticipantNotifier.class);

  private final OfficialChangeRepository changes;
  private final OperationalAlertRepository alerts;
  private final WeatherWarningRepository warnings;
  private final ConnectionRepository connections;
  private final NotificationRepository notifications;
  private final NotificationAnnouncements announcements;
  private final Clock clock;

  ParticipantNotifier(
      OfficialChangeRepository changes,
      OperationalAlertRepository alerts,
      WeatherWarningRepository warnings,
      ConnectionRepository connections,
      NotificationRepository notifications,
      NotificationAnnouncements announcements,
      Clock clock) {
    this.changes = changes;
    this.alerts = alerts;
    this.warnings = warnings;
    this.connections = connections;
    this.notifications = notifications;
    this.announcements = announcements;
    this.clock = clock;
  }

  /**
   * Stores one notification per participant who wants it, in one transaction, and announces each
   * one stored to the live streams once it commits. A notification that already existed is not
   * announced again, so a redelivered message shows no second banner.
   *
   * @return how many notifications were stored; none for a request already processed
   * @throws UnsupportedNotificationTypeException for a type other than an official change, an
   *     operational alert or a weather warning
   * @throws CauseNotFoundException when the event has no such change, alert or warning
   */
  @Transactional
  public int notify(NotifyParticipantsCommand command) {
    Function<Recipient, NotificationText> text = textOf(command);
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
              command.causeId(),
              text.apply(recipient),
              now);
      if (notifications.addIfAbsent(notification)) {
        announcements.announce(notification.getId());
        stored++;
      }
    }
    log.info(
        "Message {}: {} notification(s) of {} {} for {} recipient(s) of event {}",
        command.messageId(),
        stored,
        command.type(),
        command.causeId(),
        recipients.size(),
        command.eventId());
    return stored;
  }

  /** What each recipient reads, from the change, alert or warning behind the request. */
  private Function<Recipient, NotificationText> textOf(NotifyParticipantsCommand command) {
    return switch (command.type()) {
      case OFFICIAL_CHANGE -> {
        OfficialChange change =
            changes
                .findById(command.causeId())
                .filter(found -> found.isOf(command.eventId()))
                .orElseThrow(() -> notFound(command));
        yield change::describeFor;
      }
      case OPERATIONAL_ALERT -> {
        NotificationText alert =
            alerts
                .findById(command.causeId())
                .filter(found -> found.isOf(command.eventId()))
                .orElseThrow(() -> notFound(command))
                .describe();
        yield recipient -> alert;
      }
      case WEATHER_WARNING -> {
        NotificationText warning =
            warnings
                .findById(command.causeId())
                .filter(found -> found.isOf(command.eventId()))
                .orElseThrow(() -> notFound(command))
                .describe();
        yield recipient -> warning;
      }
      default -> throw new UnsupportedNotificationTypeException(command.type());
    };
  }

  private static CauseNotFoundException notFound(NotifyParticipantsCommand command) {
    return new CauseNotFoundException(command.eventId(), command.causeId());
  }
}
