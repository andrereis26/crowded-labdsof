package com.crowded.core.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

  @Test
  void isStrippedAndInLowerCase() {
    assertThat(new Email("  Ana@Exemplo.PT ")).isEqualTo(new Email("ana@exemplo.pt"));
    assertThat(new Email("Ana@Exemplo.PT").value()).isEqualTo("ana@exemplo.pt");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "sem-arroba", "a@", "@b.pt", "a b@c.pt", "a@b@c.pt"})
  void refusesWhatIsNotAnEmail(String value) {
    assertThatThrownBy(() -> new Email(value))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo("EMAIL_INVALID"));
    assertThat(Email.parse(value)).isEmpty();
  }

  @Test
  void refusesAnAddressLongerThanSmtpAllows() {
    String local = "a".repeat(64);
    String domain = "b".repeat(Email.MAX_LENGTH - local.length() - 1);

    assertThat(Email.parse(local + "@" + domain)).isPresent();
    assertThat(Email.parse(local + "@" + domain + "c")).isEmpty();
  }
}
