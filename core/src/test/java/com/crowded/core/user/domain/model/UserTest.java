package com.crowded.core.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final UUID ORGANIZATION = UUID.randomUUID();

  @Test
  void isAMemberOnlyOfItsOwnOrganization() {
    User member = new User(UUID.randomUUID(), ORGANIZATION, Role.ORGANIZATION_MEMBER);

    assertThat(member.isMemberOf(ORGANIZATION)).isTrue();
    assertThat(member.isMemberOf(UUID.randomUUID())).isFalse();
    assertThat(member.isMemberOf(null)).isFalse();
  }

  @Test
  void isNeverAMemberWithAnotherRole() {
    assertThat(
            new User(UUID.randomUUID(), ORGANIZATION, Role.ADMINISTRATOR).isMemberOf(ORGANIZATION))
        .isFalse();
  }
}
