package com.crowded.core.user.application.service;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.domain.exception.TooManyRequestsException;
import com.crowded.core.shared.domain.repository.RateLimiter;
import com.crowded.core.user.application.dto.RegisterCommand;
import com.crowded.core.user.application.dto.SignInCommand;
import com.crowded.core.user.application.dto.SignedIn;
import com.crowded.core.user.domain.model.Email;
import com.crowded.core.user.domain.model.Password;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.domain.repository.PasswordHasher;
import com.crowded.core.user.domain.repository.TokenIssuer;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers participants and signs users in. Passwords are stored only as a hash with salt;
 * registration and sign-in are rate-limited. A refused sign-in says the same whatever was wrong, so
 * it does not reveal which emails have an account.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private static final Duration SIGN_IN_WINDOW = Duration.ofMinutes(15);
  private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);

  private final UserRepository users;
  private final PasswordHasher passwords;
  private final TokenIssuer tokens;
  private final RateLimiter limiter;
  private final Clock clock;
  private final MeterRegistry meters;
  private final int signInsPerEmail;
  private final int signInsPerAddress;
  private final int registrationsPerAddress;

  /** Hash compared against when the email has no account, so both take as long. */
  private final String unknownAccountHash;

  AuthService(
      UserRepository users,
      PasswordHasher passwords,
      TokenIssuer tokens,
      RateLimiter limiter,
      Clock clock,
      MeterRegistry meters,
      @Value("${crowded.rate-limits.sign-ins-per-email:10}") int signInsPerEmail,
      @Value("${crowded.rate-limits.sign-ins-per-address:30}") int signInsPerAddress,
      @Value("${crowded.rate-limits.registrations-per-address:10}") int registrationsPerAddress) {
    this.users = users;
    this.passwords = passwords;
    this.tokens = tokens;
    this.limiter = limiter;
    this.clock = clock;
    this.meters = meters;
    this.signInsPerEmail = signInsPerEmail;
    this.signInsPerAddress = signInsPerAddress;
    this.registrationsPerAddress = registrationsPerAddress;
    this.unknownAccountHash = passwords.hash(UUID.randomUUID().toString());
  }

  /**
   * Creates a participant account. Nobody can register as an organization member or an
   * administrator: their accounts are created by an administrator.
   *
   * @throws RuleViolationException when the password is too short or too long, or the email is
   *     invalid
   * @throws ConflictException when the email already has an account
   * @throws TooManyRequestsException when the client registered too often
   */
  @Transactional
  public User register(RegisterCommand command) {
    limit(
        "registration:address:" + command.clientAddress(),
        registrationsPerAddress,
        REGISTRATION_WINDOW,
        "register");
    Password password = new Password(command.password());
    Email email = new Email(command.email());
    UserAccount account =
        UserAccount.registerParticipant(email, passwords.hash(password.value()), clock.instant());
    users.add(account);
    count("crowded.auth.registrations", "Participant accounts registered", "registered");
    log.info("Registered participant {}", account.getId());
    return account.user();
  }

  /**
   * Signs a user in with their email and password.
   *
   * @throws NotAuthenticatedException when the email and password do not match an active account
   * @throws NotAllowedException when the account still has its temporary password
   * @throws TooManyRequestsException when the client or the email had too many attempts
   */
  @Transactional
  public SignedIn signIn(SignInCommand command) {
    String normalized = Email.normalize(command.email());
    limit(
        "sign-in:address:" + command.clientAddress(), signInsPerAddress, SIGN_IN_WINDOW, "sign-in");
    limit("sign-in:email:" + sha256(normalized), signInsPerEmail, SIGN_IN_WINDOW, "sign-in");

    Optional<UserAccount> found = Email.parse(normalized).flatMap(users::findAccountByEmail);
    String password = command.password() == null ? "" : command.password();
    // Hashes even for an unknown email, so the answer takes as long either way.
    boolean matches =
        passwords.matches(
            password, found.map(UserAccount::getPasswordHash).orElse(unknownAccountHash));
    UserAccount account = found.orElseThrow(() -> refused(UserAccount.invalidCredentials()));
    try {
      account.signIn(matches, clock.instant());
    } catch (NotAuthenticatedException e) {
      throw refused(e);
    } catch (NotAllowedException e) {
      count("crowded.auth.sign-ins", "Sign-in attempts", "password-change-required");
      throw e;
    }
    users.saveSignIn(account);
    count("crowded.auth.sign-ins", "Sign-in attempts", "signed-in");
    log.info("User {} signed in", account.getId());
    return new SignedIn(tokens.issue(account.getId()), account.user(), false);
  }

  private NotAuthenticatedException refused(NotAuthenticatedException refusal) {
    count("crowded.auth.sign-ins", "Sign-in attempts", "refused");
    log.warn("Refused a sign-in: wrong credentials or deactivated account");
    return refusal;
  }

  private void limit(String key, int limit, Duration window, String action) {
    if (!limiter.tryAcquire(key, limit, window)) {
      Counter.builder("crowded.auth.rate-limited")
          .description("Registrations and sign-ins refused by the rate limit")
          .tag("action", action)
          .register(meters)
          .increment();
      log.warn("Rate-limited a {} attempt", action);
      throw new TooManyRequestsException(
          "RATE_LIMITED", "Too many attempts. Try again in a few minutes.");
    }
  }

  /** The email as a key without the email itself, which is personal data. */
  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }

  private void count(String name, String description, String outcome) {
    Counter.builder(name)
        .description(description)
        .tag("outcome", outcome)
        .register(meters)
        .increment();
  }
}
