package com.crowded.core.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.user.application.dto.SignOutCommand;
import com.crowded.core.user.application.dto.SignedIn;
import com.crowded.core.user.domain.model.Email;
import com.crowded.core.user.domain.model.IssuedToken;
import com.crowded.core.user.domain.model.RefreshToken;
import com.crowded.core.user.domain.model.Session;
import com.crowded.core.user.domain.model.SessionLifetime;
import com.crowded.core.user.domain.model.SessionLifetimes;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.domain.repository.Sessions;
import com.crowded.core.user.domain.repository.TokenIssuer;
import com.crowded.core.user.domain.repository.TokenRevocations;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionServiceTest {

  /** The lifetimes of application.yml. */
  static final SessionLifetimes LIFETIMES =
      new SessionLifetimes(
          new SessionLifetime(Duration.ofDays(30), Duration.ofDays(90)),
          new SessionLifetime(Duration.ofHours(12), Duration.ofDays(7)));

  private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

  private final InMemorySessions sessions = new InMemorySessions();
  private final TokenIssuer tokens = mock(TokenIssuer.class);
  private final TokenRevocations revocations = mock(TokenRevocations.class);
  private final UserRepository users = mock(UserRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final UserAccount participant =
      UserAccount.registerParticipant(new Email("ines@exemplo.pt"), "hash", NOW);
  private Instant now = NOW;
  private SessionService service;

  @BeforeEach
  void createTheService() {
    Clock clock =
        new Clock() {
          @Override
          public Instant instant() {
            return now;
          }

          @Override
          public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }
        };
    when(tokens.issue(any())).thenAnswer(call -> new IssuedToken("jwt", now.plusSeconds(900)));
    when(users.findAccountById(participant.getId())).thenReturn(Optional.of(participant));
    service =
        new SessionService(
            sessions, LIFETIMES, tokens, revocations, users, clock, meters, new Random(7));
  }

  @Test
  void startsASessionThatLastsAsLongAsTheRoleAllows() {
    SignedIn signedIn = service.start(participant);

    Session stored = sessions.only();
    assertThat(stored.getUserId()).isEqualTo(participant.getId());
    assertThat(RefreshToken.parse(signedIn.refreshToken()))
        .hasValueSatisfying(token -> assertThat(token.hash()).isEqualTo(stored.getTokenHash()));
    assertThat(signedIn.refreshExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    assertThat(signedIn.token().value()).isEqualTo("jwt");
    assertThat(signedIn.user()).isEqualTo(participant.user());
    assertThat(meters.counter("crowded.auth.sessions", "outcome", "started").count()).isOne();
  }

  @Test
  void continuesTheSessionWithNewTokens() {
    SignedIn first = service.start(participant);
    now = NOW.plus(Duration.ofDays(29));

    SignedIn second = service.refresh(first.refreshToken());

    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    assertThat(second.refreshExpiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
    assertThat(second.token().expiresAt()).isEqualTo(now.plusSeconds(900));
    assertThat(sessions.only().getTokenHash())
        .isEqualTo(RefreshToken.parse(second.refreshToken()).orElseThrow().hash());
    assertThat(meters.counter("crowded.auth.sessions", "outcome", "refreshed").count()).isOne();
  }

  @Test
  void refusesATokenThatContinuesNoSession() {
    assertRefused(() -> service.refresh(null), "INVALID_REFRESH_TOKEN");
    assertRefused(() -> service.refresh("lixo"), "INVALID_REFRESH_TOKEN");
    assertRefused(
        () -> service.refresh(RefreshToken.generate(UUID.randomUUID(), new Random()).value()),
        "INVALID_REFRESH_TOKEN");
    assertThat(meters.counter("crowded.auth.sessions", "outcome", "refused").count()).isEqualTo(3);
  }

  @Test
  void endsTheSessionWhenAnOldTokenComesBack() {
    SignedIn first = service.start(participant);
    SignedIn second = service.refresh(first.refreshToken());

    now = NOW.plusSeconds(5);
    assertRefused(() -> service.refresh(first.refreshToken()), "REFRESH_TOKEN_ROTATED");
    assertThat(sessions.all()).hasSize(1);

    now = NOW.plusSeconds(60);
    assertRefused(() -> service.refresh(first.refreshToken()), "INVALID_REFRESH_TOKEN");
    assertThat(sessions.all()).isEmpty();
    assertRefused(() -> service.refresh(second.refreshToken()), "INVALID_REFRESH_TOKEN");
  }

  @Test
  void letsOnlyOneOfTwoRefreshesAtOnceWin() {
    SignedIn first = service.start(participant);
    String[] winner = new String[1];
    sessions.meanwhile = () -> winner[0] = service.refresh(first.refreshToken()).refreshToken();

    assertRefused(() -> service.refresh(first.refreshToken()), "REFRESH_TOKEN_ROTATED");

    // Tokens for the sign-in and the winner only; the winner's refresh token is stored.
    verify(tokens, org.mockito.Mockito.times(2)).issue(any());
    assertThat(sessions.only().getTokenHash())
        .isEqualTo(RefreshToken.parse(winner[0]).orElseThrow().hash());
    assertThat(meters.counter("crowded.auth.sessions", "outcome", "rotated").count()).isOne();
  }

  @Test
  void issuesNothingForASessionEndedMeanwhile() {
    SignedIn signedIn = service.start(participant);
    sessions.meanwhile = () -> sessions.endAllOf(participant.getId());

    assertRefused(() -> service.refresh(signedIn.refreshToken()), "REFRESH_TOKEN_ROTATED");

    assertThat(sessions.all()).isEmpty();
    // Only the sign-in's.
    verify(tokens, org.mockito.Mockito.times(1)).issue(any());
  }

  @Test
  void endsTheSessionOfADeactivatedAccount() {
    SignedIn signedIn = service.start(participant);
    participant.deactivate();

    assertRefused(() -> service.refresh(signedIn.refreshToken()), "INVALID_REFRESH_TOKEN");
    verify(tokens, org.mockito.Mockito.times(1)).issue(any());
  }

  @Test
  void signsOutTheSessionAndItsAccessToken() {
    SignedIn signedIn = service.start(participant);
    Instant accessExpiry = NOW.plusSeconds(900);

    service.signOut(new SignOutCommand(signedIn.refreshToken(), "jti-1", accessExpiry));

    verify(revocations).revokeToken("jti-1", accessExpiry);
    assertThat(sessions.all()).isEmpty();
    assertRefused(() -> service.refresh(signedIn.refreshToken()), "INVALID_REFRESH_TOKEN");
  }

  @Test
  void signsOutQuietlyWithoutASession() {
    service.signOut(new SignOutCommand(null, null, null));
    service.signOut(new SignOutCommand("lixo", null, null));

    verify(revocations, never()).revokeToken(any(), any());
    assertThat(meters.counter("crowded.auth.sessions", "outcome", "signed-out").count())
        .isEqualTo(2);
  }

  @Test
  void keepsASessionWhenSignOutComesWithAnotherToken() {
    SignedIn signedIn = service.start(participant);
    UUID sessionId = RefreshToken.parse(signedIn.refreshToken()).orElseThrow().sessionId();

    service.signOut(
        new SignOutCommand(RefreshToken.generate(sessionId, new Random()).value(), null, null));

    assertThat(sessions.all()).hasSize(1);
  }

  private static void assertRefused(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            NotAuthenticatedException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }

  /** {@link Sessions} in memory, keeping the contract: an ended session is forgotten. */
  private static final class InMemorySessions implements Sessions {

    private final Map<UUID, Session> stored = new HashMap<>();

    /** Runs once before the next replace, as a request that gets there first. */
    private Runnable meanwhile;

    @Override
    public void save(Session session) {
      if (session.hasEnded()) {
        stored.remove(session.getId());
      } else {
        stored.put(session.getId(), copy(session));
      }
    }

    @Override
    public boolean replace(Session session, String readTokenHash) {
      Runnable first = meanwhile;
      meanwhile = null;
      if (first != null) {
        first.run();
      }
      Session current = stored.get(session.getId());
      if (current == null || !current.getTokenHash().equals(readTokenHash)) {
        return false;
      }
      save(session);
      return true;
    }

    @Override
    public Optional<Session> findById(UUID sessionId) {
      return Optional.ofNullable(stored.get(sessionId)).map(InMemorySessions::copy);
    }

    @Override
    public void endAllOf(UUID userId) {
      stored.values().removeIf(session -> session.getUserId().equals(userId));
    }

    Session only() {
      assertThat(stored).hasSize(1);
      return stored.values().iterator().next();
    }

    java.util.Collection<Session> all() {
      return stored.values();
    }

    /** As a store would: each read is a new object. */
    private static Session copy(Session session) {
      return Session.restore(
          session.getId(),
          session.getUserId(),
          session.getStartedAt(),
          session.getEndsAt(),
          session.getIdleLifetime(),
          session.getTokenHash(),
          session.getPreviousTokenHash(),
          session.getRotatedAt(),
          session.getLastUsedAt());
    }
  }
}
