package com.crowded.core.user.application.service;

import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.user.application.dto.AccountOverview;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.domain.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in user's own account, which the apps read at start-up. */
@Service
public class AccountService {

  private final UserRepository users;

  AccountService(UserRepository users) {
    this.users = users;
  }

  /**
   * @throws NotAuthenticatedException when the account no longer exists
   */
  @Transactional(readOnly = true)
  public AccountOverview current(UUID userId) {
    UserAccount account =
        users
            .findAccountById(userId)
            .orElseThrow(
                () -> new NotAuthenticatedException("INVALID_TOKEN", "The token names no user."));
    return new AccountOverview(
        account.getId(),
        account.getEmail().value(),
        account.getName(),
        account.getRole(),
        account.getOrganizationId());
  }
}
