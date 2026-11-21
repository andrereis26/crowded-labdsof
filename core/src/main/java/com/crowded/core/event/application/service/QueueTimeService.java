package com.crowded.core.event.application.service;

import com.crowded.core.event.application.dto.PublishQueueTimesCommand;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.QueuePoint;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.EventUpdates;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the waiting times an organization reads at its event's queue points. Once they commit,
 * the event's page is refreshed in the cache and sent on its live streams, so connected
 * participants see them at once.
 */
@Service
public class QueueTimeService {

  private static final Logger log = LoggerFactory.getLogger(QueueTimeService.class);

  private final EventRepository events;
  private final EventAccess access;
  private final OrganizationRepository organizations;
  private final EventUpdates updates;
  private final Clock clock;
  private final MeterRegistry meters;

  QueueTimeService(
      EventRepository events,
      EventAccess access,
      OrganizationRepository organizations,
      EventUpdates updates,
      Clock clock,
      MeterRegistry meters) {
    this.events = events;
    this.access = access;
    this.organizations = organizations;
    this.updates = updates;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Publishes the command's waiting times, with the organization as their source.
   *
   * @return the event's queue points that have a waiting time, in order
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not an active member of its organization
   * @throws RuleViolationException when a time is invalid or names no point of the event
   * @throws ConflictException when the event is cancelled or ended
   */
  @Transactional
  public List<QueuePoint> publish(PublishQueueTimesCommand command) {
    Event event =
        events
            .findByIdForUpdate(command.eventId())
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
    access.checkManager(command.userId(), event);

    Organization organization =
        organizations
            .findById(event.getOrganizationId())
            .orElseThrow(() -> new IllegalStateException("Event without its organization"));
    event.publishQueueTimes(
        command.queues(), Provenance.organization(organization.getName(), clock.instant()));
    events.save(event);
    updates.announce(event.getId());
    Counter.builder("crowded.event.queue.times.published")
        .description("Queue waiting times published by organizations")
        .register(meters)
        .increment(command.queues().size());
    log.info(
        "Published {} queue waiting times of event {} by user {}",
        command.queues().size(),
        event.getId(),
        command.userId());
    return event.getQueuePoints().timed();
  }
}
