package com.crowded.core.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final UUID ORGANIZATION = UUID.randomUUID();

  @Test
  void isAMemberOnlyOfItsOwnOrganization() {
    User member = User.restore(UUID.randomUUID(), ORGANIZATION, Role.ORGANIZATION_MEMBER, true);

    assertThat(member.isMemberOf(ORGANIZATION)).isTrue();
    assertThat(member.isMemberOf(UUID.randomUUID())).isFalse();
    assertThat(member.isMemberOf(null)).isFalse();
  }

  @Test
  void isNeverAMemberWithAnotherRole() {
    assertThat(
            User.restore(UUID.randomUUID(), ORGANIZATION, Role.ADMINISTRATOR, true)
                .isMemberOf(ORGANIZATION))
        .isFalse();
  }

  @Test
  void isNoLongerAMemberOnceDeactivated() {
    assertThat(
            User.restore(UUID.randomUUID(), ORGANIZATION, Role.ORGANIZATION_MEMBER, false)
                .isMemberOf(ORGANIZATION))
        .isFalse();
  }
}
