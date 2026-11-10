package com.crowded.core.event;

import com.crowded.core.audit.AuditLog;
import com.crowded.core.outbox.Outbox;
import com.crowded.core.user.AppUserRepository;
import com.crowded.core.user.Organization;
import com.crowded.core.user.OrganizationRepository;
import com.crowded.core.web.ApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

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
  private final AppUserRepository users;
  private final OrganizationRepository organizations;
  private final Outbox outbox;
  private final AuditLog audit;
  private final JsonMapper json;
  private final Clock clock;
  private final MeterRegistry meters;

  EventChangeService(
      EventRepository events,
      EventChangeRepository changes,
      AppUserRepository users,
      OrganizationRepository organizations,
      Outbox outbox,
      AuditLog audit,
      JsonMapper json,
      Clock clock,
      MeterRegistry meters) {
    this.events = events;
    this.changes = changes;
    this.users = users;
    this.organizations = organizations;
    this.outbox = outbox;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Publishes {@code request} to the event on behalf of {@code userId}. A change the event already
   * has returns the change recorded the first time, and records nothing.
   *
   * @throws ApiException when the event does not exist, the user is not a member of its
   *     organization (SR-001), or the change is invalid
   */
  @Transactional
  public PublishedChange publish(UUID eventId, UUID userId, ChangeRequest request) {
    Event event =
        events
            .findForUpdateById(eventId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "There is no such event."));
    boolean member =
        users.findById(userId).filter(u -> u.isMemberOf(event.getOrganizationId())).isPresent();
    if (!member) {
      log.warn(
          "Refused a change to event {} by user {}: not a member of its organization",
          eventId,
          userId);
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "NOT_EVENT_ORGANIZATION",
          "Only members of the organization that owns this event can change it.");
    }

    Instant now = clock.instant();
    Optional<ChangePlan> plan = ChangePlanner.plan(event.state(), request, now);
    if (plan.isEmpty()) {
      return changes
          .findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(eventId, request.type())
          .map(change -> new PublishedChange(change.getId(), change.getSeverity(), false))
          .orElseThrow(
              () ->
                  new ApiException(
                      HttpStatus.BAD_REQUEST,
                      "NOTHING_CHANGES",
                      "The event already has these values."));
    }
    return record(event, userId, plan.get(), now);
  }

  private PublishedChange record(Event event, UUID userId, ChangePlan plan, Instant now) {
    Organization organization =
        organizations
            .findById(event.getOrganizationId())
            .orElseThrow(() -> new IllegalStateException("Event without its organization"));
    EventChange change =
        new EventChange(
            event.getId(),
            plan,
            json.writeValueAsString(plan.before().differencesFrom(plan.after())),
            json.writeValueAsString(plan.after().differencesFrom(plan.before())),
            organization.getName(),
            now);
    changes.save(change);
    event.moveTo(plan.after());
    outbox.requestNotification(OFFICIAL_CHANGE, event.getId(), change.getId());
    audit.record(
        userId,
        event.getId(),
        "EVENT_CHANGE_PUBLISHED",
        "EventChange",
        change.getId(),
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
        change.getId(),
        event.getId(),
        userId);
    return new PublishedChange(change.getId(), plan.severity(), true);
  }
}
