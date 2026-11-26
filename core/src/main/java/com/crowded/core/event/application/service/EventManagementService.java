package com.crowded.core.event.application.service;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.connection.domain.repository.ActiveAlertRepository;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.application.dto.ConfirmationsInput;
import com.crowded.core.event.application.dto.CorrectEventCommand;
import com.crowded.core.event.application.dto.ManagedEvent;
import com.crowded.core.event.application.dto.RegisterEventCommand;
import com.crowded.core.event.application.dto.VenueInput;
import com.crowded.core.event.domain.model.Admission;
import com.crowded.core.event.domain.model.Capacity;
import com.crowded.core.event.domain.model.ConfirmationRules;
import com.crowded.core.event.domain.model.ConfirmationSettings;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventProfile;
import com.crowded.core.event.domain.model.QueuePoints;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventImageRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.EventUpdates;
import com.crowded.core.event.domain.repository.ReportCategoryRepository;
import com.crowded.core.event.domain.service.EventCodes;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An organization's events in the backoffice: a member registers one, corrects its details and
 * lists them. The organization is always the member's own, read from the database.
 */
@Service
public class EventManagementService {

  private static final Logger log = LoggerFactory.getLogger(EventManagementService.class);

  private final EventRepository events;
  private final ReportCategoryRepository categories;
  private final UserRepository users;
  private final ConnectionRepository connections;
  private final EventImageRepository images;
  private final ActiveAlertRepository alerts;
  private final EventAccess access;
  private final EventCodes codes;
  private final EventUpdates updates;
  private final WeatherService weather;
  private final AuditLog audit;
  private final Clock clock;
  private final MeterRegistry meters;

