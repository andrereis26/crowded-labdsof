package com.crowded.core.ticket.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID OWNER = UUID.randomUUID();
  private static final EntryCodeHash HASH = new EntryCodeHash("abc123");

  private static Ticket ticket(TicketDetails details, String encryptedEntryCode) {
    return Ticket.connect(
        ID,
        OWNER,
        details,
        encryptedEntryCode,
        HASH,
        ImportMethod.PDF_UPLOAD,
        MatchMethod.EXTRACTED_CODE);
  }

  @Test
  void sharesItsIdWithTheConnectionAndKeepsWhatWasRead() {
    Ticket ticket = ticket(new TicketDetails("A", "2", "14"), "enc");

    assertThat(ticket.getId()).isEqualTo(ID);
    assertThat(ticket.getDetails()).isEqualTo(new TicketDetails("A", "2", "14"));
    assertThat(ticket.getEncryptedEntryCode()).isEqualTo("enc");
    assertThat(ticket.getEntryCodeHash()).isEqualTo(HASH);
    assertThat(ticket.getImportMethod()).isEqualTo(ImportMethod.PDF_UPLOAD);
    assertThat(ticket.getMatchMethod()).isEqualTo(MatchMethod.EXTRACTED_CODE);
  }

  @Test
  void aTicketKeepsTheEntryCodeThatGetsItsOwnerIn() {
    assertThat(ticket(null, "enc").hasEntryCode()).isTrue();
  }

  @Test
  void missingDetailsAreNone() {
    assertThat(ticket(null, "enc").getDetails()).isEqualTo(TicketDetails.NONE);
  }

  @Test
  void belongsToItsOwnerOnly() {
    Ticket ticket = ticket(TicketDetails.NONE, "enc");

    assertThat(ticket.isOwnedBy(OWNER)).isTrue();
    assertThat(ticket.isOwnedBy(UUID.randomUUID())).isFalse();
  }

  @Test
  void needsItsEncryptedEntryCode() {
    assertThatThrownBy(() -> ticket(TicketDetails.NONE, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ticket(TicketDetails.NONE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isEqualById() {
    Ticket first = ticket(TicketDetails.NONE, "enc");
    Ticket restored =
        Ticket.restore(
            ID,
            OWNER,
            new TicketDetails("B", null, null),
            "other",
            new EntryCodeHash("other"),
            ImportMethod.IMAGE_UPLOAD,
            MatchMethod.TYPED_CODE);

    assertThat(restored).isEqualTo(first).hasSameHashCodeAs(first);
    Ticket another =
        Ticket.connect(
            UUID.randomUUID(),
            OWNER,
            TicketDetails.NONE,
            "enc",
            HASH,
            ImportMethod.PDF_UPLOAD,
            MatchMethod.EXTRACTED_CODE);
    assertThat(another).isNotEqualTo(first);
  }

  @Test
  void neverPrintsTheCodeOrItsHash() {
    Ticket ticket = ticket(TicketDetails.NONE, "secret-encrypted");

    assertThat(ticket.toString()).doesNotContain("secret-encrypted", "abc123");
    assertThat(HASH.toString()).doesNotContain("abc123");
  }

  @Test
  void aHashIsNotBlank() {
    assertThatThrownBy(() -> new EntryCodeHash(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EntryCodeHash(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
