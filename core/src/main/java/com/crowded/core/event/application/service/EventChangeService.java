package com.crowded.core.event.application.service;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.event.application.dto.PublishChangeCommand;
import com.crowded.core.event.application.dto.PublishedChange;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.EventUpdates;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.outbox.application.service.NotificationRequester;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes an organization's official change to its event (Asynchronous Flows, §3 Step 1): checks
 * the member, records the change, updates the event, audits it and asks Notifications to notify the
 * connected participants, all in one transaction. Once it commits, the event's page is refreshed in
 * the cache and on its live streams.
 */
@Service
public class EventChangeService {

  private static final Logger log = LoggerFactory.getLogger(EventChangeService.class);

  /** Notification type of an official change (API Outline, Messages). */
  static final String OFFICIAL_CHANGE = "OFFICIAL_CHANGE";

  private final EventRepository events;
  private final EventChangeRepository changes;
  private final EventAccess access;
  private final OrganizationRepository organizations;
  private final NotificationRequester notifications;
  private final EventUpdates updates;
  private final WeatherService weather;
  private final AuditLog audit;
  private final Clock clock;
  private final MeterRegistry meters;

  EventChangeService(
      EventRepository events,
      EventChangeRepository changes,
      EventAccess access,
      OrganizationRepository organizations,
      NotificationRequester notifications,
      EventUpdates updates,
      WeatherService weather,
      AuditLog audit,
      Clock clock,
      MeterRegistry meters) {
    this.events = events;
    this.changes = changes;
    this.access = access;
    this.organizations = organizations;
    this.notifications = notifications;
    this.updates = updates;
    this.weather = weather;
    this.audit = audit;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Publishes the command's change. When the event already has the requested values because this
   * change was published before, returns that change and records nothing.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not an active member of its organization
   * @throws RuleViolationException when the change is invalid, or the event already has these
   *     values without a change that set them
   */
  @Transactional
  public PublishedChange publish(PublishChangeCommand command) {
    Event event =
        events
            .findByIdForUpdate(command.eventId())
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
    access.checkManager(command.userId(), event);

    Organization organization =
        organizations
            .findById(event.getOrganizationId())
            .orElseThrow(() -> new IllegalStateException("Event without its organization"));
    Instant now = clock.instant();
    Optional<EventChange> change =
        event.publishChange(
            command.change(), Provenance.organization(organization.getName(), now), now);
    if (change.isEmpty()) {
      // A resend: the earlier change that set the values the event has.
      EventChange earlier =
          event.repeatedChange(changes.findLatest(event.getId(), command.change().type()));
      return new PublishedChange(earlier.getId(), earlier.getSeverity(), false);
    }
    return record(event, command, change.get());
  }

  private PublishedChange record(Event event, PublishChangeCommand command, EventChange change) {
    changes.save(change);
    events.save(event);
    // A new date or venue may fall under other forecasts and warnings.
    weather.refresh(event);
    notifications.request(OFFICIAL_CHANGE, event.getId(), change.getId());
    updates.announce(event.getId());
    audit.record(
        command.userId(),
        event.getId(),
        "EVENT_CHANGE_PUBLISHED",
        "EventChange",
        change.getId(),
        Map.of("type", change.getType().name(), "severity", change.getSeverity().name()));
    Counter.builder("crowded.event.changes")
        .description("Official event changes published")
        .tag("type", change.getType().name())
        .tag("severity", change.getSeverity().name())
        .register(meters)
        .increment();
    log.info(
        "Published {} change {} to event {} by user {}",
        change.getType(),
        change.getId(),
        event.getId(),
        command.userId());
    return new PublishedChange(change.getId(), change.getSeverity(), true);
  }
}
