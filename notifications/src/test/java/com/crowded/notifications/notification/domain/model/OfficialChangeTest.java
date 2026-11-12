package com.crowded.notifications.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfficialChangeTest {

  private static final Recipient TICKET_HOLDER = new Recipient(UUID.randomUUID(), true);
  private static final Recipient ACCESS_CODE = new Recipient(UUID.randomUUID(), false);

  @Test
  void aPostponementSaysSoAndLeavesTheNewDateToTheChange() {
    assertThat(change(ChangeType.POSTPONEMENT, true, false).describeFor(ACCESS_CODE))
        .isEqualTo(new NotificationText("Adiado", "A organização adiou o evento."));
  }

  @Test
  void aTicketHolderLearnsWhetherTheTicketIsValidAndWhereToAskForARefund() {
    OfficialChange change =
        OfficialChange.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.POSTPONEMENT,
            true,
            false,
            null,
            null,
            null,
            true,
            " Até 31 OUT em teatromunicipal.pt/reembolsos ",
            " Pedimos desculpa pelo incómodo. ");

    assertThat(change.describeFor(TICKET_HOLDER).body())
        .isEqualTo(
            "A organização adiou o evento. O teu bilhete continua válido. "
                + "Reembolso: Até 31 OUT em teatromunicipal.pt/reembolsos\n"
                + "Pedimos desculpa pelo incómodo.");
    assertThat(change.describeFor(ACCESS_CODE).body())
        .isEqualTo("A organização adiou o evento.\nPedimos desculpa pelo incómodo.");
  }

  @Test
  void aCancellationSaysSoAndWhetherTheTicketStillCounts() {
    OfficialChange change =
        OfficialChange.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.CANCELLATION,
            false,
            false,
            null,
            null,
            null,
            false,
            null,
            null);

    assertThat(change.describeFor(TICKET_HOLDER))
        .isEqualTo(
            new NotificationText(
                "Cancelado",
                "A organização cancelou o evento. O teu bilhete deixa de ser válido."));
  }

  @Test
  void aTimeChangeSaysWhichTimeMoved() {
    assertThat(change(ChangeType.TIME_CHANGE, true, false).describeFor(ACCESS_CODE))
        .isEqualTo(
            new NotificationText("Nova hora de início", "A organização mudou a hora de início."));
    assertThat(change(ChangeType.TIME_CHANGE, false, true).describeFor(ACCESS_CODE))
        .isEqualTo(
            new NotificationText(
                "Nova hora de abertura", "A organização mudou a hora de abertura das portas."));
    assertThat(change(ChangeType.TIME_CHANGE, true, true).describeFor(ACCESS_CODE))
        .isEqualTo(
            new NotificationText(
                "Nova hora de início",
                "A organização mudou a hora de início e a de abertura das portas."));
    assertThat(change(ChangeType.TIME_CHANGE, false, false).describeFor(ACCESS_CODE))
        .isEqualTo(new NotificationText("Novo horário", "A organização mudou o horário."));
  }

  @Test
  void aVenueChangeSaysTheNewVenueItsAddressAndTheOldVenue() {
    NotificationText text =
        OfficialChange.restore(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ChangeType.VENUE_CHANGE,
                false,
                false,
                "Teatro Municipal",
                "Coliseu do Porto",
                "Rua de Passos Manuel 137, Porto",
                null,
                null,
                null)
            .describeFor(ACCESS_CODE);

    assertThat(text.title()).isEqualTo("Novo local: Coliseu do Porto");
    assertThat(text.body()).isEqualTo("Rua de Passos Manuel 137, Porto. Era em Teatro Municipal.");
  }

  @Test
  void venueValuesCoreDidNotRecordAreLeftOut() {
    assertThat(change(ChangeType.VENUE_CHANGE, false, false).describeFor(ACCESS_CODE))
        .isEqualTo(new NotificationText("Novo local", ""));
    OfficialChange withMessage =
        OfficialChange.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.VENUE_CHANGE,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            "Vemo-nos lá.");
    assertThat(withMessage.describeFor(ACCESS_CODE).body()).isEqualTo("Vemo-nos lá.");
  }

  @Test
  void otherDetailsAndABlankRefundSayOnlyThatTheEventChanged() {
    OfficialChange change =
        OfficialChange.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.DETAILS_UPDATE,
            false,
            false,
            null,
            null,
            null,
            null,
            "  ",
            "Novo programa no site.");

    assertThat(change.describeFor(TICKET_HOLDER))
        .isEqualTo(
            new NotificationText(
                "Detalhes atualizados",
                "A organização atualizou o evento.\nNovo programa no site."));
  }

  @Test
  void isOfTheEventItChangedAndIdentifiedByItsId() {
    UUID event = UUID.randomUUID();
    OfficialChange change =
        OfficialChange.restore(
            UUID.randomUUID(),
            event,
            ChangeType.CANCELLATION,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            null);

    assertThat(change.isOf(event)).isTrue();
    assertThat(change.isOf(UUID.randomUUID())).isFalse();
    assertThat(change)
        .isEqualTo(
            OfficialChange.restore(
                change.getId(),
                UUID.randomUUID(),
                ChangeType.POSTPONEMENT,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                null))
        .isNotEqualTo(change(ChangeType.CANCELLATION, false, false));
  }

  private static OfficialChange change(
      ChangeType type, boolean startChanged, boolean doorsOpenChanged) {
    return OfficialChange.restore(
        UUID.randomUUID(),
        UUID.randomUUID(),
        type,
        startChanged,
        doorsOpenChanged,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
