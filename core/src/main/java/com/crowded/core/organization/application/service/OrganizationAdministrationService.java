package com.crowded.core.organization.application.service;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.organization.application.dto.CreateOrganizationCommand;
import com.crowded.core.organization.application.dto.OrganizationOverview;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.user.application.dto.MemberOverview;
import com.crowded.core.user.application.service.AdministratorGuard;
import com.crowded.core.user.application.service.MemberAdministrationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An administrator's management of organizations: a festival that subscribes to Crowded gets its
 * organization from the Crowded team instead of registering itself. Audited.
 */
@Service
public class OrganizationAdministrationService {

  private static final Logger log =
      LoggerFactory.getLogger(OrganizationAdministrationService.class);

  private final AdministratorGuard guard;
  private final OrganizationRepository organizations;
  private final EventRepository events;
  private final MemberAdministrationService members;
  private final AuditLog audit;
  private final Clock clock;
  private final MeterRegistry meters;

  OrganizationAdministrationService(
      AdministratorGuard guard,
      OrganizationRepository organizations,
      EventRepository events,
      MemberAdministrationService members,
      AuditLog audit,
      Clock clock,
      MeterRegistry meters) {
    this.guard = guard;
    this.organizations = organizations;
    this.events = events;
    this.members = members;
    this.audit = audit;
    this.clock = clock;
    this.meters = meters;
  }

  /** Every organization by name, with its number of events and its members. */
  @Transactional(readOnly = true)
  public List<OrganizationOverview> list(UUID administratorId) {
    Map<UUID, List<MemberOverview>> byOrganization = members.membersByOrganization(administratorId);
    Map<UUID, Long> eventCounts = events.countByOrganization();
    return organizations.findAll().stream()
        .map(
            o ->
                new OrganizationOverview(
                    o.getId(),
                    o.getName(),
                    o.getCreatedAt(),
                    eventCounts.getOrDefault(o.getId(), 0L),
                    byOrganization.getOrDefault(o.getId(), List.of())))
        .toList();
  }

  /**
   * Creates an organization.
   *
   * @throws ConflictException when another organization has the name, whatever its case
   */
  @Transactional
  public UUID create(CreateOrganizationCommand command) {
    guard.require(command.administratorId());
    Organization organization =
        Organization.create(command.name(), command.administratorId(), clock.instant());
    organizations.add(organization);
    audit.record(
        command.administratorId(),
        null,
        "ORGANIZATION_CREATED",
        "Organization",
        organization.getId(),
        Map.of("name", organization.getName()));
    Counter.builder("crowded.admin.actions")
        .description("Administrator actions on organizations and members")
        .tag("action", "ORGANIZATION_CREATED")
        .register(meters)
        .increment();
    log.info(
        "Administrator {} created organization {}",
        command.administratorId(),
        organization.getId());
    return organization.getId();
  }
}
