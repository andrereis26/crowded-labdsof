package com.crowded.core.event.application.service;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.application.dto.SendAlertCommand;
import com.crowded.core.event.domain.model.Alert;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.repository.AlertRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.EventUpdates;
import com.crowded.core.outbox.application.service.NotificationRequester;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends an organization's operational alert to the participants connected to its event: records it,
 * audits it and asks Notifications to notify those who want operational alerts, all in one
 * transaction. Once it commits, the event's page shows it to every connected participant, live.
 */
@Service
public class AlertService {

  private static final Logger log = LoggerFactory.getLogger(AlertService.class);

  /** Notification type of an operational alert (API Outline, Messages). */
  static final String OPERATIONAL_ALERT = "OPERATIONAL_ALERT";

  private final EventRepository events;
  private final AlertRepository alerts;
  private final EventAccess access;
  private final ConnectionRepository connections;
  private final NotificationRequester notifications;
  private final EventUpdates updates;
  private final AuditLog audit;
  private final Clock clock;
  private final MeterRegistry meters;

  AlertService(
      EventRepository events,
      AlertRepository alerts,
      EventAccess access,
      ConnectionRepository connections,
      NotificationRequester notifications,
      EventUpdates updates,
      AuditLog audit,
      Clock clock,
      MeterRegistry meters) {
    this.events = events;
    this.alerts = alerts;
    this.access = access;
    this.connections = connections;
    this.notifications = notifications;
    this.updates = updates;
    this.audit = audit;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Sends the command's alert.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not an active member of its organization
   * @throws RuleViolationException when the type or the message is missing or invalid
   * @throws ConflictException when the event is cancelled or ended
   */
  @Transactional
  public Alert send(SendAlertCommand command) {
    Event event =
        events
            .findByIdForUpdate(command.eventId())
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
    access.checkManager(command.userId(), event);

    long connected =
        connections.countByEvent(List.of(event.getId())).getOrDefault(event.getId(), 0L);
    Alert alert =
        event.sendAlert(
            command.type(), command.message(), Math.toIntExact(connected), clock.instant());
    alerts.save(alert);
    notifications.request(OPERATIONAL_ALERT, event.getId(), alert.getId());
    updates.announce(event.getId());
    audit.record(
        command.userId(),
        event.getId(),
        "ALERT_SENT",
        "Alert",
        alert.getId(),
        Map.of(
            "type", alert.getType().name(),
            "severity", alert.getSeverity().name(),
            "recipients", alert.getRecipients()));
    Counter.builder("crowded.alerts.sent")
        .description("Operational alerts sent by organizations")
        .tag("type", alert.getType().name())
        .tag("severity", alert.getSeverity().name())
        .register(meters)
        .increment();
    log.info(
        "Sent {} alert {} to {} participant(s) of event {} by user {}",
        alert.getType(),
        alert.getId(),
        alert.getRecipients(),
        event.getId(),
        command.userId());
    return alert;
  }
}
