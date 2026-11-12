package com.crowded.core.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final Email EMAIL = new Email("rita@ribeira.pt");

  @Test
  void registersAnActiveParticipantOfNoOrganization() {
    UserAccount participant = UserAccount.registerParticipant(EMAIL, "hash", NOW);

    assertThat(participant.getRole()).isEqualTo(Role.PARTICIPANT);
    assertThat(participant.getOrganizationId()).isNull();
    assertThat(participant.isActive()).isTrue();
    assertThat(participant.hasTemporaryPassword()).isFalse();
    assertThat(participant.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void addsAMemberWithATemporaryPasswordForSevenDays() {
    UserAccount member = UserAccount.addMember(ORGANIZATION, " Rita ", EMAIL, "hash", NOW);

    assertThat(member.getName()).isEqualTo("Rita");
    assertThat(member.isMember()).isTrue();
    assertThat(member.hasTemporaryPassword()).isTrue();
    assertThat(member.getTemporaryPasswordExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
  }

  @Test
  void refusesAMemberWithoutAName() {
    assertThatThrownBy(() -> UserAccount.addMember(ORGANIZATION, "  ", EMAIL, "hash", NOW))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo("NAME_REQUIRED"));
    assertThatThrownBy(() -> UserAccount.addMember(ORGANIZATION, "x".repeat(121), EMAIL, "h", NOW))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo("NAME_TOO_LONG"));
  }

  @Test
  void cannotBeRestoredWithAnOrganizationThatDoesNotFitItsRole() {
    assertThatThrownBy(
            () ->
                UserAccount.restore(
                    UUID.randomUUID(),
                    ORGANIZATION,
                    Role.PARTICIPANT,
                    null,
                    EMAIL,
                    "h",
                    true,
                    false,
                    null,
                    null,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                UserAccount.restore(
                    UUID.randomUUID(),
                    null,
                    Role.ORGANIZATION_MEMBER,
                    "Rita",
                    EMAIL,
                    "h",
                    true,
                    false,
                    null,
                    null,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void signsInWithTheRightPasswordAndRecordsWhen() {
    UserAccount participant = UserAccount.registerParticipant(EMAIL, "hash", NOW);

    participant.signIn(true, NOW.plusSeconds(60));

    assertThat(participant.getLastSignedInAt()).isEqualTo(NOW.plusSeconds(60));
  }

  @Test
  void refusesAWrongPasswordADeactivatedAccountAndATemporaryPassword() {
    UserAccount participant = UserAccount.registerParticipant(EMAIL, "hash", NOW);
    assertThatThrownBy(() -> participant.signIn(false, NOW))
        .isInstanceOf(NotAuthenticatedException.class);

    participant.deactivate();
    assertThatThrownBy(() -> participant.signIn(true, NOW))
        .isInstanceOf(NotAuthenticatedException.class)
        .hasMessage(UserAccount.invalidCredentials().getMessage());

    UserAccount member = UserAccount.addMember(ORGANIZATION, "Rita", EMAIL, "hash", NOW);
    assertThatThrownBy(() -> member.signIn(true, NOW))
        .isInstanceOfSatisfying(
            NotAllowedException.class,
            e -> assertThat(e.getCode()).isEqualTo("PASSWORD_CHANGE_REQUIRED"));
    assertThat(member.getLastSignedInAt()).isNull();
  }

  @Test
  void deactivatesAndReactivatesOnlyWhenItChangesSomething() {
    UserAccount member = UserAccount.addMember(ORGANIZATION, "Rita", EMAIL, "hash", NOW);

    assertThat(member.reactivate()).isFalse();
    assertThat(member.deactivate()).isTrue();
    assertThat(member.isActive()).isFalse();
    assertThat(member.user().isMemberOf(ORGANIZATION)).isFalse();
    assertThat(member.deactivate()).isFalse();
    assertThat(member.reactivate()).isTrue();
    assertThat(member.isActive()).isTrue();
  }

  @Test
  void replacesThePasswordWithATemporaryOne() {
    UserAccount participant = UserAccount.registerParticipant(EMAIL, "old", NOW);

    participant.resetPassword("new", NOW.plusSeconds(10));

    assertThat(participant.getPasswordHash()).isEqualTo("new");
    assertThat(participant.hasTemporaryPassword()).isTrue();
    assertThat(participant.getTemporaryPasswordExpiresAt())
        .isEqualTo(NOW.plusSeconds(10).plus(UserAccount.TEMPORARY_PASSWORD_LIFETIME));
  }

  @Test
  void isTheSameAccountWhateverItsState() {
    UserAccount account = UserAccount.registerParticipant(EMAIL, "hash", NOW);
    UserAccount same =
        UserAccount.restore(
            account.getId(),
            null,
            Role.PARTICIPANT,
            null,
            EMAIL,
            "other",
            false,
            false,
            null,
            null,
            NOW);

    assertThat(account)
        .isEqualTo(same)
        .hasSameHashCodeAs(same)
        .isNotEqualTo(UserAccount.registerParticipant(EMAIL, "hash", NOW));
    assertThat(account.toString()).doesNotContain("hash");
  }
}
