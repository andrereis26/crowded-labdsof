package com.crowded.core.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private final UUID session = UUID.randomUUID();

  @Test
  void namesItsSessionAndHasA256BitSecret() {
    RefreshToken token = RefreshToken.generate(session, new Random(1));

    assertThat(token.sessionId()).isEqualTo(session);
    assertThat(token.secret()).hasSize(43);
    assertThat(token.value()).startsWith(session + ".");
    assertThat(RefreshToken.generate(session, new Random(2))).isNotEqualTo(token);
  }

  @Test
  void readsBackTheValueItGave() {
    RefreshToken token = RefreshToken.generate(session, new Random(1));

    assertThat(RefreshToken.parse(token.value())).contains(token);
    assertThat(RefreshToken.parse(null)).isEmpty();
    assertThat(RefreshToken.parse("sem-ponto")).isEmpty();
    assertThat(RefreshToken.parse(session + ".")).isEmpty();
    assertThat(RefreshToken.parse("nao-e-uuid.segredo")).isEmpty();
  }

  @Test
  void keepsOnlyAHashAndNeverPrintsTheSecret() {
    RefreshToken token = new RefreshToken(session, "segredo");

    assertThat(token.hash())
        .hasSize(64)
        .isEqualTo(new RefreshToken(UUID.randomUUID(), "segredo").hash())
        .isNotEqualTo(new RefreshToken(session, "outro").hash());
    assertThat(token.toString()).doesNotContain("segredo").contains(session.toString());
    assertThatThrownBy(() -> new RefreshToken(session, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
