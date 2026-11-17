package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The crowd level an organization publishes on its event. */
class EventCrowdTest {

  private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");
  private static final Provenance RIBEIRA = Provenance.organization("Ribeira Live", NOW);

  @Test
  void anEventStartsWithoutACrowdLevel() {
    assertThat(event(EventStatus.ONGOING).getCrowd()).isNull();
  }

  @Test
  void publishesTheLevelWithItsSource() {
    Event event = event(EventStatus.ONGOING);

    event.publishCrowdLevel(CrowdLevel.HIGH, RIBEIRA);

    assertThat(event.getCrowd()).isEqualTo(new CrowdStatus(CrowdLevel.HIGH, RIBEIRA));
  }

  @Test
  void theSameLevelAgainTellsItIsStillCurrent() {
    Event event = event(EventStatus.ONGOING);
    event.publishCrowdLevel(CrowdLevel.FULL, RIBEIRA);
    Provenance later = Provenance.organization("Ribeira Live", NOW.plusSeconds(600));

    event.publishCrowdLevel(CrowdLevel.FULL, later);

    assertThat(event.getCrowd().provenance()).isEqualTo(later);
  }

  @ParameterizedTest
  @EnumSource(
      value = EventStatus.class,
      names = {"CANCELLED", "ENDED"})
  void refusesALevelForAnEventThatIsOver(EventStatus status) {
    Event event = event(status);

    assertThatThrownBy(() -> event.publishCrowdLevel(CrowdLevel.LOW, RIBEIRA))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "EVENT_CLOSED");
    assertThat(event.getCrowd()).isNull();
  }

  @Test
  void refusesAMissingLevel() {
    Event event = event(EventStatus.ONGOING);

    assertThatThrownBy(() -> event.publishCrowdLevel(null, RIBEIRA))
        .isInstanceOf(RuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "CROWD_LEVEL_REQUIRED");
  }

  private static Event event(EventStatus status) {
    return StoredEvents.restore(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new EventState(
            status,
            new Schedule(NOW.minusSeconds(3600), null, NOW.plusSeconds(7200)),
            new Venue("Coliseu", "Porto", null)));
  }
}
