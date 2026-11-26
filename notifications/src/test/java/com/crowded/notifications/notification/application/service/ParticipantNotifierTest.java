package com.crowded.notifications.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.domain.exception.CauseNotFoundException;
import com.crowded.notifications.notification.domain.exception.UnsupportedNotificationTypeException;
import com.crowded.notifications.notification.domain.model.AlertType;
import com.crowded.notifications.notification.domain.model.ChangeType;
import com.crowded.notifications.notification.domain.model.Connection;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationType;
import com.crowded.notifications.notification.domain.model.OfficialChange;
import com.crowded.notifications.notification.domain.model.OperationalAlert;
import com.crowded.notifications.notification.domain.model.WarningLevel;
import com.crowded.notifications.notification.domain.model.WeatherWarning;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantNotifierTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID TICKET_HOLDER = UUID.randomUUID();
  private static final UUID ACCESS_CODE = UUID.randomUUID();
  private static final UUID TURNED_OFF = UUID.randomUUID();

  private final OfficialChange cancellation =
      OfficialChange.restore(
          UUID.randomUUID(),
          EVENT,
          ChangeType.CANCELLATION,
          false,
          false,
          null,
          null,
          null,
          false,
          "Até 5 DEZ em ribeira-live.test/reembolsos",
          null);
  private final OperationalAlert closure =
      OperationalAlert.restore(
          UUID.randomUUID(),
          EVENT,
          AlertType.ENTRANCE_CLOSURE,
          "Usem a Porta B, na Rua do Almada.");
  private final WeatherWarning storm =
      WeatherWarning.restore(
          UUID.randomUUID(),
          EVENT,
          "Trovoada",
          WarningLevel.RED,
          Instant.parse("2026-11-27T18:00:00Z"),
          Instant.parse("2026-11-27T23:00:00Z"),
          "Trovoada forte com granizo.",
          ZoneId.of("Europe/Lisbon"));
  private final List<Connection> connections =
      List.of(
          new Connection(TICKET_HOLDER, true, null, null),
          new Connection(ACCESS_CODE, false, null, null),
          new Connection(TURNED_OFF, true, false, null));
  private final InMemoryNotifications notifications = new InMemoryNotifications();
  private final List<UUID> announced = new ArrayList<>();
  private final ParticipantNotifier notifier =
      new ParticipantNotifier(
          id -> Optional.ofNullable(Map.of(cancellation.getId(), cancellation).get(id)),
          id -> Optional.ofNullable(Map.of(closure.getId(), closure).get(id)),
          id -> Optional.ofNullable(Map.of(storm.getId(), storm).get(id)),
          (eventId, type) -> eventId.equals(EVENT) ? connections : List.of(),
          notifications,
          announced::add,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void notifiesEveryConnectedParticipantWhoWantsOfficialChanges() {
    UUID messageId = UUID.randomUUID();

    int stored = notifier.notify(command(messageId, NotificationType.OFFICIAL_CHANGE, List.of()));

    assertThat(stored).isEqualTo(2);
    assertThat(notifications.stored)
        .extracting(Notification::getUserId)
        .containsExactly(TICKET_HOLDER, ACCESS_CODE);
    assertThat(notifications.stored)
        .allSatisfy(
            notification -> {
              assertThat(notification.getType()).isEqualTo(NotificationType.OFFICIAL_CHANGE);
              assertThat(notification.getEventId()).isEqualTo(EVENT);
              assertThat(notification.getCauseId()).isEqualTo(cancellation.getId());
              assertThat(notification.getTitle()).isEqualTo("Cancelado");
              assertThat(notification.getCreatedAt()).isEqualTo(NOW);
              assertThat(notification.getDeduplicationKey())
                  .isEqualTo(Notification.deduplicationKey(notification.getUserId(), messageId));
            });
    assertThat(notifications.stored.get(0).getBody())
        .contains("O teu bilhete deixa de ser válido.", "Reembolso: Até 5 DEZ");
    assertThat(notifications.stored.get(1).getBody()).isEqualTo("A organização cancelou o evento.");
    assertThat(announced)
        .containsExactly(notifications.stored.get(0).getId(), notifications.stored.get(1).getId());
  }

  @Test
  void notifiesEveryConnectedParticipantWhoWantsOperationalAlertsWithTheSameText() {
    int stored =
        notifier.notify(
            new NotifyParticipantsCommand(
                UUID.randomUUID(),
                NotificationType.OPERATIONAL_ALERT,
                EVENT,
                closure.getId(),
                List.of()));

    assertThat(stored).isEqualTo(2);
    assertThat(notifications.stored)
        .extracting(Notification::getUserId)
        .containsExactly(TICKET_HOLDER, ACCESS_CODE);
    assertThat(notifications.stored)
        .allSatisfy(
            notification -> {
              assertThat(notification.getType()).isEqualTo(NotificationType.OPERATIONAL_ALERT);
              assertThat(notification.getCauseId()).isEqualTo(closure.getId());
              assertThat(notification.getTitle()).isEqualTo("Entrada fechada");
              assertThat(notification.getBody()).isEqualTo("Usem a Porta B, na Rua do Almada.");
            });
    assertThat(announced).hasSize(2);
  }

  @Test
  void notifiesEveryConnectedParticipantWhoWantsWeatherWarningsOfARedOneAsCritical() {
    int stored =
        notifier.notify(
            new NotifyParticipantsCommand(
                UUID.randomUUID(),
                NotificationType.WEATHER_WARNING,
                EVENT,
                storm.getId(),
                List.of()));

    assertThat(stored).isEqualTo(2);
    assertThat(notifications.stored)
        .allSatisfy(
            notification -> {
              assertThat(notification.getType()).isEqualTo(NotificationType.WEATHER_WARNING);
              assertThat(notification.getCauseId()).isEqualTo(storm.getId());
              assertThat(notification.getTitle()).isEqualTo("Crítico · Aviso vermelho de trovoada");
              assertThat(notification.getBody())
                  .isEqualTo("Trovoada forte com granizo. Em vigor das 18:00 às 23:00 de 27/11.");
            });
  }

  @Test
  void failsWhenTheWarningIsMissingOrAboutAnotherEvent() {
    assertThatThrownBy(
            () ->
                notifier.notify(
                    new NotifyParticipantsCommand(
                        UUID.randomUUID(),
                        NotificationType.WEATHER_WARNING,
                        UUID.randomUUID(),
                        storm.getId(),
                        List.of())))
        .isInstanceOf(CauseNotFoundException.class);
  }

  @Test
  void failsWhenTheAlertIsMissingOrAboutAnotherEvent() {
    assertThatThrownBy(
            () ->
                notifier.notify(
                    new NotifyParticipantsCommand(
                        UUID.randomUUID(),
                        NotificationType.OPERATIONAL_ALERT,
                        EVENT,
                        UUID.randomUUID(),
                        List.of())))
        .isInstanceOf(CauseNotFoundException.class);
    assertThatThrownBy(
            () ->
                notifier.notify(
                    new NotifyParticipantsCommand(
                        UUID.randomUUID(),
                        NotificationType.OPERATIONAL_ALERT,
                        UUID.randomUUID(),
                        closure.getId(),
                        List.of())))
        .isInstanceOf(CauseNotFoundException.class);
    assertThat(notifications.stored).isEmpty();
    assertThat(announced).isEmpty();
  }

  @Test
  void aMessageDeliveredTwiceNotifiesOnce() {
    NotifyParticipantsCommand command =
        command(UUID.randomUUID(), NotificationType.OFFICIAL_CHANGE, List.of());

    notifier.notify(command);
    int again = notifier.notify(command);

    assertThat(again).isZero();
    assertThat(notifications.stored).hasSize(2);
    assertThat(announced)
        .as("a redelivered message announces nothing again")
        .containsExactlyElementsOf(notifications.stored.stream().map(Notification::getId).toList());
  }

  @Test
  void notifiesOnlyTheNamedUsersWhenTheMessageNamesThem() {
    notifier.notify(
        command(UUID.randomUUID(), NotificationType.OFFICIAL_CHANGE, List.of(ACCESS_CODE)));

    assertThat(notifications.stored)
        .extracting(Notification::getUserId)
        .containsExactly(ACCESS_CODE);
  }

  @Test
  void refusesATypeItHasNoTextFor() {
    NotifyParticipantsCommand command =
        command(UUID.randomUUID(), NotificationType.INCIDENT_STATUS, List.of());

    assertThatThrownBy(() -> notifier.notify(command))
        .isInstanceOf(UnsupportedNotificationTypeException.class)
        .hasFieldOrPropertyWithValue("code", "UNSUPPORTED_NOTIFICATION_TYPE");
    assertThat(notifications.stored).isEmpty();
    assertThat(announced).isEmpty();
  }

  @Test
  void failsWhenTheChangeIsMissingOrBelongsToAnotherEvent() {
    NotifyParticipantsCommand missing =
        new NotifyParticipantsCommand(
            UUID.randomUUID(),
            NotificationType.OFFICIAL_CHANGE,
            EVENT,
            UUID.randomUUID(),
            List.of());
    NotifyParticipantsCommand otherEvent =
        new NotifyParticipantsCommand(
            UUID.randomUUID(),
            NotificationType.OFFICIAL_CHANGE,
            UUID.randomUUID(),
            cancellation.getId(),
            null);

    assertThatThrownBy(() -> notifier.notify(missing))
        .isInstanceOf(CauseNotFoundException.class)
        .hasFieldOrPropertyWithValue("code", "CAUSE_NOT_FOUND");
    assertThatThrownBy(() -> notifier.notify(otherEvent))
        .isInstanceOf(CauseNotFoundException.class);
    assertThat(notifications.stored).isEmpty();
  }

  private NotifyParticipantsCommand command(
      UUID messageId, NotificationType type, List<UUID> userIds) {
    return new NotifyParticipantsCommand(messageId, type, EVENT, cancellation.getId(), userIds);
  }
}
