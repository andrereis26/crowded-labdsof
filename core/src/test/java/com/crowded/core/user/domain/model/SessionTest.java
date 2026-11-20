package com.crowded.core.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class SessionTest {

  private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final SessionLifetime LIFETIME =
      new SessionLifetime(Duration.ofHours(12), Duration.ofDays(7));
  private static final RandomGenerator RANDOM = new java.util.Random(42);

  private final UUID id = UUID.randomUUID();
  private final RefreshToken first = RefreshToken.generate(id, RANDOM);
  private final RefreshToken second = RefreshToken.generate(id, RANDOM);
  private final RefreshToken third = RefreshToken.generate(id, RANDOM);

  @Test
  void startsWithTheTokensHashAndItsLifetime() {
    Session session = Session.start(first, USER, LIFETIME, NOW);

    assertThat(session.getId()).isEqualTo(id);
    assertThat(session.getUserId()).isEqualTo(USER);
    assertThat(session.getTokenHash()).isEqualTo(first.hash()).doesNotContain(first.secret());
    assertThat(session.getEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
    assertThat(session.hasEnded()).isFalse();
  }

  @Test
  void replacesTheTokenOnEachUseAndStaysAliveWhileUsed() {
    Session session = Session.start(first, USER, LIFETIME, NOW);
    Instant later = NOW.plus(Duration.ofHours(11));

    session.refresh(first, second, later);

    assertThat(session.getTokenHash()).isEqualTo(second.hash());
    assertThat(session.getPreviousTokenHash()).isEqualTo(first.hash());
    assertThat(session.getRotatedAt()).isEqualTo(later);
    assertThat(session.expiresAt()).isEqualTo(later.plus(Duration.ofHours(12)));
  }

  @Test
  void neverOutlivesItsMaximumAge() {
    SessionLifetime lasting = new SessionLifetime(Duration.ofDays(5), Duration.ofDays(7));
    Session session = Session.start(first, USER, lasting, NOW);

    session.refresh(first, second, NOW.plus(Duration.ofDays(4)));

    assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    assertRefused(
        () -> session.refresh(second, third, NOW.plus(Duration.ofDays(7))),
        "INVALID_REFRESH_TOKEN");
    assertThat(session.hasEnded()).isTrue();
  }

  @Test
  void endsWhenUnusedForTooLong() {
    Session session = Session.start(first, USER, LIFETIME, NOW);

    assertRefused(
        () -> session.refresh(first, second, NOW.plus(Duration.ofHours(12))),
        "INVALID_REFRESH_TOKEN");
    assertThat(session.hasEnded()).isTrue();
  }

  @Test
  void onlyRefusesAReplacedTokenUsedRightAfterTheChange() {
    Session session = Session.start(first, USER, LIFETIME, NOW);
    session.refresh(first, second, NOW);

    assertRefused(() -> session.refresh(first, third, NOW.plusSeconds(9)), "REFRESH_TOKEN_ROTATED");
    assertThat(session.hasEnded()).isFalse();
    assertThat(session.getTokenHash()).isEqualTo(second.hash());
  }

  @Test
  void endsWhenAReplacedTokenIsUsedLater() {
    Session session = Session.start(first, USER, LIFETIME, NOW);
    session.refresh(first, second, NOW);

    assertRefused(
        () -> session.refresh(first, third, NOW.plusSeconds(10)), "INVALID_REFRESH_TOKEN");
    assertThat(session.hasEnded()).isTrue();
    // Whoever held the newer token is signed out too.
    assertRefused(
        () -> session.refresh(second, third, NOW.plusSeconds(11)), "INVALID_REFRESH_TOKEN");
  }

  @Test
  void refusesAnotherSessionsTokenWithoutEnding() {
    Session session = Session.start(first, USER, LIFETIME, NOW);
    RefreshToken stranger = RefreshToken.generate(id, RANDOM);

    assertRefused(() -> session.refresh(stranger, second, NOW), "INVALID_REFRESH_TOKEN");
    assertThat(session.hasEnded()).isFalse();
  }

  @Test
  void endsOnSignOutOnlyWithOneOfItsTokens() {
    Session session = Session.start(first, USER, LIFETIME, NOW);
    session.refresh(first, second, NOW);

    assertThat(session.end(third)).isFalse();
    assertThat(session.end(first)).isTrue();
    assertThat(session.hasEnded()).isTrue();

    Session other = Session.start(first, USER, LIFETIME, NOW);
    assertThat(other.end(first)).isTrue();
  }

  @Test
  void restoresAStoredSession() {
    Instant rotated = NOW.plusSeconds(30);
    Session session =
        Session.restore(
            id,
            USER,
            NOW,
            NOW.plus(Duration.ofDays(7)),
            Duration.ofHours(12),
            "b",
            "a",
            rotated,
            rotated);

    assertThat(session.getStartedAt()).isEqualTo(NOW);
    assertThat(session.getIdleLifetime()).isEqualTo(Duration.ofHours(12));
    assertThat(session.getLastUsedAt()).isEqualTo(rotated);
    assertThat(session).isEqualTo(Session.start(first, USER, LIFETIME, NOW)).hasSameHashCodeAs(id);
    assertThat(session.toString()).contains(id.toString());
  }

  @Test
  void hasLifetimesThatEndAfterTheyStart() {
    assertThatThrownBy(() -> new SessionLifetime(Duration.ZERO, Duration.ofDays(1)))
        .isInstanceOf(RuleViolationException.class);
    assertThatThrownBy(() -> new SessionLifetime(Duration.ofDays(2), Duration.ofDays(1)))
        .isInstanceOf(RuleViolationException.class);

    SessionLifetime participant = new SessionLifetime(Duration.ofDays(30), Duration.ofDays(90));
    SessionLifetimes lifetimes = new SessionLifetimes(participant, LIFETIME);
    assertThat(lifetimes.forRole(Role.PARTICIPANT)).isEqualTo(participant);
    assertThat(lifetimes.forRole(Role.ORGANIZATION_MEMBER)).isEqualTo(LIFETIME);
    assertThat(lifetimes.forRole(Role.ADMINISTRATOR)).isEqualTo(LIFETIME);
  }

  private static void assertRefused(Runnable refresh, String code) {
    assertThatThrownBy(refresh::run)
        .isInstanceOfSatisfying(
            NotAuthenticatedException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
