package com.crowded.notifications.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.domain.exception.CauseNotFoundException;
import com.crowded.notifications.notification.domain.exception.UnsupportedNotificationTypeException;
import com.crowded.notifications.notification.domain.model.ChangeType;
import com.crowded.notifications.notification.domain.model.Connection;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationType;
import com.crowded.notifications.notification.domain.model.OfficialChange;
import com.crowded.notifications.notification.domain.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
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
  private final List<Connection> connections =
      List.of(
          new Connection(TICKET_HOLDER, true, null, null),
          new Connection(ACCESS_CODE, false, null, null),
          new Connection(TURNED_OFF, true, false, null));
  private final FakeNotifications notifications = new FakeNotifications();
  private final ParticipantNotifier notifier =
      new ParticipantNotifier(
          id -> Optional.ofNullable(Map.of(cancellation.getId(), cancellation).get(id)),
          (eventId, type) -> eventId.equals(EVENT) ? connections : List.of(),
          notifications,
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
  }

  @Test
  void aMessageDeliveredTwiceNotifiesOnce() {
    NotifyParticipantsCommand command =
        command(UUID.randomUUID(), NotificationType.OFFICIAL_CHANGE, List.of());

    notifier.notify(command);
    int again = notifier.notify(command);

    assertThat(again).isZero();
    assertThat(notifications.stored).hasSize(2);
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
        command(UUID.randomUUID(), NotificationType.WEATHER_WARNING, List.of());

    assertThatThrownBy(() -> notifier.notify(command))
        .isInstanceOf(UnsupportedNotificationTypeException.class)
        .hasFieldOrPropertyWithValue("code", "UNSUPPORTED_NOTIFICATION_TYPE");
    assertThat(notifications.stored).isEmpty();
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

  /** Keeps the first notification per deduplication key, as the unique column does. */
  private static final class FakeNotifications implements NotificationRepository {

    private final List<Notification> stored = new ArrayList<>();

    @Override
    public boolean addIfAbsent(Notification notification) {
      if (stored.stream()
          .anyMatch(n -> n.getDeduplicationKey().equals(notification.getDeduplicationKey()))) {
        return false;
      }
      return stored.add(notification);
    }
  }
}
