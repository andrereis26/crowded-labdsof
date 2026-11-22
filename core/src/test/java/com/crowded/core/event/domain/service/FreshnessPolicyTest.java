package com.crowded.core.event.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.event.domain.model.InformationKind;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.SourceType;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FreshnessPolicyTest {

  private static final Instant UPDATED = Instant.parse("2026-11-20T10:00:00Z");
  private static final Map<InformationKind, Duration> PERIODS =
      Map.of(
          InformationKind.CROWD, Duration.ofMinutes(20),
          InformationKind.QUEUE, Duration.ofMinutes(15),
          InformationKind.ESTIMATE, Duration.ofMinutes(30),
          InformationKind.WEATHER, Duration.ofHours(3));

  private final FreshnessPolicy policy = new FreshnessPolicy(PERIODS);
  private final Provenance provenance = Provenance.organization("Ribeira Live", UPDATED);

  @ParameterizedTest
  @EnumSource(InformationKind.class)
  void goesOutOfDateExactlyWhenTheKindsPeriodEnds(InformationKind kind) {
    Instant end = UPDATED.plus(PERIODS.get(kind));

    assertThat(policy.staleAt(kind, provenance)).isEqualTo(end);
    assertThat(policy.isStale(kind, provenance, end.minusSeconds(1))).isFalse();
    assertThat(policy.isStale(kind, provenance, end)).isTrue();
    assertThat(policy.isStale(kind, provenance, end.plusSeconds(1))).isTrue();
  }

  @Test
  void eachKindKeepsItsOwnPeriod() {
    Instant at = UPDATED.plus(Duration.ofMinutes(25));

    assertThat(policy.isStale(InformationKind.CROWD, provenance, at)).isTrue();
    assertThat(policy.isStale(InformationKind.QUEUE, provenance, at)).isTrue();
    assertThat(policy.isStale(InformationKind.ESTIMATE, provenance, at)).isFalse();
    assertThat(policy.isStale(InformationKind.WEATHER, provenance, at)).isFalse();
  }

  @Test
  void judgesInformationFromAnySource() {
    Provenance estimate =
        new Provenance(SourceType.PARTICIPANT_ESTIMATE, "Participants", UPDATED, false);

    assertThat(policy.isStale(InformationKind.ESTIMATE, estimate, UPDATED.plusSeconds(1800)))
        .isTrue();
  }

  @Test
  void needsAPositivePeriodForEveryKind() {
    Map<InformationKind, Duration> missing = new EnumMap<>(PERIODS);
    missing.remove(InformationKind.WEATHER);
    Map<InformationKind, Duration> zero = new EnumMap<>(PERIODS);
    zero.put(InformationKind.CROWD, Duration.ZERO);
    Map<InformationKind, Duration> negative = new EnumMap<>(PERIODS);
    negative.put(InformationKind.QUEUE, Duration.ofMinutes(-1));

    assertThatThrownBy(() -> new FreshnessPolicy(missing))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WEATHER");
    assertThatThrownBy(() -> new FreshnessPolicy(zero))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FreshnessPolicy(negative))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
