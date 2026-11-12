package com.crowded.notifications.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfficialChangeTest {

  private static final Recipient TICKET_HOLDER = new Recipient(UUID.randomUUID(), true);
  private static final Recipient ACCESS_CODE = new Recipient(UUID.randomUUID(), false);

  // 21:30 in Lisbon on both days: summer time on 25 October 2025, winter time on 8 November.
  private static final Instant SAT_25_OCT = Instant.parse("2025-10-25T20:30:00Z");
  private static final Instant SAT_8_NOV = Instant.parse("2025-11-08T21:30:00Z");

  @Test
  void aPostponementSaysTheNewDateAndTheOldOneAsInTheMockup() {
    NotificationText text =
        change(ChangeType.POSTPONEMENT, SAT_25_OCT, SAT_8_NOV, null, null, null)
            .describeFor(ACCESS_CODE);

    assertThat(text.title()).isEqualTo("Adiado para SÁB 8 NOV · 21:30");
    assertThat(text.body()).isEqualTo("Era SÁB 25 OUT.");
  }

  @Test
  void aPostponementToAnotherTimeSaysTheOldTime() {
    NotificationText text =
        change(
                ChangeType.POSTPONEMENT,
                SAT_25_OCT,
                Instant.parse("2025-11-08T22:00:00Z"),
                null,
                null,
                null)
            .describeFor(ACCESS_CODE);

    assertThat(text.title()).isEqualTo("Adiado para SÁB 8 NOV · 22:00");
    assertThat(text.body()).isEqualTo("Era SÁB 25 OUT · 21:30.");
  }

  @Test
  void aTicketHolderLearnsWhetherTheTicketIsValidAndWhereToAskForARefund() {
    OfficialChange change =
        new OfficialChange(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.POSTPONEMENT,
            SAT_25_OCT,
            SAT_8_NOV,
            null,
            null,
            null,
            null,
            null,
            true,
            " Até 31 OUT em teatromunicipal.pt/reembolsos ",
            " Pedimos desculpa pelo incómodo. ");

    assertThat(change.describeFor(TICKET_HOLDER).body())
        .isEqualTo(
            "Era SÁB 25 OUT. O teu bilhete continua válido. "
                + "Reembolso: Até 31 OUT em teatromunicipal.pt/reembolsos\n"
                + "Pedimos desculpa pelo incómodo.");
    assertThat(change.describeFor(ACCESS_CODE).body())
        .isEqualTo("Era SÁB 25 OUT.\nPedimos desculpa pelo incómodo.");
  }

  @Test
  void aCancellationSaysSoAndWhetherTheTicketStillCounts() {
    OfficialChange change =
        new OfficialChange(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.CANCELLATION,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null);

    NotificationText text = change.describeFor(TICKET_HOLDER);

    assertThat(text.title()).isEqualTo("Cancelado");
    assertThat(text.body())
        .isEqualTo("A organização cancelou o evento. O teu bilhete deixa de ser válido.");
  }

  @Test
  void aTimeChangeOnTheSameDaySaysTheNewTime() {
    NotificationText text =
        change(
                ChangeType.TIME_CHANGE,
                SAT_8_NOV,
                Instant.parse("2025-11-08T22:00:00Z"),
                null,
                Instant.parse("2025-11-08T21:00:00Z"),
                null)
            .describeFor(ACCESS_CODE);

    assertThat(text.title()).isEqualTo("Nova hora: 22:00");
    assertThat(text.body()).isEqualTo("Era às 21:30. Portas abrem às 21:00.");
  }

  @Test
  void aTimeChangeToAnotherDaySaysTheNewDate() {
    NotificationText text =
        change(ChangeType.TIME_CHANGE, SAT_8_NOV, SAT_25_OCT, null, null, null)
            .describeFor(ACCESS_CODE);

    assertThat(text.title()).isEqualTo("Nova data: SÁB 25 OUT · 21:30");
    assertThat(text.body()).isEqualTo("Era SÁB 8 NOV · 21:30.");
  }

  @Test
  void aNewDoorsOpeningTimeSaysTheNewAndOldTimes() {
    NotificationText text =
        change(
                ChangeType.TIME_CHANGE,
                null,
                null,
                Instant.parse("2025-11-08T20:00:00Z"),
                Instant.parse("2025-11-08T19:30:00Z"),
                null)
            .describeFor(ACCESS_CODE);

    assertThat(text.title()).isEqualTo("Portas abrem às 19:30");
    assertThat(text.body()).isEqualTo("Era às 20:00.");
  }

  @Test
  void aVenueChangeSaysTheNewVenueItsAddressAndTheOldVenue() {
    NotificationText text =
        new OfficialChange(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ChangeType.VENUE_CHANGE,
                null,
                null,
                null,
                null,
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
  void valuesCoreDidNotRecordAreLeftOut() {
    assertThat(
            change(ChangeType.POSTPONEMENT, null, null, null, null, null).describeFor(ACCESS_CODE))
        .isEqualTo(new NotificationText("Adiado", ""));
    assertThat(
            change(ChangeType.TIME_CHANGE, null, null, null, null, null).describeFor(ACCESS_CODE))
        .isEqualTo(new NotificationText("Novo horário", ""));
    assertThat(
            change(ChangeType.VENUE_CHANGE, null, null, null, null, null).describeFor(ACCESS_CODE))
        .isEqualTo(new NotificationText("Novo local", ""));
    assertThat(
            change(ChangeType.DETAILS_UPDATE, null, null, null, null, null)
                .describeFor(ACCESS_CODE))
        .isEqualTo(new NotificationText("Detalhes atualizados", ""));
  }

  @Test
  void theOrganizationsMessageAloneIsTheBodyWhenNothingElseIsKnown() {
    OfficialChange change =
        new OfficialChange(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.DETAILS_UPDATE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "  ",
            "Novo programa no site.");

    assertThat(change.describeFor(TICKET_HOLDER).body()).isEqualTo("Novo programa no site.");
  }

  private static OfficialChange change(
      ChangeType type,
      Instant previousStart,
      Instant newStart,
      Instant previousDoors,
      Instant newDoors,
      String message) {
    return new OfficialChange(
        UUID.randomUUID(),
        UUID.randomUUID(),
        type,
        previousStart,
        newStart,
        previousDoors,
        newDoors,
        null,
        null,
        null,
        null,
        null,
        message);
  }
}
