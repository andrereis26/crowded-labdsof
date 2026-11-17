package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.NewValues;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.RequestedChange;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.StoredEvents;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventViewServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final Instant START = Instant.parse("2026-11-20T20:00:00Z");
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID PARTICIPANT = UUID.randomUUID();
  private static final UUID STRANGER = UUID.randomUUID();

  private final EventRepository events = mock(EventRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final EventChangeRepository changes = mock(EventChangeRepository.class);
  private final EventPages pages = mock(EventPages.class);
  private final EventViewService service =
      new EventViewService(
          new EventAccess(events, users, connections, new SimpleMeterRegistry()), pages, changes);
  private Event event;

  @BeforeEach
  void givenAnEventWithAConnectedParticipant() {
    event =
        StoredEvents.restore(
            UUID.randomUUID(),
            ORGANIZATION,
            new EventState(
                EventStatus.SCHEDULED,
                new Schedule(START, null, null),
                new Venue("Coliseu", "Porto", null)));
    when(events.findById(event.getId())).thenReturn(Optional.of(event));
    for (UUID participant : new UUID[] {PARTICIPANT, STRANGER}) {
      when(users.findById(participant))
          .thenReturn(Optional.of(User.restore(participant, null, Role.PARTICIPANT, true)));
    }
    when(connections.isConnected(PARTICIPANT, event.getId())).thenReturn(true);
  }

  @Test
  void showsTheCachedPageToAConnectedParticipant() {
    EventPage page =
        new EventPage(
            event.getId(),
            "Concerto",
            "Um concerto.",
            EventType.TICKETED,
            EventStatus.SCHEDULED,
            START,
            null,
            null,
            "Coliseu",
            "Porto",
            "Porto",
            null,
            null,
            ORGANIZATION,
            "Ribeira Live",
            null,
            null);
    when(pages.current(event.getId())).thenReturn(page);

    assertThat(service.page(PARTICIPANT, event.getId())).isEqualTo(page);
  }

  @Test
  void listsTheChangesInTheOrderTheRepositoryGivesThem() {
    EventChange postponement = change(ChangeType.POSTPONEMENT, NOW.minusSeconds(60));
    EventChange cancellation = change(ChangeType.CANCELLATION, NOW);
    when(changes.findByEvent(event.getId())).thenReturn(List.of(cancellation, postponement));

    assertThat(service.history(PARTICIPANT, event.getId()))
        .extracting(EventChangeView::id)
        .containsExactly(cancellation.getId(), postponement.getId());
  }

  @Test
  void listsNothingForAnEventWithoutChanges() {
    when(changes.findByEvent(event.getId())).thenReturn(List.of());

    assertThat(service.history(PARTICIPANT, event.getId())).isEmpty();
  }

  @Test
  void hidesThePageAndTheHistoryFromAParticipantWhoIsNotConnected() {
    assertThatThrownBy(() -> service.page(STRANGER, event.getId()))
        .isInstanceOfSatisfying(
            NotAllowedException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_CONNECTED"));
    assertThatThrownBy(() -> service.history(STRANGER, event.getId()))
        .isInstanceOfSatisfying(
            NotAllowedException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_CONNECTED"));
    verifyNoInteractions(pages, changes);
  }

  private EventChange change(ChangeType type, Instant at) {
    NewValues values =
        type == ChangeType.POSTPONEMENT
            ? new NewValues(START.plusSeconds(86_400), null, null, null, null, null, null)
            : null;
    return StoredEvents.restore(UUID.randomUUID(), ORGANIZATION, event.getState())
        .publishChange(
            new RequestedChange(type, values, true, null, null),
            Provenance.organization("Ribeira Live", at),
            at)
        .orElseThrow();
  }
}
