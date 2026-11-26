package com.crowded.jobs.weather.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The districts come from Core's events, in the order the query gives them. */
class EventDistrictsAdapterTest {

  @Test
  void readsTheDistrictsOfTheEventsBetweenTheTwoTimes() {
    SpringDataEventRepository events = mock(SpringDataEventRepository.class);
    Instant now = Instant.parse("2026-11-27T10:00:00Z");
    Instant until = now.plusSeconds(3600);
    when(events.findDistrictsBetween(now, until)).thenReturn(List.of("Lisboa", "Porto"));

    assertThat(new EventDistrictsAdapter(events).withEventsBetween(now, until))
        .containsExactly("Lisboa", "Porto");
  }
}
