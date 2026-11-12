package com.crowded.core.user.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.user.domain.model.Password;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class TemporaryPasswordsTest {

  @Test
  void hasThreeGroupsOfCharactersThatAreNotEasilyConfused() {
    String password = TemporaryPasswords.generate(new SecureRandom());

    assertThat(password).matches("[A-Za-z2-9]{3}-[A-Za-z2-9]{3}-[A-Za-z2-9]{4}");
    assertThat(password.replace("-", "")).doesNotContainPattern("[0O1lI]");
  }

  @Test
  void usesTheRandomGeneratorItIsGiven() {
    RandomGenerator first = () -> 0L;

    assertThat(TemporaryPasswords.generate(first)).isEqualTo(TemporaryPasswords.generate(first));
  }

  @Test
  void meetsTheRuleOfAPasswordAUserChooses() {
    SecureRandom random = new SecureRandom();
    for (int i = 0; i < 1_000; i++) {
      String password = TemporaryPasswords.generate(random);
      assertThat(Password.isStrong(password)).as(password).isTrue();
      assertThat(new Password(password).value()).isEqualTo(password);
    }
    // Even a generator that always answers 0 gives one of each.
    assertThat(Password.isStrong(TemporaryPasswords.generate(() -> 0L))).isTrue();
  }

  @Test
  void rarelyRepeatsItself() {
    SecureRandom random = new SecureRandom();
    Set<String> passwords = new HashSet<>();
    for (int i = 0; i < 1_000; i++) {
      passwords.add(TemporaryPasswords.generate(random));
    }

    assertThat(passwords).hasSize(1_000);
  }
}
