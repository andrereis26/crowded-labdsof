package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveEventPagesTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final EventPage PAGE =
      new EventPage(
          EVENT,
          "Concerto",
          "Um concerto.",
          EventType.PUBLIC,
          EventStatus.POSTPONED,
          Instant.parse("2026-11-21T20:00:00Z"),
          null,
          null,
          "Coliseu",
          "Porto",
          "Porto",
          null,
          null,
          UUID.randomUUID(),
          "Ribeira Live",
          null,
          null,
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          null);

  private final EventPages pages = mock(EventPages.class);
  private final LiveEventPages live = new LiveEventPages(pages);

  @Test
  void sendsTheNewPageToEveryStreamOfTheEventUntilItCloses() {
    when(pages.current(EVENT)).thenReturn(PAGE);
    List<EventPage> first = new ArrayList<>();
    List<EventPage> second = new ArrayList<>();
    Runnable closeFirst = live.subscribe(EVENT, first::add);
    live.subscribe(EVENT, second::add);

    live.deliver(EVENT);
    closeFirst.run();
    closeFirst.run();
    live.deliver(EVENT);

    assertThat(first).containsExactly(PAGE);
    assertThat(second).containsExactly(PAGE, PAGE);
  }

  @Test
  void dropsAStreamThatFailsAndKeepsTheOthers() {
    when(pages.current(EVENT)).thenReturn(PAGE);
    List<EventPage> healthy = new ArrayList<>();
    live.subscribe(
        EVENT,
        page -> {
          throw new IllegalStateException("closed");
        });
    live.subscribe(EVENT, healthy::add);

    live.deliver(EVENT);
    live.deliver(EVENT);

    assertThat(healthy).hasSize(2);
  }

  @Test
  void readsNothingForAnEventWithoutStreamsHere() {
    Runnable close = live.subscribe(EVENT, page -> {});
    close.run();

    live.deliver(EVENT);
    live.deliver(UUID.randomUUID());

    verifyNoInteractions(pages);
  }
}
