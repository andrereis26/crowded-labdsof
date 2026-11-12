package com.crowded.core.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import org.junit.jupiter.api.Test;

class PasswordTest {

  @Test
  void needsEightCharactersAndAtMost72Bytes() {
    assertThat(new Password("Segr3do!").value()).isEqualTo("Segr3do!"); // ggignore
    assertThat(new Password("Aa1-" + "a".repeat(68))).isNotNull(); // ggignore
    assertRefused("Seg3do!", "PASSWORD_TOO_SHORT"); // ggignore
    assertRefused(null, "PASSWORD_TOO_SHORT");
    // 37 two-byte characters: 74 bytes, more than bcrypt reads.
    assertRefused("ç".repeat(37), "PASSWORD_TOO_LONG");
  }

  @Test
  void needsAnUppercaseLetterALowercaseLetterADigitAndASpecialCharacter() {
    assertRefused("segr3do!", "PASSWORD_TOO_WEAK"); // ggignore
    assertRefused("SEGR3DO!", "PASSWORD_TOO_WEAK"); // ggignore
    assertRefused("Segredo!", "PASSWORD_TOO_WEAK"); // ggignore
    assertRefused("Segr3dos", "PASSWORD_TOO_WEAK"); // ggignore
    // A space is not a special character.
    assertRefused("Segr3do s", "PASSWORD_TOO_WEAK");
    assertThat(new Password("Çeia-2026").value()).isEqualTo("Çeia-2026"); // ggignore
    assertThat(new Password("Pass 2026€x")).isNotNull();
  }

  @Test
  void neverShowsItself() {
    assertThat(new Password("Segredo-forte1").toString()).doesNotContain("Segredo"); // ggignore
  }

  private static void assertRefused(String value, String code) {
    assertThatThrownBy(() -> new Password(value))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
