package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScheduleTest {

  private static final Instant DOORS = Instant.parse("2026-11-20T18:30:00Z");
  private static final Instant START = Instant.parse("2026-11-20T20:00:00Z");
  private static final Instant END = Instant.parse("2026-11-20T23:00:00Z");
  private static final Schedule SCHEDULE = new Schedule(START, DOORS, END);

  @Test
  void cannotOpenTheDoorsAfterTheStartOrEndBeforeIt() {
    assertRefused(() -> new Schedule(START, START.plusSeconds(1), null), "DOORS_AFTER_START");
    assertRefused(() -> new Schedule(START, null, START.minusSeconds(1)), "END_BEFORE_START");
    assertThat(new Schedule(START, START, START)).isNotNull();
  }

  @Test
  void movesTheDoorsAndTheEndWithTheStart() {
    Instant later = Instant.parse("2026-12-04T20:00:00Z");

    assertThat(SCHEDULE.movedTo(later, null, null))
        .isEqualTo(
            new Schedule(
                later,
                Instant.parse("2026-12-04T18:30:00Z"),
                Instant.parse("2026-12-04T23:00:00Z")));
  }

  @Test
  void keepsTheDoorsButNotTheEndWhenRetimed() {
    Schedule retimed = SCHEDULE.retimed(START.plusSeconds(3600), null, null);

    assertThat(retimed.doorsOpenAt()).isEqualTo(DOORS);
    assertThat(retimed.end()).isEqualTo(END.plusSeconds(3600));
    assertThat(SCHEDULE.retimed(null, DOORS.plusSeconds(60), null).start()).isEqualTo(START);
  }

  private static void assertRefused(Runnable creation, String code) {
    assertThatThrownBy(creation::run)
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
