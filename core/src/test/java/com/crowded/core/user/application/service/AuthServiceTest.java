package com.crowded.core.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.DomainException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.domain.exception.TooManyRequestsException;
import com.crowded.core.shared.domain.repository.RateLimiter;
import com.crowded.core.user.application.dto.RegisterCommand;
import com.crowded.core.user.application.dto.SignInCommand;
import com.crowded.core.user.application.dto.SignedIn;
import com.crowded.core.user.domain.model.Email;
import com.crowded.core.user.domain.model.IssuedToken;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.domain.repository.PasswordHasher;
import com.crowded.core.user.domain.repository.TokenIssuer;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
  private static final String ADDRESS = "203.0.113.7";
  private static final String PASSWORD = "uma-frase-longa";

  private final UserRepository users = mock(UserRepository.class);
  private final PasswordHasher passwords = mock(PasswordHasher.class);
  private final TokenIssuer tokens = mock(TokenIssuer.class);
  private final RateLimiter limiter = mock(RateLimiter.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private AuthService service;

  @BeforeEach
  void createTheService() {
    when(passwords.hash(anyString())).thenAnswer(call -> "hash:" + call.getArgument(0));
    when(passwords.matches(anyString(), anyString()))
        .thenAnswer(call -> call.getArgument(1).equals("hash:" + call.getArgument(0)));
    when(limiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    service =
        new AuthService(
            users, passwords, tokens, limiter, Clock.fixed(NOW, ZoneOffset.UTC), meters, 10, 30, 5);
  }

  @Test
  void registersAParticipantWithAHashedPasswordAndALowerCaseEmail() {
    User user = service.register(new RegisterCommand("  Ana@Exemplo.PT ", PASSWORD, ADDRESS));

    ArgumentCaptor<UserAccount> stored = ArgumentCaptor.forClass(UserAccount.class);
    verify(users).add(stored.capture());
    UserAccount account = stored.getValue();
    assertThat(account.getEmail()).isEqualTo(new Email("ana@exemplo.pt"));
    assertThat(account.getPasswordHash()).isEqualTo("hash:" + PASSWORD);
    assertThat(account.getRole()).isEqualTo(Role.PARTICIPANT);
    assertThat(account.getOrganizationId()).isNull();
    assertThat(account.isActive()).isTrue();
    assertThat(account.isMustChangePassword()).isFalse();
    assertThat(account.getCreatedAt()).isEqualTo(NOW);
    assertThat(user).isEqualTo(account.user());
    verify(limiter).tryAcquire("registration:address:" + ADDRESS, 5, Duration.ofHours(1));
    assertThat(meters.counter("crowded.auth.registrations", "outcome", "registered").count())
        .isOne();
  }

  @Test
  void refusesAPasswordThatIsTooShortOrTooLong() {
    assertRefused(
        () -> service.register(new RegisterCommand("a@b.pt", "curta12", ADDRESS)),
        RuleViolationException.class,
        "PASSWORD_TOO_SHORT");
    assertRefused(
        () -> service.register(new RegisterCommand("a@b.pt", null, ADDRESS)),
        RuleViolationException.class,
        "PASSWORD_TOO_SHORT");
    // 37 two-byte characters: 74 bytes, more than bcrypt reads.
    assertRefused(
        () -> service.register(new RegisterCommand("a@b.pt", "ç".repeat(37), ADDRESS)),
        RuleViolationException.class,
        "PASSWORD_TOO_LONG");
    verify(users, never()).add(any());
  }

  @Test
  void refusesAnInvalidEmail() {
    assertRefused(
        () -> service.register(new RegisterCommand("sem-arroba", PASSWORD, ADDRESS)),
        RuleViolationException.class,
        "EMAIL_INVALID");
    verify(users, never()).add(any());
  }

  @Test
  void refusesAnEmailThatAlreadyHasAnAccount() {
    ConflictException taken = new ConflictException("EMAIL_ALREADY_REGISTERED", "Taken.");
    doThrow(taken).when(users).add(any());

    assertRefused(
        () -> service.register(new RegisterCommand("ana@crowded.test", PASSWORD, ADDRESS)),
        ConflictException.class,
        "EMAIL_ALREADY_REGISTERED");
  }

  @Test
  void refusesARegistrationOverTheLimit() {
    when(limiter.tryAcquire(startsWith("registration:"), anyInt(), any())).thenReturn(false);

    assertRefused(
        () -> service.register(new RegisterCommand("a@b.pt", PASSWORD, ADDRESS)),
        TooManyRequestsException.class,
        "RATE_LIMITED");
    verify(users, never()).add(any());
    assertThat(meters.counter("crowded.auth.rate-limited", "action", "register").count()).isOne();
  }

  @Test
  void signsInWithTheRightPasswordWhateverTheEmailsCase() {
    UserAccount member = account(Role.ORGANIZATION_MEMBER, UUID.randomUUID(), true, false);
    when(users.findAccountByEmail(new Email("equipa@ribeira-live.test")))
        .thenReturn(Optional.of(member));
    IssuedToken token = new IssuedToken("jwt", NOW.plus(Duration.ofHours(1)));
    when(tokens.issue(member.getId())).thenReturn(token);

    SignedIn signedIn =
        service.signIn(new SignInCommand("Equipa@Ribeira-Live.test", PASSWORD, ADDRESS));

    assertThat(signedIn.token()).isEqualTo(token);
    assertThat(signedIn.user()).isEqualTo(member.user());
    assertThat(signedIn.mustChangePassword()).isFalse();
    assertThat(member.getLastSignedInAt()).isEqualTo(NOW);
    verify(users).saveSignIn(member);
    assertThat(meters.counter("crowded.auth.sign-ins", "outcome", "signed-in").count()).isOne();
  }

  @Test
  void refusesAWrongPasswordAnUnknownEmailAndADeactivatedAccountAlike() {
    UserAccount participant = account(Role.PARTICIPANT, null, true, false);
    UserAccount deactivated = account(Role.ORGANIZATION_MEMBER, UUID.randomUUID(), false, false);
    when(users.findAccountByEmail(new Email("ana@crowded.test")))
        .thenReturn(Optional.of(participant));
    when(users.findAccountByEmail(new Email("antigo@ribeira-live.test")))
        .thenReturn(Optional.of(deactivated));

    String wrongPassword =
        refusal(() -> service.signIn(new SignInCommand("ana@crowded.test", "errada", ADDRESS)));
    String unknownEmail =
        refusal(() -> service.signIn(new SignInCommand("ninguem@crowded.test", PASSWORD, ADDRESS)));
    String deactivatedAccount =
        refusal(
            () -> service.signIn(new SignInCommand("antigo@ribeira-live.test", PASSWORD, ADDRESS)));
    String notAnEmail =
        refusal(() -> service.signIn(new SignInCommand("sem-arroba", PASSWORD, ADDRESS)));

    assertThat(wrongPassword)
        .isEqualTo(unknownEmail)
        .isEqualTo(deactivatedAccount)
        .isEqualTo(notAnEmail);
    verify(users, never()).saveSignIn(any());
    verify(tokens, never()).issue(any());
    // An unknown or invalid email is still compared against a hash, so it takes as long as a
    // wrong password.
    verify(passwords, times(2))
        .matches(eq(PASSWORD), argThat(hash -> !hash.equals("hash:" + PASSWORD)));
    assertThat(meters.counter("crowded.auth.sign-ins", "outcome", "refused").count()).isEqualTo(4);
  }

  @Test
  void refusesAMissingPasswordWithoutFailing() {
    when(users.findAccountByEmail(new Email("ana@crowded.test")))
        .thenReturn(Optional.of(account(Role.PARTICIPANT, null, true, false)));

    assertRefused(
        () -> service.signIn(new SignInCommand("ana@crowded.test", null, ADDRESS)),
        NotAuthenticatedException.class,
        "INVALID_CREDENTIALS");
  }

  @Test
  void refusesASignInWithATemporaryPasswordUntilItIsChanged() {
    UserAccount member = account(Role.ORGANIZATION_MEMBER, UUID.randomUUID(), true, true);
    when(users.findAccountByEmail(new Email("novo@ribeira-live.test")))
        .thenReturn(Optional.of(member));

    assertRefused(
        () -> service.signIn(new SignInCommand("novo@ribeira-live.test", PASSWORD, ADDRESS)),
        NotAllowedException.class,
        "PASSWORD_CHANGE_REQUIRED");
    verify(tokens, never()).issue(any());
    assertThat(
            meters.counter("crowded.auth.sign-ins", "outcome", "password-change-required").count())
        .isOne();
  }

  @Test
  void limitsSignInsPerAddressAndPerEmailWithoutStoringTheEmail() {
    when(limiter.tryAcquire(startsWith("sign-in:email:"), anyInt(), any())).thenReturn(false);

    assertRefused(
        () -> service.signIn(new SignInCommand("ana@crowded.test", PASSWORD, ADDRESS)),
        TooManyRequestsException.class,
        "RATE_LIMITED");

    verify(limiter).tryAcquire("sign-in:address:" + ADDRESS, 30, Duration.ofMinutes(15));
    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    verify(limiter).tryAcquire(key.capture(), eq(10), eq(Duration.ofMinutes(15)));
    assertThat(key.getValue()).startsWith("sign-in:email:").doesNotContain("ana");
    verify(users, never()).findAccountByEmail(any());
  }

  private UserAccount account(
      Role role, UUID organization, boolean active, boolean mustChangePassword) {
    return UserAccount.restore(
        UUID.randomUUID(),
        organization,
        role,
        role == Role.ORGANIZATION_MEMBER ? "Rita" : null,
        new Email("x@crowded.test"),
        "hash:" + PASSWORD,
        active,
        mustChangePassword,
        mustChangePassword ? NOW.plus(UserAccount.TEMPORARY_PASSWORD_LIFETIME) : null,
        null,
        NOW);
  }

  /** The refusal's code and message, to compare refusals. */
  private static String refusal(ThrowingCallable call) {
    try {
      call.call();
    } catch (NotAuthenticatedException e) {
      return e.getCode() + ": " + e.getMessage();
    } catch (Throwable e) {
      throw new AssertionError("Expected a refused sign-in", e);
    }
    throw new AssertionError("Expected a refused sign-in");
  }

  private static void assertRefused(
      ThrowingCallable call, Class<? extends DomainException> type, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(type, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
