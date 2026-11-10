package com.crowded.core.event.application.service;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.event.application.dto.PublishChangeCommand;
import com.crowded.core.event.application.dto.PublishedChange;
import com.crowded.core.event.domain.model.ChangePlan;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.service.ChangePlanner;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.outbox.application.service.NotificationRequester;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.user.domain.repository.UserRepository;
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
 * connected participants, all in one transaction.
 */
@Service
public class EventChangeService {

  private static final Logger log = LoggerFactory.getLogger(EventChangeService.class);

  /** Notification type of an official change (API Outline, Messages). */
  static final String OFFICIAL_CHANGE = "OFFICIAL_CHANGE";

  private final EventRepository events;
  private final EventChangeRepository changes;
  private final UserRepository users;
  private final OrganizationRepository organizations;
  private final NotificationRequester notifications;
  private final AuditLog audit;
  private final Clock clock;
  private final MeterRegistry meters;

  EventChangeService(
      EventRepository events,
      EventChangeRepository changes,
      UserRepository users,
      OrganizationRepository organizations,
      NotificationRequester notifications,
      AuditLog audit,
      Clock clock,
      MeterRegistry meters) {
    this.events = events;
    this.changes = changes;
    this.users = users;
    this.organizations = organizations;
    this.notifications = notifications;
    this.audit = audit;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Publishes the command's change. When the event already has the requested values because this
   * change was published before, returns that change and records nothing.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not a member of its organization (SR-001)
   * @throws RuleViolationException when the change is invalid, or the event already has these
   *     values without a change that set them
   */
  @Transactional
  public PublishedChange publish(PublishChangeCommand command) {
    Event event =
        events
            .findByIdForUpdate(command.eventId())
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
    boolean member =
        users
            .findById(command.userId())
            .filter(user -> user.isMemberOf(event.getOrganizationId()))
            .isPresent();
    if (!member) {
      log.warn(
          "Refused a change to event {} by user {}: not a member of its organization",
          event.getId(),
          command.userId());
      throw new NotAllowedException(
          "NOT_EVENT_ORGANIZATION",
          "Only members of the organization that owns this event can change it.");
    }

    Instant now = clock.instant();
    Optional<ChangePlan> plan = ChangePlanner.plan(event.getState(), command.change(), now);
    if (plan.isEmpty()) {
      return alreadyPublished(event, command);
    }
    return record(event, command, plan.get(), now);
  }

  /** The earlier change that set the values the event has, as a resend finds it. */
  private PublishedChange alreadyPublished(Event event, PublishChangeCommand command) {
    return changes
        .findLatest(event.getId(), command.change().type())
        .filter(change -> change.isReflectedIn(event.getState()))
        .map(change -> new PublishedChange(change.id(), change.severity(), false))
        .orElseThrow(
            () ->
                new RuleViolationException(
                    "NOTHING_CHANGES", "The event already has these values."));
  }

  private PublishedChange record(
      Event event, PublishChangeCommand command, ChangePlan plan, Instant now) {
    Organization organization =
        organizations
            .findById(event.getOrganizationId())
            .orElseThrow(() -> new IllegalStateException("Event without its organization"));
    EventChange change = EventChange.publish(event.getId(), plan, organization.name(), now);
    changes.save(change);
    event.apply(plan);
    events.save(event);
    notifications.request(OFFICIAL_CHANGE, event.getId(), change.id());
    audit.record(
        command.userId(),
        event.getId(),
        "EVENT_CHANGE_PUBLISHED",
        "EventChange",
        change.id(),
        Map.of("type", plan.type().name(), "severity", plan.severity().name()));
    Counter.builder("crowded.event.changes")
        .description("Official event changes published")
        .tag("type", plan.type().name())
        .tag("severity", plan.severity().name())
        .register(meters)
        .increment();
    log.info(
        "Published {} change {} to event {} by user {}",
        plan.type(),
        change.id(),
        event.getId(),
        command.userId());
    return new PublishedChange(change.id(), plan.severity(), true);
  }
}
