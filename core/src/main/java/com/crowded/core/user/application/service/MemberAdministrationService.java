package com.crowded.core.user.application.service;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.user.application.dto.AddMemberCommand;
import com.crowded.core.user.application.dto.ChangeMemberCommand;
import com.crowded.core.user.application.dto.MemberChanged;
import com.crowded.core.user.application.dto.MemberOverview;
import com.crowded.core.user.application.dto.TemporaryPassword;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.domain.repository.PasswordHasher;
import com.crowded.core.user.domain.repository.TokenRevocations;
import com.crowded.core.user.domain.repository.UserRepository;
import com.crowded.core.user.domain.service.TemporaryPasswords;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An administrator's management of organization members (FR-087): their accounts, created with a
 * temporary password shown once (SR-022), deactivation and new temporary passwords. Every action is
 * audited (SR-006), and the member's earlier tokens are revoked when they lose access.
 */
@Service
public class MemberAdministrationService {

  private static final Logger log = LoggerFactory.getLogger(MemberAdministrationService.class);

  private final AdministratorGuard guard;
  private final UserRepository users;
  private final OrganizationRepository organizations;
  private final PasswordHasher passwords;
  private final TokenRevocations revocations;
  private final AuditLog audit;
  private final Clock clock;
  private final MeterRegistry meters;
  private final RandomGenerator random;

  @Autowired
  MemberAdministrationService(
      AdministratorGuard guard,
      UserRepository users,
      OrganizationRepository organizations,
      PasswordHasher passwords,
      TokenRevocations revocations,
      AuditLog audit,
      Clock clock,
      MeterRegistry meters) {
    this(
        guard,
        users,
        organizations,
        passwords,
        revocations,
        audit,
        clock,
        meters,
        new SecureRandom());
  }

  MemberAdministrationService(
      AdministratorGuard guard,
      UserRepository users,
      OrganizationRepository organizations,
      PasswordHasher passwords,
      TokenRevocations revocations,
      AuditLog audit,
      Clock clock,
      MeterRegistry meters,
      RandomGenerator random) {
    this.guard = guard;
    this.users = users;
    this.organizations = organizations;
    this.passwords = passwords;
    this.revocations = revocations;
    this.audit = audit;
    this.clock = clock;
    this.meters = meters;
    this.random = random;
  }

  /** Every member, by organization, for the administration page. */
  @Transactional(readOnly = true)
  public Map<UUID, List<MemberOverview>> membersByOrganization(UUID administratorId) {
    guard.require(administratorId);
    return users.findMembers().stream()
        .map(MemberAdministrationService::overview)
        .collect(Collectors.groupingBy(MemberOverview::organizationId));
  }

  /**
   * Creates a member account with a temporary password that expires after 7 days.
   *
   * @throws NotFoundException when the organization does not exist
   * @throws ConflictException when the email already has an account
   */
  @Transactional
  public TemporaryPassword add(AddMemberCommand command) {
    guard.require(command.administratorId());
    if (organizations.findById(command.organizationId()).isEmpty()) {
      throw new NotFoundException("ORGANIZATION_NOT_FOUND", "There is no such organization.");
    }
    Instant now = clock.instant();
    String password = TemporaryPasswords.generate(random);
    UserAccount member =
        UserAccount.newMember(
            UUID.randomUUID(),
            command.organizationId(),
            command.name().strip(),
            command.email().strip().toLowerCase(Locale.ROOT),
            passwords.hash(password),
            now);
    users.add(member);
    record(
        command.administratorId(),
        "MEMBER_CREATED",
        member,
        Map.of("organizationId", member.organizationId().toString()));
    log.info(
        "Administrator {} created member {} of organization {}",
        command.administratorId(),
        member.id(),
        member.organizationId());
    return new TemporaryPassword(member.id(), password, member.temporaryPasswordExpiresAt());
  }

  /**
   * Deactivates or reactivates a member, or gives them a new temporary password. Deactivating and a
   * new password revoke the member's earlier tokens.
   *
   * @throws NotFoundException when {@code userId} is not a member
   * @throws UnavailableException when the tokens cannot be revoked; nothing changes then
   */
  @Transactional
  public MemberChanged change(ChangeMemberCommand command) {
    guard.require(command.administratorId());
    UserAccount member =
        users
            .findAccountById(command.userId())
            .filter(UserAccount::isMember)
            .orElseThrow(
                () -> new NotFoundException("MEMBER_NOT_FOUND", "There is no such member."));
    Instant now = clock.instant();
    boolean revoke = false;

    if (command.active() != null && command.active() != member.active()) {
      member = command.active() ? member.reactivated() : member.deactivated();
      revoke = !member.active();
      record(
          command.administratorId(),
          member.active() ? "MEMBER_REACTIVATED" : "MEMBER_DEACTIVATED",
          member,
          Map.of());
    }
    String password = null;
    if (Boolean.TRUE.equals(command.resetPassword())) {
      password = TemporaryPasswords.generate(random);
      member = member.resetPassword(passwords.hash(password), now);
      revoke = true;
      record(command.administratorId(), "MEMBER_PASSWORD_RESET", member, Map.of());
    }
    users.update(member);
    if (revoke) {
      // Last, so a failure rolls the change back with the transaction.
      revocations.revokeIssuedBefore(member.id(), now);
    }
    log.info("Administrator {} changed member {}", command.administratorId(), member.id());
    return new MemberChanged(
        member.id(),
        member.active(),
        password,
        password == null ? null : member.temporaryPasswordExpiresAt());
  }

  private void record(
      UUID administratorId, String action, UserAccount member, Map<String, Object> details) {
    audit.record(administratorId, null, action, "User", member.id(), details);
    Counter.builder("crowded.admin.actions")
        .description("Administrator actions on organizations and members")
        .tag("action", action)
        .register(meters)
        .increment();
  }

  private static MemberOverview overview(UserAccount account) {
    return new MemberOverview(
        account.id(),
        account.organizationId(),
        account.name(),
        account.email(),
        account.active(),
        account.mustChangePassword(),
        account.temporaryPasswordExpiresAt(),
        account.lastSignedInAt());
  }
}
