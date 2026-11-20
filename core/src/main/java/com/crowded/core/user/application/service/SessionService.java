package com.crowded.core.user.application.service;

import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.user.application.dto.SignOutCommand;
import com.crowded.core.user.application.dto.SignedIn;
import com.crowded.core.user.domain.model.RefreshToken;
import com.crowded.core.user.domain.model.Session;
import com.crowded.core.user.domain.model.SessionLifetimes;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.domain.repository.Sessions;
import com.crowded.core.user.domain.repository.TokenIssuer;
import com.crowded.core.user.domain.repository.TokenRevocations;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Starts, continues and ends sessions (ADR 0021): a short-lived access token, and a refresh token
 * that changes on each use and lasts as long as the user's role allows.
 */
@Service
public class SessionService {

  private static final Logger log = LoggerFactory.getLogger(SessionService.class);

  private final Sessions sessions;
  private final SessionLifetimes lifetimes;
  private final TokenIssuer tokens;
  private final TokenRevocations revocations;
  private final UserRepository users;
  private final Clock clock;
  private final MeterRegistry meters;
  private final RandomGenerator random;

  @Autowired
  SessionService(
      Sessions sessions,
      SessionLifetimes lifetimes,
      TokenIssuer tokens,
      TokenRevocations revocations,
      UserRepository users,
      Clock clock,
      MeterRegistry meters) {
    this(sessions, lifetimes, tokens, revocations, users, clock, meters, new SecureRandom());
  }

  SessionService(
      Sessions sessions,
      SessionLifetimes lifetimes,
      TokenIssuer tokens,
      TokenRevocations revocations,
      UserRepository users,
      Clock clock,
      MeterRegistry meters,
      RandomGenerator random) {
    this.sessions = sessions;
    this.lifetimes = lifetimes;
    this.tokens = tokens;
    this.revocations = revocations;
    this.users = users;
    this.clock = clock;
    this.meters = meters;
    this.random = random;
  }

  /** Starts a session for {@code account}, which has just signed in. */
  public SignedIn start(UserAccount account) {
    RefreshToken refresh = RefreshToken.generate(UUID.randomUUID(), random);
    Session session =
        Session.start(
            refresh, account.getId(), lifetimes.forRole(account.getRole()), clock.instant());
    sessions.save(session);
    count("started");
    return signedIn(account, session, refresh);
  }

  /**
   * Continues the session {@code refreshToken} belongs to, with new tokens. They are issued only if
   * no other refresh stored the session meanwhile, as two tabs may refresh at once.
   *
   * @throws NotAuthenticatedException when the session ended or the token does not continue it, or
   *     with {@code REFRESH_TOKEN_ROTATED} when another request has just refreshed it
   */
  public SignedIn refresh(String refreshToken) {
    RefreshToken presented =
        RefreshToken.parse(refreshToken).orElseThrow(() -> refused(Session.invalidToken()));
    Session session =
        sessions.findById(presented.sessionId()).orElseThrow(() -> refused(Session.invalidToken()));
    RefreshToken next = RefreshToken.generate(session.getId(), random);
    String readTokenHash = session.getTokenHash();
    try {
      session.refresh(presented, next, clock.instant());
    } catch (NotAuthenticatedException e) {
      if (session.hasEnded()) {
        sessions.save(session);
        log.warn("Ended session {}: expired or its refresh token was reused", session.getId());
      }
      throw refused(e);
    }
    UserAccount account =
        users
            .findAccountById(session.getUserId())
            .orElseThrow(() -> refused(Session.invalidToken()));
    account.continueSession();
    if (!sessions.replace(session, readTokenHash)) {
      throw refused(Session.refreshedMeanwhile());
    }
    count("refreshed");
    return signedIn(account, session, next);
  }

  /**
   * Ends the session of {@code command}'s refresh token, and revokes its access token until it
   * expires. A token that names no session is ignored, so signing out always succeeds.
   */
  public void signOut(SignOutCommand command) {
    if (command.accessTokenId() != null && command.accessExpiresAt() != null) {
      revocations.revokeToken(command.accessTokenId(), command.accessExpiresAt());
    }
    RefreshToken.parse(command.refreshToken())
        .ifPresent(
            presented ->
                sessions
                    .findById(presented.sessionId())
                    .filter(session -> session.end(presented))
                    .ifPresent(
                        session -> {
                          sessions.save(session);
                          log.info("User {} signed out", session.getUserId());
                        }));
    count("signed-out");
  }

  private SignedIn signedIn(UserAccount account, Session session, RefreshToken refresh) {
    return new SignedIn(
        tokens.issue(account.getId()), refresh.value(), session.expiresAt(), account.user(), false);
  }

  private NotAuthenticatedException refused(NotAuthenticatedException refusal) {
    count(refusal.getCode().equals("REFRESH_TOKEN_ROTATED") ? "rotated" : "refused");
    return refusal;
  }

  private void count(String outcome) {
    Counter.builder("crowded.auth.sessions")
        .description("Sessions started, refreshed, refused and signed out")
        .tag("outcome", outcome)
        .register(meters)
        .increment();
  }
}
