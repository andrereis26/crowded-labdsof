package com.crowded.core.event.application.service;

import com.crowded.core.event.application.dto.PublishCrowdLevelCommand;
import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.Provenance;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the crowd level an organization reads at its event. Once it commits, the event's page
 * is refreshed in the cache and sent on its live streams, so connected participants see it at once.
 */
@Service
public class CrowdLevelService {

  private static final Logger log = LoggerFactory.getLogger(CrowdLevelService.class);

  private final EventRepository events;
  private final EventAccess access;
  private final OrganizationRepository organizations;
  private final EventUpdates updates;
  private final Clock clock;
  private final MeterRegistry meters;

  CrowdLevelService(
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
   * Publishes the command's level, with the organization as its source.
   *
   * @return the crowd level participants read now
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not an active member of its organization
   * @throws RuleViolationException when the level is missing
   * @throws ConflictException when the event is cancelled or ended
   */
  @Transactional
  public CrowdStatus publish(PublishCrowdLevelCommand command) {
    Event event =
        events
            .findByIdForUpdate(command.eventId())
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
    access.checkManager(command.userId(), event);

    Organization organization =
        organizations
            .findById(event.getOrganizationId())
            .orElseThrow(() -> new IllegalStateException("Event without its organization"));
    event.publishCrowdLevel(
        command.level(), Provenance.organization(organization.getName(), clock.instant()));
    events.save(event);
    updates.announce(event.getId());
    Counter.builder("crowded.event.crowd.published")
        .description("Crowd levels published by organizations")
        .tag("level", command.level().name())
        .register(meters)
        .increment();
    log.info(
        "Published crowd level {} of event {} by user {}",
        command.level(),
        event.getId(),
        command.userId());
    return event.getCrowd();
  }
}