  EventManagementService(
      EventRepository events,
      ReportCategoryRepository categories,
      UserRepository users,
      ConnectionRepository connections,
      EventImageRepository images,
      ActiveAlertRepository alerts,
      EventAccess access,
      EventUpdates updates,
      WeatherService weather,
      AuditLog audit,
      Clock clock,
      MeterRegistry meters) {
    this.events = events;
    this.categories = categories;
    this.users = users;
    this.connections = connections;
    this.images = images;
    this.alerts = alerts;
    this.access = access;
    this.codes = new EventCodes(events);
    this.updates = updates;
    this.weather = weather;
    this.audit = audit;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Registers and publishes the command's event for the member's organization.
   *
   * @throws NotAllowedException when the user is not an active member of an organization
   * @throws RuleViolationException when a value is missing or invalid
   * @throws ConflictException when another upcoming event uses the code
   */
  @Transactional
  public ManagedEvent register(RegisterEventCommand command) {
    UUID organization = member(command.userId()).organizationManaged();
    Instant now = clock.instant();
    VenueInput venue =
        command.venue() == null ? new VenueInput(null, null, null, null, null) : command.venue();
    Event event =
        Event.register(
            organization,
            new EventProfile(command.name(), command.description()),
            new Admission(command.type(), new EventCode(command.code())),
            new Schedule(command.startDateTime(), command.doorsOpenAt(), command.endDateTime()),
            new Venue(
                venue.name(),
                venue.address(),
                Coordinates.ofNullable(venue.latitude(), venue.longitude())),
            new District(venue.district()),
            command.capacity() == null ? null : new Capacity(command.capacity()),
            QueuePoints.named(orNone(command.queuePoints())),
            command.confirmations() == null
                ? ConfirmationSettings.defaults(categories.findAll())
                : settings(command.confirmations()),
            now);
    codes.checkFree(event.getAdmission().code(), null, now);
    events.add(event);
    weather.refresh(event);

    audit.record(
        command.userId(),
        event.getId(),
        "EVENT_CREATED",
        "Event",
        event.getId(),
        Map.of("type", event.getAdmission().type().name()));
    Counter.builder("crowded.events.created")
        .description("Events registered by organizations")
        .tag("type", event.getAdmission().type().name())
        .register(meters)
        .increment();
    log.info(
        "Registered {} event {} of organization {} by user {}",
        event.getAdmission().type(),
        event.getId(),
        organization,
        command.userId());
    return new ManagedEvent(event, 0, null, List.of());
  }

  /**
   * Corrects the command's details of an event; participants already connected stay connected.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not an active member of its organization
   * @throws RuleViolationException when a value is invalid
   * @throws ConflictException when another upcoming event uses the new code
   */
  @Transactional
  public ManagedEvent correct(CorrectEventCommand command) {
    Event event =
        events
            .findByIdForUpdate(command.eventId())
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
    access.checkManager(command.userId(), event);
    Instant now = clock.instant();

    List<String> corrected = new ArrayList<>();
    if (command.name() != null || command.description() != null) {
      EventProfile current = event.getProfile();
      event.correctProfile(
          new EventProfile(
              command.name() == null ? current.name() : command.name(),
              command.description() == null ? current.description() : command.description()));
      corrected.add("profile");
    }
    if (command.capacity() != null) {
      event.changeCapacity(new Capacity(command.capacity()));
      corrected.add("capacity");
    }
    if (command.type() != null) {
      event.changeType(command.type());
      corrected.add("type");
    }
    if (command.ticketEventCode() != null || command.accessCode() != null) {
      event.replaceCode(command.ticketEventCode(), command.accessCode());
      codes.checkFree(event.getAdmission().code(), event.getId(), now);
      corrected.add("code");
    }
    if (command.district() != null) {
      event.correctDistrict(new District(command.district()));
      corrected.add("district");
    }
    if (command.queuePoints() != null) {
      event.defineQueuePoints(QueuePoints.named(command.queuePoints()));
      corrected.add("queuePoints");
    }
    if (command.confirmations() != null) {
      event.configureConfirmations(settings(command.confirmations()));
      corrected.add("confirmations");
    }
    events.save(event);
    if (command.district() != null) {
      weather.refresh(event);
    }
    updates.announce(event.getId());

    audit.record(
        command.userId(),
        event.getId(),
        "EVENT_UPDATED",
        "Event",
        event.getId(),
        Map.of("fields", String.join(",", corrected)));
    log.info("Corrected {} of event {} by user {}", corrected, event.getId(), command.userId());
    return new ManagedEvent(
        event,
        connections.countByEvent(List.of(event.getId())).getOrDefault(event.getId(), 0L),
        images.findUpdatedAt(List.of(event.getId())).get(event.getId()),
        alerts.findByEvents(List.of(event.getId())).getOrDefault(event.getId(), List.of()));
  }

  /**
   * Deletes an event nobody is connected to, such as one registered by mistake; participants are
   * not notified, as there are none.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not an active member of its organization
   * @throws ConflictException when a participant is connected to it
   */
  @Transactional
  public void delete(UUID userId, UUID eventId) {
    Event event =
        events
            .findByIdForUpdate(eventId)
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
    access.checkManager(userId, event);
    event.checkCanBeDeleted(connections.countByEvent(List.of(eventId)).getOrDefault(eventId, 0L));
    events.delete(event);
    updates.announce(eventId);

    // Not linked to the event, or deleting the event would delete this entry with it.
    audit.record(
        userId, null, "EVENT_DELETED", "Event", eventId, Map.of("name", event.getProfile().name()));
    log.info("Deleted event {} by user {}", eventId, userId);
  }

  /**
   * The events of the member's organization, the latest start first.
   *
   * @throws NotAllowedException when the user is not an active member of an organization
   */
  @Transactional(readOnly = true)
  public List<ManagedEvent> listForMember(UUID userId) {
    List<Event> found = events.findByOrganization(member(userId).organizationManaged());
    List<UUID> ids = found.stream().map(Event::getId).toList();
    Map<UUID, Long> connected = connections.countByEvent(ids);
    Map<UUID, Instant> imaged = images.findUpdatedAt(ids);
    Map<UUID, List<ActiveAlert>> alerted = alerts.findByEvents(ids);
    return found.stream()
        .map(
            e ->
                new ManagedEvent(
                    e,
                    connected.getOrDefault(e.getId(), 0L),
                    imaged.get(e.getId()),
                    alerted.getOrDefault(e.getId(), List.of())))
        .toList();
  }

  private User member(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(
            () ->
                new NotAllowedException(
                    "NOT_ORGANIZATION_MEMBER",
                    "Only active members of an organization manage its events."));
  }

  private ConfirmationSettings settings(ConfirmationsInput input) {
    return ConfirmationSettings.chosen(
        orNone(input.categories()),
        new ConfirmationRules(
            input.venueRadiusMeters(), input.highConfidenceAnswers(), input.maxPromptsPerHour()),
        categories.findAll());
  }

  private static List<String> orNone(List<String> values) {
    return values == null ? List.of() : values;
  }

  private static Set<String> orNone(Set<String> values) {
    return values == null ? Set.of() : values;
  }
}
