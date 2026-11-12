package com.crowded.notifications.shared.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.notifications.shared.domain.exception.NotAuthenticatedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

  @Test
  void isTheTokensSubject() {
    UUID user = UUID.randomUUID();

    assertThat(CurrentUser.id(token(user.toString()))).isEqualTo(user);
  }

  @Test
  void refusesATokenThatNamesNoUser() {
    Jwt notAUser = token("service");

    assertThatThrownBy(() -> CurrentUser.id(notAUser))
        .isInstanceOf(NotAuthenticatedException.class)
        .hasFieldOrPropertyWithValue("code", "INVALID_TOKEN");
    assertThatThrownBy(() -> CurrentUser.id(null)).isInstanceOf(NotAuthenticatedException.class);
  }

  private static Jwt token(String subject) {
    return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject).build();
  }
}
