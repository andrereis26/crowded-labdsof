package com.crowded.core.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.organization.application.dto.CreateOrganizationCommand;
import com.crowded.core.organization.application.dto.OrganizationOverview;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.user.application.dto.MemberOverview;
import com.crowded.core.user.application.service.AdministratorGuard;
import com.crowded.core.user.application.service.MemberAdministrationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrganizationAdministrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final UUID ADMIN = UUID.randomUUID();

  private final AdministratorGuard guard = mock(AdministratorGuard.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final EventRepository events = mock(EventRepository.class);
  private final MemberAdministrationService members = mock(MemberAdministrationService.class);
  private final AuditLog audit = mock(AuditLog.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final OrganizationAdministrationService service =
      new OrganizationAdministrationService(
          guard, organizations, events, members, audit, Clock.fixed(NOW, ZoneOffset.UTC), meters);

  @Test
  void createsAnOrganizationAndAuditsIt() {
    UUID id = service.create(new CreateOrganizationCommand(ADMIN, "  Festival Paredes "));

    ArgumentCaptor<Organization> stored = ArgumentCaptor.forClass(Organization.class);
    verify(organizations).add(stored.capture());
    assertThat(stored.getValue())
        .usingRecursiveComparison()
        .isEqualTo(Organization.restore(id, "Festival Paredes", ADMIN, NOW));
    verify(audit)
        .record(
            ADMIN,
            null,
            "ORGANIZATION_CREATED",
            "Organization",
            id,
            Map.of("name", "Festival Paredes"));
    assertThat(meters.counter("crowded.admin.actions", "action", "ORGANIZATION_CREATED").count())
        .isOne();
  }

  @Test
  void refusesAnOrganizationNameTakenOrANonAdministrator() {
    doThrow(new ConflictException("ORGANIZATION_NAME_TAKEN", "Taken."))
        .when(organizations)
        .add(any());
    assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(ADMIN, "Ribeira Live")))
        .isInstanceOf(ConflictException.class);

    UUID member = UUID.randomUUID();
    doThrow(new NotAllowedException("ADMINISTRATOR_ONLY", "No.")).when(guard).require(member);
    assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(member, "X")))
        .isInstanceOf(NotAllowedException.class);
    verify(audit, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void listsEachOrganizationWithItsEventsAndMembers() {
    Organization ribeira = Organization.restore(UUID.randomUUID(), "Ribeira Live", ADMIN, NOW);
    Organization minho = Organization.restore(UUID.randomUUID(), "Sons do Minho", ADMIN, NOW);
    MemberOverview rita =
        new MemberOverview(
            UUID.randomUUID(), ribeira.getId(), "Rita", "rita@x.pt", true, true, NOW, null);
    when(organizations.findAll()).thenReturn(List.of(ribeira, minho));
    when(events.countByOrganization()).thenReturn(Map.of(ribeira.getId(), 4L));
    when(members.membersByOrganization(ADMIN)).thenReturn(Map.of(ribeira.getId(), List.of(rita)));

    List<OrganizationOverview> list = service.list(ADMIN);

    assertThat(list)
        .containsExactly(
            new OrganizationOverview(ribeira.getId(), "Ribeira Live", NOW, 4, List.of(rita)),
            new OrganizationOverview(minho.getId(), "Sons do Minho", NOW, 0, List.of()));
  }
}
