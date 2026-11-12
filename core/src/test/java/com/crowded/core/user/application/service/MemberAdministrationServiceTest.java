package com.crowded.core.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.shared.domain.exception.DomainException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.user.application.dto.AddMemberCommand;
import com.crowded.core.user.application.dto.ChangeMemberCommand;
import com.crowded.core.user.application.dto.MemberChanged;
import com.crowded.core.user.application.dto.MemberOverview;
import com.crowded.core.user.application.dto.TemporaryPassword;
import com.crowded.core.user.domain.model.Email;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.domain.repository.PasswordHasher;
import com.crowded.core.user.domain.repository.TokenRevocations;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MemberAdministrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final UUID ADMIN = UUID.randomUUID();
  private static final UUID ORGANIZATION = UUID.randomUUID();

  private final UserRepository users = mock(UserRepository.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final PasswordHasher passwords = mock(PasswordHasher.class);
  private final TokenRevocations revocations = mock(TokenRevocations.class);
  private final AuditLog audit = mock(AuditLog.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final MemberAdministrationService service =
      new MemberAdministrationService(
          new AdministratorGuard(users),
          users,
          organizations,
          passwords,
          revocations,
          audit,
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters,
          () -> 0L);

  @BeforeEach
  void givenAnAdministratorAndAnOrganization() {
    when(users.findById(ADMIN))
        .thenReturn(Optional.of(User.restore(ADMIN, null, Role.ADMINISTRATOR, true)));
    when(organizations.findById(ORGANIZATION))
        .thenReturn(Optional.of(Organization.restore(ORGANIZATION, "Ribeira Live", ADMIN, NOW)));
    when(passwords.hash(anyString())).thenAnswer(call -> "hash:" + call.getArgument(0));
  }

  @Test
  void createsAMemberWithATemporaryPasswordShownOnce() {
    TemporaryPassword issued =
        service.add(
            new AddMemberCommand(ADMIN, ORGANIZATION, " Rita Ferreira ", "Rita@Ribeira.PT "));

    ArgumentCaptor<UserAccount> stored = ArgumentCaptor.forClass(UserAccount.class);
    verify(users).add(stored.capture());
    UserAccount member = stored.getValue();
    assertThat(issued.password()).isEqualTo("AAA-AAA-AAAA");
    assertThat(member.getPasswordHash()).isEqualTo("hash:AAA-AAA-AAAA");
    assertThat(member.getRole()).isEqualTo(Role.ORGANIZATION_MEMBER);
    assertThat(member.getOrganizationId()).isEqualTo(ORGANIZATION);
    assertThat(member.getName()).isEqualTo("Rita Ferreira");
    assertThat(member.getEmail()).isEqualTo(new Email("rita@ribeira.pt"));
    assertThat(member.isMustChangePassword()).isTrue();
    assertThat(member.getTemporaryPasswordExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    assertThat(issued.expiresAt()).isEqualTo(member.getTemporaryPasswordExpiresAt());
    assertThat(issued.userId()).isEqualTo(member.getId());
    verify(audit)
        .record(
            eq(ADMIN),
            isNull(),
            eq("MEMBER_CREATED"),
            eq("User"),
            eq(member.getId()),
            eq(Map.of("organizationId", ORGANIZATION.toString())));
    assertThat(meters.counter("crowded.admin.actions", "action", "MEMBER_CREATED").count()).isOne();
  }

  @Test
  void refusesAnUnknownOrganization() {
    assertRefused(
        () -> service.add(new AddMemberCommand(ADMIN, UUID.randomUUID(), "Rita", "r@x.pt")),
        NotFoundException.class,
        "ORGANIZATION_NOT_FOUND");
    verify(users, never()).add(any());
  }

  @Test
  void refusesAnyoneButAnActiveAdministrator() {
    UUID member = UUID.randomUUID();
    UUID formerAdmin = UUID.randomUUID();
    when(users.findById(member))
        .thenReturn(
            Optional.of(User.restore(member, ORGANIZATION, Role.ORGANIZATION_MEMBER, true)));
    when(users.findById(formerAdmin))
        .thenReturn(Optional.of(User.restore(formerAdmin, null, Role.ADMINISTRATOR, false)));

    for (UUID caller : new UUID[] {member, formerAdmin, UUID.randomUUID()}) {
      assertRefused(
          () -> service.add(new AddMemberCommand(caller, ORGANIZATION, "Rita", "r@x.pt")),
          NotAllowedException.class,
          "ADMINISTRATOR_ONLY");
      assertRefused(
          () -> service.membersByOrganization(caller),
          NotAllowedException.class,
          "ADMINISTRATOR_ONLY");
    }
    verify(users, never()).add(any());
  }

  @Test
  void deactivatesAMemberAndRevokesTheirTokens() {
    UserAccount member = givenMember(true, false);

    MemberChanged changed =
        service.change(new ChangeMemberCommand(ADMIN, member.getId(), false, false));

    assertThat(changed.active()).isFalse();
    assertThat(changed.temporaryPassword()).isNull();
    assertThat(changed.expiresAt()).isNull();
    assertThat(stored().isActive()).isFalse();
    verify(revocations).revokeIssuedBefore(member.getId(), NOW);
    verifyAudited("MEMBER_DEACTIVATED", member.getId());
  }

  @Test
  void reactivatesAMemberWithoutRevokingAnything() {
    UserAccount member = givenMember(false, false);

    MemberChanged changed =
        service.change(new ChangeMemberCommand(ADMIN, member.getId(), true, false));

    assertThat(changed.active()).isTrue();
    assertThat(stored().getPasswordHash()).isEqualTo(member.getPasswordHash());
    verify(revocations, never()).revokeIssuedBefore(any(), any());
    verifyAudited("MEMBER_REACTIVATED", member.getId());
  }

  @Test
  void issuesANewTemporaryPasswordAndRevokesTheTokens() {
    UserAccount member = givenMember(true, false);

    MemberChanged changed =
        service.change(new ChangeMemberCommand(ADMIN, member.getId(), null, true));

    assertThat(changed.active()).isTrue();
    assertThat(changed.temporaryPassword()).isEqualTo("AAA-AAA-AAAA");
    assertThat(changed.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    UserAccount stored = stored();
    assertThat(stored.getPasswordHash()).isEqualTo("hash:AAA-AAA-AAAA");
    assertThat(stored.isMustChangePassword()).isTrue();
    verify(revocations).revokeIssuedBefore(member.getId(), NOW);
    verifyAudited("MEMBER_PASSWORD_RESET", member.getId());
  }

  @Test
  void changesNothingThatIsAlreadySo() {
    UserAccount member = givenMember(true, false);

    MemberChanged changed =
        service.change(new ChangeMemberCommand(ADMIN, member.getId(), true, false));

    assertThat(changed.active()).isTrue();
    verify(audit, never()).record(any(), any(), anyString(), anyString(), any(), anyMap());
    verify(revocations, never()).revokeIssuedBefore(any(), any());
  }

  @Test
  void refusesAUserWhoIsNotAMember() {
    UUID participant = UUID.randomUUID();
    when(users.findAccountById(participant))
        .thenReturn(
            Optional.of(
                UserAccount.restore(
                    participant,
                    null,
                    Role.PARTICIPANT,
                    null,
                    new Email("ana@x.pt"),
                    "h",
                    true,
                    false,
                    null,
                    null,
                    NOW)));

    assertRefused(
        () -> service.change(new ChangeMemberCommand(ADMIN, participant, false, false)),
        NotFoundException.class,
        "MEMBER_NOT_FOUND");
    assertRefused(
        () -> service.change(new ChangeMemberCommand(ADMIN, UUID.randomUUID(), false, false)),
        NotFoundException.class,
        "MEMBER_NOT_FOUND");
  }

  @Test
  void failsWhenTheTokensCannotBeRevoked() {
    UserAccount member = givenMember(true, false);
    doThrow(new UnavailableException("REVOCATIONS_UNAVAILABLE", "Down."))
        .when(revocations)
        .revokeIssuedBefore(any(), any());

    assertRefused(
        () -> service.change(new ChangeMemberCommand(ADMIN, member.getId(), false, false)),
        UnavailableException.class,
        "REVOCATIONS_UNAVAILABLE");
  }

  @Test
  void listsTheMembersByOrganization() {
    UserAccount member = givenMember(true, true);
    when(users.findMembers()).thenReturn(List.of(member));

    Map<UUID, List<MemberOverview>> members = service.membersByOrganization(ADMIN);

    assertThat(members)
        .containsOnlyKeys(ORGANIZATION)
        .extractingByKey(ORGANIZATION)
        .satisfies(
            list ->
                assertThat(list)
                    .singleElement()
                    .satisfies(
                        m -> {
                          assertThat(m.userId()).isEqualTo(member.getId());
                          assertThat(m.name()).isEqualTo("Rita Ferreira");
                          assertThat(m.mustChangePassword()).isTrue();
                        }));
  }

  private UserAccount givenMember(boolean active, boolean mustChangePassword) {
    UserAccount member =
        UserAccount.restore(
            UUID.randomUUID(),
            ORGANIZATION,
            Role.ORGANIZATION_MEMBER,
            "Rita Ferreira",
            new Email("rita@ribeira.pt"),
            "hash:old",
            active,
            mustChangePassword,
            mustChangePassword ? NOW.plus(Duration.ofDays(1)) : null,
            null,
            NOW.minus(Duration.ofDays(30)));
    when(users.findAccountById(member.getId())).thenReturn(Optional.of(member));
    return member;
  }

  private UserAccount stored() {
    ArgumentCaptor<UserAccount> stored = ArgumentCaptor.forClass(UserAccount.class);
    verify(users).update(stored.capture());
    return stored.getValue();
  }

  private void verifyAudited(String action, UUID memberId) {
    verify(audit).record(ADMIN, null, action, "User", memberId, Map.of());
    assertThat(meters.counter("crowded.admin.actions", "action", action).count()).isOne();
  }

  private static void assertRefused(
      ThrowingCallable call, Class<? extends DomainException> type, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(type, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
