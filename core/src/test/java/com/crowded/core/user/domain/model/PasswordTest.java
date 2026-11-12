package com.crowded.core.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import org.junit.jupiter.api.Test;

class PasswordTest {

  @Test
  void needsEightCharactersAndAtMost72Bytes() {
    assertThat(new Password("12345678").value()).isEqualTo("12345678");
    assertThat(new Password("a".repeat(72))).isNotNull();
    assertRefused("1234567", "PASSWORD_TOO_SHORT");
    assertRefused(null, "PASSWORD_TOO_SHORT");
    // 37 two-byte characters: 74 bytes, more than bcrypt reads.
    assertRefused("ç".repeat(37), "PASSWORD_TOO_LONG");
  }

  @Test
  void neverShowsItself() {
    assertThat(new Password("segredo-forte").toString()).doesNotContain("segredo");
  }

  private static void assertRefused(String value, String code) {
    assertThatThrownBy(() -> new Password(value))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
