package com.crowded.core.ticket.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketImportTest {

  private static final UUID OWNER = UUID.randomUUID();
  private static final TicketExtraction EXTRACTION =
      new TicketExtraction("MARG-2026", new TicketDetails("A", null, "14"));

  @Test
  void startsWithANewIdTheOwnerAndWhatTheAiFound() {
    TicketImport started = TicketImport.start(OWNER, "encrypted", EXTRACTION);

    assertThat(started.getId()).isNotNull();
    assertThat(started.getOwnerId()).isEqualTo(OWNER);
    assertThat(started.getEncryptedEntryCode()).isEqualTo("encrypted");
    assertThat(started.getExtraction()).isEqualTo(EXTRACTION);
    assertThat(TicketImport.start(OWNER, "encrypted", EXTRACTION).getId())
        .isNotEqualTo(started.getId());
  }

  @Test
  void keepsNothingFoundWhenTheAiFoundNothing() {
    assertThat(TicketImport.start(OWNER, "encrypted", null).getExtraction())
        .isEqualTo(TicketExtraction.NOTHING);
  }

  @Test
  void needsAnOwnerAndTheEncryptedEntryCode() {
    assertThatNullPointerException()
        .isThrownBy(() -> TicketImport.start(null, "encrypted", EXTRACTION));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TicketImport.start(OWNER, " ", EXTRACTION));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TicketImport.start(OWNER, null, EXTRACTION));
  }

  @Test
  void isRestoredAsItWasSavedAndIsTheSameImportByItsId() {
    TicketImport started = TicketImport.start(OWNER, "encrypted", EXTRACTION);

    TicketImport restored = TicketImport.restore(started.getId(), OWNER, "encrypted", EXTRACTION);

    assertThat(restored).isEqualTo(started).hasSameHashCodeAs(started);
    assertThat(restored.getExtraction()).isEqualTo(EXTRACTION);
    assertThat(TicketImport.restore(UUID.randomUUID(), OWNER, "encrypted", EXTRACTION))
        .isNotEqualTo(started);
  }

  @Test
  void neverPrintsTheEntryCode() {
    assertThat(TicketImport.start(OWNER, "encrypted", EXTRACTION).toString())
        .doesNotContain("encrypted");
  }
}
