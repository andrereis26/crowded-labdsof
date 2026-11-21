package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An event an organization registered: what participants read about it, how they connect to it,
 * when and where it happens, and how its organization runs it on site. Its schedule, venue and
 * status change only through {@link #publishChange}, which applies the rules of an official change;
 * the other details through corrections that participants are not notified of.
 */
public final class Event {

  /** Events that no longer take changes. */
  private static final Set<EventStatus> CLOSED = Set.of(EventStatus.CANCELLED, EventStatus.ENDED);

  private final UUID id;
  private final UUID organizationId;
  private EventProfile profile;
  private Admission admission;
  private EventState state;
  private District district;
  private Capacity capacity;
  private QueuePoints queuePoints;
  private ConfirmationSettings confirmations;
  private CrowdStatus crowd;

  private Event(
      UUID id,
      UUID organizationId,
      EventProfile profile,
      Admission admission,
      EventState state,
      District district,
      Capacity capacity,
      QueuePoints queuePoints,
      ConfirmationSettings confirmations,
      CrowdStatus crowd) {
    this.id = Objects.requireNonNull(id, "id");
    this.organizationId = organizationId;
    this.profile = Objects.requireNonNull(profile, "profile");
    this.admission = Objects.requireNonNull(admission, "admission");
    this.state = Objects.requireNonNull(state, "state");
    this.district = Objects.requireNonNull(district, "district");
    this.capacity = capacity;
    this.queuePoints = Objects.requireNonNull(queuePoints, "queuePoints");
    this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
    this.crowd = crowd;
  }

  /**
   * A new event of {@code organizationId}, published at once: participants can connect to it with
   * its code from now on. Whether another event uses the code is checked by {@code EventCodes}.
   *
   * @throws RuleViolationException when the event has no end, no location on the map or no
   *     capacity, or starts in the past
   */
  public static Event register(
      UUID organizationId,
      EventProfile profile,
      Admission admission,
      Schedule schedule,
      Venue venue,
      District district,
      Capacity capacity,
      QueuePoints queuePoints,
      ConfirmationSettings confirmations,
      Instant now) {
    Objects.requireNonNull(organizationId, "organizationId");
    if (schedule.end() == null) {
      throw new RuleViolationException("END_REQUIRED", "An event needs the time it ends.");
    }
    if (venue.coordinates() == null) {
      throw new RuleViolationException(
          "LOCATION_REQUIRED", "An event needs its venue's location on the map.");
    }
    if (capacity == null) {
      throw new RuleViolationException("CAPACITY_REQUIRED", "An event needs its capacity.");
    }
    checkFuture(schedule.start(), now);
    return new Event(
        UUID.randomUUID(),
        organizationId,
        profile,
        admission,
        new EventState(EventStatus.SCHEDULED, schedule, venue),
        district,
        capacity,
        queuePoints,
        confirmations,
        null);
  }

  /**
   * A stored event, as it was saved.
   *
   * @param organizationId the owning organization; null for an event no organization owns
   * @param capacity null when it was never set
   * @param crowd null when no crowd level was ever published
   */
  public static Event restore(
      UUID id,
      UUID organizationId,
      EventProfile profile,
      Admission admission,
      EventState state,
      District district,
      Capacity capacity,
      QueuePoints queuePoints,
      ConfirmationSettings confirmations,
      CrowdStatus crowd) {
    return new Event(
        id,
        organizationId,
        profile,
        admission,
        state,
        district,
        capacity,
        queuePoints,
        confirmations,
        crowd);
  }

  /** Corrects the name and description, such as a typo; participants are not notified. */
  public void correctProfile(EventProfile newProfile) {
    profile = Objects.requireNonNull(newProfile, "profile");
  }

  /** Sets how many people the venue holds. */
  public void changeCapacity(Capacity newCapacity) {
    capacity = Objects.requireNonNull(newCapacity, "capacity");
  }

  /**
   * Checks that the event can be deleted, such as one registered by mistake. Once a participant is
   * connected, the organization cancels it instead, which notifies them.
   *
   * @throws ConflictException when a participant is connected to it
   */
  public void checkCanBeDeleted(long connectedParticipants) {
    if (connectedParticipants > 0) {
      throw new ConflictException(
          "EVENT_HAS_PARTICIPANTS",
          "Participants are connected to this event: cancel it instead, which notifies them.");
    }
  }

  /** Corrects the district, which selects the weather warnings; participants are not notified. */
  public void correctDistrict(District newDistrict) {
    district = Objects.requireNonNull(newDistrict, "district");
  }

  /**
   * Turns a ticketed event into a public one or back, keeping its code, which from now on is read
   * as the other type's. Participants already connected keep their connection.
   *
   * @throws RuleViolationException when the type is missing
   */
  public void changeType(EventType newType) {
    admission = new Admission(newType, admission.code());
  }

  /**
   * Replaces the code of the tickets or the access code, sent in the field of the event's type.
   * Participants already connected keep their connection; the new code is for those who connect
   * from now on. Whether another event uses it is checked by {@code EventCodes}.
   *
   * @param ticketEventCode the new code of a ticketed event, or null
   * @param accessCode the new code of a public event, or null
   * @throws RuleViolationException when both are sent, or the code is in the other type's field
   */
  public void replaceCode(String ticketEventCode, String accessCode) {
    if (ticketEventCode != null && accessCode != null) {
      throw new RuleViolationException(
          "ONE_CODE_ONLY", "Send the new code of the tickets or the new access code, not both.");
    }
    boolean ticketed = admission.type() == EventType.TICKETED;
    String code = ticketed ? ticketEventCode : accessCode;
    if (code == null) {
      throw new RuleViolationException(
          "CODE_TYPE_MISMATCH",
          ticketed
              ? "This event is ticketed: send its new code as ticketEventCode."
              : "This event is public: send its new code as accessCode.");
    }
    admission = admission.withCode(new EventCode(code));
  }

  /**
   * Sets the entrances and queue points, in the order the organization lists them; a point that
   * stays, by its exact name, keeps its waiting time.
   */
  public void defineQueuePoints(QueuePoints points) {
    queuePoints = Objects.requireNonNull(points, "queuePoints").keepingTimesOf(queuePoints);
  }

  /** Sets which report categories ask for confirmations, and how. */
  public void configureConfirmations(ConfirmationSettings settings) {
    confirmations = Objects.requireNonNull(settings, "confirmations");
  }

  /**
   * Applies the official change {@code request}, published by {@code provenance}, and returns it;
   * empty when the event already is as the request asks, such as a change sent twice.
   *
   * @throws RuleViolationException when the request is invalid
   * @throws ConflictException when the event takes no more changes
   */
  public Optional<EventChange> publishChange(
      RequestedChange request, Provenance provenance, Instant now) {
    Optional<ChangePlan> plan = plan(request, now);
    plan.ifPresent(p -> state = p.after());
    return plan.map(p -> EventChange.publish(id, p, provenance));
  }

  /**
   * Publishes the crowd level participants read now, replacing the previous one; the same level
   * again tells them it is still current.
   *
   * @throws ConflictException when the event is cancelled or ended
   */
  public void publishCrowdLevel(CrowdLevel level, Provenance provenance) {
    CrowdStatus published = new CrowdStatus(level, provenance);
    if (CLOSED.contains(state.status())) {
      throw new ConflictException("EVENT_CLOSED", "This event is cancelled or ended.");
    }
    crowd = published;
  }

  /**
   * Publishes the waiting times participants read now at the points the waits name, replacing their
   * previous ones; the other points keep theirs. The same time again tells them it is still
   * current.
   *
   * @throws RuleViolationException when there is no wait, or a wait names no point of this event or
   *     the same point as another
   * @throws ConflictException when the event is cancelled or ended
   */
  public void publishQueueTimes(List<QueueWait> waits, Provenance provenance) {
    QueuePoints published = queuePoints.withTimes(waits, provenance);
    if (CLOSED.contains(state.status())) {
      throw new ConflictException("EVENT_CLOSED", "This event is cancelled or ended.");
    }
    queuePoints = published;
  }

  /**
   * The change a repeated request matches: {@code latest}, the latest published change of its type,
   * when this event still has its values.
   *
   * @throws RuleViolationException when there is no such change, so the request changes nothing
   */
  public EventChange repeatedChange(Optional<EventChange> latest) {
    return latest
        .filter(change -> change.getEventId().equals(id) && change.isReflectedIn(state))
        .orElseThrow(
            () ->
                new RuleViolationException(
                    "NOTHING_CHANGES", "The event already has these values."));
  }

  /**
   * Lets through who may see this event's page, history and live updates: an active member of the
   * organization that owns it, or a participant connected to it by a ticket or its access code.
   *
   * @param connected whether {@code user} has a connection to this event
   * @throws NotAllowedException otherwise, including after the participant left the event
   */
  public void checkCanBeSeenBy(User user, boolean connected) {
    if (user.isMemberOf(organizationId) || user.isParticipant() && connected) {
      return;
    }
    throw new NotAllowedException(
        "NOT_CONNECTED", "Only participants connected to this event can see it.");
  }

  /**
   * Lets through who may manage this event: an active member of the organization that owns it.
   *
   * @throws NotAllowedException otherwise
   */
  public void checkCanBeManagedBy(User user) {
    if (!user.isMemberOf(organizationId)) {
      throw new NotAllowedException(
          "NOT_EVENT_ORGANIZATION",
          "Only active members of the organization that owns this event can change it.");
    }
  }

  /** What {@code request} would do to this event, without doing it; empty when nothing changes. */
  Optional<ChangePlan> plan(RequestedChange request, Instant now) {
    NewValues value = request.newValues() == null ? NewValues.NONE : request.newValues();
    boolean ticketInfo = request.ticketStillValid() != null || text(request.refundInfo()) != null;
    if (ticketInfo
        && request.type() != ChangeType.POSTPONEMENT
        && request.type() != ChangeType.CANCELLATION) {
      throw new RuleViolationException(
          "TICKET_INFO_NOT_APPLICABLE",
          "Ticket validity and refund information apply only to a postponement or cancellation.");
    }
    EventState after =
        switch (request.type()) {
          case CANCELLATION -> state.inStatus(EventStatus.CANCELLED);
          case POSTPONEMENT -> postponed(value, now);
          case TIME_CHANGE -> retimed(value, now);
          case VENUE_CHANGE -> relocated(value);
          case DETAILS_UPDATE ->
              throw new RuleViolationException(
                  "UNSUPPORTED_CHANGE_TYPE",
                  "Publish a postponement, a time change, a venue change or a cancellation.");
        };
    if (after.equals(state)) {
      return Optional.empty();
    }
    if (CLOSED.contains(state.status())) {
      throw new ConflictException("EVENT_CLOSED", "This event is cancelled or ended.");
    }
    return Optional.of(
        new ChangePlan(
            request.type(),
            severity(request.type(), after),
            state,
            after,
            request.ticketStillValid(),
            text(request.refundInfo()),
            text(request.message())));
  }

  private EventState postponed(NewValues value, Instant now) {
    Schedule schedule = state.schedule();
    Instant start = value.startDateTime();
    if (start == null) {
      throw new RuleViolationException("START_REQUIRED", "A postponement needs the new start.");
    }
    if (start.equals(schedule.start())
        && unchangedOrSame(value.doorsOpenAt(), schedule.doorsOpenAt())
        && unchangedOrSame(value.endDateTime(), schedule.end())) {
      // The postponement was already applied; its status is already POSTPONED.
      return state;
    }
    if (!start.isAfter(schedule.start())) {
      throw new RuleViolationException(
          "POSTPONEMENT_NOT_LATER", "A postponement moves the start later; use a time change.");
    }
    checkFuture(start, now);
    return new EventState(
        EventStatus.POSTPONED,
        schedule.movedTo(start, value.doorsOpenAt(), value.endDateTime()),
        state.venue());
  }

  private EventState retimed(NewValues value, Instant now) {
    if (value.startDateTime() == null && value.doorsOpenAt() == null) {
      throw new RuleViolationException(
          "NEW_TIME_REQUIRED", "A time change needs the new start or doors opening.");
    }
    if (value.startDateTime() != null && !value.startDateTime().equals(state.schedule().start())) {
      checkFuture(value.startDateTime(), now);
    }
    return new EventState(
        state.status(),
        state.schedule().retimed(value.startDateTime(), value.doorsOpenAt(), value.endDateTime()),
        state.venue());
  }

  /**
   * The event at the new venue, which needs its own location: the old one would point participants
   * to the wrong place, and every event is on the map.
   */
  private EventState relocated(NewValues value) {
    Venue venue =
        new Venue(
            value.venueName(),
            value.address(),
            Coordinates.ofNullable(value.latitude(), value.longitude()));
    if (venue.coordinates() == null) {
      throw new RuleViolationException(
          "LOCATION_REQUIRED", "A venue change needs the new venue's location on the map.");
    }
    if (venue.isSamePlaceAs(state.venue())) {
      return state;
    }
    return new EventState(EventStatus.RELOCATED, state.schedule(), venue);
  }

  /** Critical: cancellation, postponement, venue or start-time change; else a warning. */
  private Severity severity(ChangeType type, EventState after) {
    if (type == ChangeType.TIME_CHANGE
        && after.schedule().start().equals(state.schedule().start())) {
      return Severity.WARNING;
    }
    return Severity.CRITICAL;
  }

  /** Checked before the new schedule, so a start in the past is reported as such. */
  private static void checkFuture(Instant start, Instant now) {
    if (!start.isAfter(now)) {
      throw new RuleViolationException("START_IN_PAST", "The new start must be in the future.");
    }
  }

  private static boolean unchangedOrSame(Instant value, Instant current) {
    return value == null || value.equals(current);
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  public UUID getId() {
    return id;
  }

  /** The owning organization; null for an event no organization owns. */
  public UUID getOrganizationId() {
    return organizationId;
  }

  public EventProfile getProfile() {
    return profile;
  }

  public Admission getAdmission() {
    return admission;
  }

  public EventState getState() {
    return state;
  }

  public District getDistrict() {
    return district;
  }

  /** How many people the venue holds; null when it was never set. */
  public Capacity getCapacity() {
    return capacity;
  }

  public QueuePoints getQueuePoints() {
    return queuePoints;
  }

  public ConfirmationSettings getConfirmations() {
    return confirmations;
  }

  /** The crowd level participants read now; null when none was published. */
  public CrowdStatus getCrowd() {
    return crowd;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Event event && id.equals(event.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Event[" + id + ", " + state.status() + "]";
  }
}
