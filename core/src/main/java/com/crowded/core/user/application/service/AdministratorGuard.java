package com.crowded.core.user.application.service;

import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lets only an active platform administrator through. The role is read from the database on each
 * request, never from the token (API Outline, Authentication).
 */
@Service
public class AdministratorGuard {

  private static final Logger log = LoggerFactory.getLogger(AdministratorGuard.class);

  private final UserRepository users;

  AdministratorGuard(UserRepository users) {
    this.users = users;
  }

  /**
   * @throws NotAllowedException when {@code userId} is not an active administrator
   */
  public void require(UUID userId) {
    boolean administrator = users.findById(userId).filter(User::isAdministrator).isPresent();
    if (!administrator) {
      log.warn("Refused an administration request by user {}", userId);
      throw new NotAllowedException(
          "ADMINISTRATOR_ONLY", "Only a platform administrator can do this.");
    }
  }
}
