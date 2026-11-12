package com.crowded.notifications.notification.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;

class EventRepositoryAdapterTest {

  private final SpringDataEventRepository rows = mock(SpringDataEventRepository.class);
  private final EventRepositoryAdapter events = new EventRepositoryAdapter(rows);

  @Test
  void looksNothingUpForNoEvents() {
    assertThat(events.findNames(List.of())).isEmpty();
    verifyNoInteractions(rows);
  }
}
