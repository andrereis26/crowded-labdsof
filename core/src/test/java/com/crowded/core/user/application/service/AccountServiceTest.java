package com.crowded.core.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.user.application.dto.AccountOverview;
import com.crowded.core.user.domain.model.Email;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountServiceTest {

  private final UserRepository users = mock(UserRepository.class);
  private final AccountService service = new AccountService(users);

  @Test
  void readsTheSignedInMembersAccount() {
    UUID organization = UUID.randomUUID();
    UserAccount member =
        UserAccount.addMember(
            organization, "Rita", new Email("rita@ribeira.pt"), "hash", Instant.now());
    when(users.findAccountById(member.getId())).thenReturn(Optional.of(member));

    assertThat(service.current(member.getId()))
        .isEqualTo(
            new AccountOverview(
                member.getId(), "rita@ribeira.pt", "Rita", Role.ORGANIZATION_MEMBER, organization));
  }

  @Test
  void refusesATokenWhoseAccountIsGone() {
    assertThatThrownBy(() -> service.current(UUID.randomUUID()))
        .isInstanceOf(NotAuthenticatedException.class);
  }
}
