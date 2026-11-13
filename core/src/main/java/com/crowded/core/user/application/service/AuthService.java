package com.crowded.core.user.application.service;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.domain.exception.TooManyRequestsException;
import com.crowded.core.shared.domain.repository.RateLimiter;
import com.crowded.core.user.application.dto.ChoosePasswordCommand;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers participants, signs users in, and lets a member replace their temporary password.
 * Passwords are stored only as a hash with salt; registration and sign-in are rate-limited. A
 * refused sign-in says the same whatever was wrong, so it does not reveal which emails have an
 * account.
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
  private final AuditLog audit;
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
      AuditLog audit,
      @Value("${crowded.rate-limits.sign-ins-per-email:10}") int signInsPerEmail,
      @Value("${crowded.rate-limits.sign-ins-per-address:30}") int signInsPerAddress,
      @Value("${crowded.rate-limits.registrations-per-address:10}") int registrationsPerAddress) {
    this.users = users;
    this.passwords = passwords;
    this.tokens = tokens;
    this.limiter = limiter;
    this.clock = clock;
    this.meters = meters;
    this.audit = audit;
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
    Optional<UserAccount> found = findLimited(command.email(), command.clientAddress());
    String password = command.password() == null ? "" : command.password();
    boolean matches = matches(password, found);
    UserAccount account = found.orElseThrow(() -> refused(UserAccount.invalidCredentials()));
    try {
      account.signIn(matches, clock.instant());
    } catch (NotAuthenticatedException e) {
      throw refused(e);
    } catch (NotAllowedException e) {
      count("crowded.auth.sign-ins", "Sign-in attempts", outcome(e));
      throw e;
    }
    users.saveSignIn(account);
    count("crowded.auth.sign-ins", "Sign-in attempts", "signed-in");
    log.info("User {} signed in", account.getId());
    return new SignedIn(tokens.issue(account.getId()), account.user(), false);
  }

  /**
   * Replaces a member's temporary password with one they chose, and signs them in. It shares the
   * sign-in's rate limits, since it also checks a password.
   *
   * @throws NotAuthenticatedException when the email and temporary password do not match an active
   *     account
   * @throws NotAllowedException when the account has no temporary password, or it expired
   * @throws RuleViolationException when the new password breaks the rule or is the temporary one
   * @throws TooManyRequestsException when the client or the email had too many attempts
   */
  @Transactional
  public SignedIn replaceTemporaryPassword(ChoosePasswordCommand command) {
    // Checked before the account, so the answer does not reveal whether the email has one.
    Password chosen = new Password(command.newPassword());
    Optional<UserAccount> found = findLimited(command.email(), command.clientAddress());
    String temporary = command.temporaryPassword() == null ? "" : command.temporaryPassword();
    boolean matches = matches(temporary, found);
    UserAccount account = found.orElseThrow(() -> refused(UserAccount.invalidCredentials()));
    try {
      account.replaceTemporaryPassword(
          matches,
          chosen.value().equals(temporary),
          passwords.hash(chosen.value()),
          clock.instant());
    } catch (NotAuthenticatedException e) {
      throw refused(e);
    } catch (NotAllowedException e) {
      count("crowded.auth.sign-ins", "Sign-in attempts", outcome(e));
      throw e;
    }
    users.savePasswordChange(account);
    audit.record(
        account.getId(), null, "MEMBER_PASSWORD_CHOSEN", "User", account.getId(), Map.of());
    count("crowded.auth.sign-ins", "Sign-in attempts", "password-chosen");
    log.info("User {} replaced the temporary password and signed in", account.getId());
    return new SignedIn(tokens.issue(account.getId()), account.user(), false);
  }

  /** The account with this email, after the sign-in's rate limits per address and per email. */
  private Optional<UserAccount> findLimited(String email, String clientAddress) {
    String normalized = Email.normalize(email);
    limit("sign-in:address:" + clientAddress, signInsPerAddress, SIGN_IN_WINDOW, "sign-in");
    limit("sign-in:email:" + sha256(normalized), signInsPerEmail, SIGN_IN_WINDOW, "sign-in");
    return Email.parse(normalized).flatMap(users::findAccountByEmail);
  }

  /** Hashes even for an unknown email, so the answer takes as long either way. */
  private boolean matches(String password, Optional<UserAccount> account) {
    return passwords.matches(
        password, account.map(UserAccount::getPasswordHash).orElse(unknownAccountHash));
  }

  private static String outcome(NotAllowedException refusal) {
    return switch (refusal.getCode()) {
      case "TEMPORARY_PASSWORD_EXPIRED" -> "temporary-password-expired";
      case "NO_TEMPORARY_PASSWORD" -> "no-temporary-password";
      default -> "password-change-required";
    };
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
