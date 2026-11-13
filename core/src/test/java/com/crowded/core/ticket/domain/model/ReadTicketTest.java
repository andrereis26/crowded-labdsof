package com.crowded.core.ticket.domain.model;

import static com.crowded.core.ticket.domain.model.ReadTicket.REMOVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ReadTicketTest {

  private static String withoutPersonalData(String text, String entryCode) {
    return new ReadTicket(entryCode, text).textWithoutPersonalData();
  }

  @Test
  void removesEmailAddresses() {
    assertThat(withoutPersonalData("Enviado para joana.silva+bilhetes@exemplo.pt hoje", "QR"))
        .isEqualTo("Enviado para " + REMOVED + " hoje");
  }

  @Test
  void removesTaxNumbersWrittenTogetherOrInGroups() {
    assertThat(withoutPersonalData("NIF 123456789 e 987 654 321", "QR"))
        .doesNotContain("123456789", "987 654 321");
  }

  @Test
  void keepsNumbersThatAreNotTaxNumbers() {
    assertThat(withoutPersonalData("Lugar 12, porta 3, 2026, ref 1234567890", "QR"))
        .isEqualTo("Lugar 12, porta 3, 2026, ref 1234567890");
  }

  @Test
  void removesTheHolderNameButKeepsTheLabel() {
    String text = "Nome: Joana Silva\nTitular - Rui Costa\nName: Ana\nNome do evento: Festival";

    assertThat(withoutPersonalData(text, "QR"))
        .isEqualTo(
            "Nome: "
                + REMOVED
                + "\nTitular - "
                + REMOVED
                + "\nName: "
                + REMOVED
                + "\nNome do evento: Festival");
  }

  @Test
  void removesTheEntryCodeWhereverItIsPrinted() {
    assertThat(withoutPersonalData("Código ABC-123-XYZ (ABC-123-XYZ)", "ABC-123-XYZ"))
        .isEqualTo("Código " + REMOVED + " (" + REMOVED + ")");
  }

  @Test
  void keepsTheEventCodeAndTheTicketDetails() {
    String text = "Código do evento: MARG-2026\nPorta A Setor 2 Lugar 14";

    assertThat(withoutPersonalData(text, "QR-PAYLOAD")).isEqualTo(text);
  }

  @Test
  void isOnlyATicketWithItsQrCode() {
    assertThatIllegalArgumentException().isThrownBy(() -> new ReadTicket(null, "text"));
    assertThatIllegalArgumentException().isThrownBy(() -> new ReadTicket(" ", "text"));
    assertThat(new ReadTicket("QR", null).text()).isEmpty();
  }

  @Test
  void refusesAFileWithoutAQrCode() {
    assertThat(ReadTicket.noQrCode().getCode()).isEqualTo("QR_CODE_NOT_FOUND");
  }

  @Test
  void neverPrintsTheEntryCode() {
    assertThat(new ReadTicket("QR-ENTRY-0001", "text").toString()).doesNotContain("QR-ENTRY-0001");
  }
}
