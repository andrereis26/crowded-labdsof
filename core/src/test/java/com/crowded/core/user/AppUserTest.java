package com.crowded.core.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppUserTest {

  private static final UUID ORGANIZATION = UUID.randomUUID();

  @Test
  void isAMemberOnlyOfItsOwnOrganization() {
    AppUser member = new AppUser(UUID.randomUUID(), ORGANIZATION, Role.ORGANIZATION_MEMBER);

    assertThat(member.isMemberOf(ORGANIZATION)).isTrue();
    assertThat(member.isMemberOf(UUID.randomUUID())).isFalse();
    assertThat(member.isMemberOf(null)).isFalse();
  }

  @Test
  void isNeverAMemberWithAnotherRole() {
    assertThat(
            new AppUser(UUID.randomUUID(), ORGANIZATION, Role.ADMINISTRATOR)
                .isMemberOf(ORGANIZATION))
        .isFalse();
  }
}
