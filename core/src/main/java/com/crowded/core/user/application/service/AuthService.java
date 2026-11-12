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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers participants and signs users in (FR-039, FR-043). Passwords are stored only as a hash
 * with salt (SR-013); registration and sign-in are rate-limited (SR-015). A refused sign-in says
 * the same whatever was wrong, so it does not reveal which emails have an account.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  static final int MIN_PASSWORD_LENGTH = 8;

  /** bcrypt uses only the first 72 bytes of a password. */
  static final int MAX_PASSWORD_BYTES = 72;

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
   * administrator: their accounts are created by an administrator (FR-087).
   *
   * @throws RuleViolationException when the password is too short or too long
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
    checkPassword(command.password());
    String email = normalized(command.email());
    UserAccount account =
        UserAccount.newParticipant(
            UUID.randomUUID(), email, passwords.hash(command.password()), clock.instant());
    users.add(account);
    count("crowded.auth.registrations", "Participant accounts registered", "registered");
    log.info("Registered participant {}", account.id());
    return account.user();
  }

  /**
   * Signs a user in with their email and password.
   *
   * @throws NotAuthenticatedException when the email and password do not match an active account
   * @throws NotAllowedException when the account still has its temporary password; #141 lets it
   *     sign in to set a new one
   * @throws TooManyRequestsException when the client or the email had too many attempts
   */
  @Transactional(readOnly = true)
  public SignedIn signIn(SignInCommand command) {
    String email = normalized(command.email());
    limit(
        "sign-in:address:" + command.clientAddress(), signInsPerAddress, SIGN_IN_WINDOW, "sign-in");
    limit("sign-in:email:" + sha256(email), signInsPerEmail, SIGN_IN_WINDOW, "sign-in");

    Optional<UserAccount> found = users.findAccountByEmail(email);
    String password = command.password() == null ? "" : command.password();
    boolean matches =
        passwords.matches(
            password, found.map(UserAccount::passwordHash).orElse(unknownAccountHash));
    if (found.isEmpty() || !matches || !found.get().active()) {
      count("crowded.auth.sign-ins", "Sign-in attempts", "refused");
      log.warn("Refused a sign-in: wrong credentials or deactivated account");
      throw new NotAuthenticatedException(
          "INVALID_CREDENTIALS", "The email or the password is wrong.");
    }
    UserAccount account = found.get();
    if (account.mustChangePassword()) {
      count("crowded.auth.sign-ins", "Sign-in attempts", "password-change-required");
      throw new NotAllowedException(
          "PASSWORD_CHANGE_REQUIRED", "Set a new password before signing in.");
    }
    count("crowded.auth.sign-ins", "Sign-in attempts", "signed-in");
    log.info("User {} signed in", account.id());
    return new SignedIn(tokens.issue(account.id()), account.user(), false);
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

  private static void checkPassword(String password) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      throw new RuleViolationException(
          "PASSWORD_TOO_SHORT", "The password needs at least 8 characters.");
    }
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new RuleViolationException(
          "PASSWORD_TOO_LONG", "The password can have at most 72 bytes.");
    }
  }

  /** Emails are stored and compared in lower case (app_user_email_lower_case). */
  private static String normalized(String email) {
    return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
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
