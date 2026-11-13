package com.crowded.core.event.domain.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class EventCodesTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final EventCode CODE = new EventCode("MARG-2026");

  private final EventRepository events = mock(EventRepository.class);
  private final EventCodes codes = new EventCodes(events);

  @Test
  void letsAFreeCodeThroughAfterTakingTheLock() {
    UUID event = UUID.randomUUID();

    assertThatNoException().isThrownBy(() -> codes.checkFree(CODE, event, NOW));

    InOrder order = inOrder(events);
    order.verify(events).lockCodes();
    order.verify(events).isCodeUsedByAnotherUpcomingEvent(CODE, event, NOW);
  }

  @Test
  void refusesACodeAnotherUpcomingEventUses() {
    when(events.isCodeUsedByAnotherUpcomingEvent(CODE, null, NOW)).thenReturn(true);

    assertThatThrownBy(() -> codes.checkFree(CODE, null, NOW))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "EVENT_CODE_TAKEN");
  }
}
