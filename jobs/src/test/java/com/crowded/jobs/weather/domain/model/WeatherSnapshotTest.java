package com.crowded.jobs.weather.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A snapshot is kept only when it changes what Jobs knows about a district. */
class WeatherSnapshotTest {

  private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
  private static final Warning WIND =
      new Warning(
          "Vento",
          WarningLevel.ORANGE,
          Instant.parse("2026-11-27T18:00:00Z"),
          Instant.parse("2026-11-28T03:00:00Z"),
          null);

  @Test
  void theFirstSnapshotOfADistrictIsNew() {
    WeatherSnapshot snapshot = WeatherSnapshot.take("Porto", new Warnings(List.of(WIND)), NOW);

    assertThat(snapshot.isNewComparedTo(Optional.empty())).isTrue();
    assertThat(snapshot.kind()).isEqualTo(SnapshotKind.WARNINGS);
    assertThat(snapshot.getPublishedAt()).isNull();
  }

  @Test
  void isNewOnlyWhenTheContentDiffers() {
    WeatherSnapshot latest = WeatherSnapshot.take("Porto", new Warnings(List.of(WIND)), NOW);

    assertThat(
            WeatherSnapshot.take("Porto", new Warnings(List.of(WIND)), NOW.plusSeconds(300))
                .isNewComparedTo(Optional.of(latest)))
        .isFalse();
    assertThat(
            WeatherSnapshot.take("Porto", new Warnings(List.of()), NOW.plusSeconds(300))
                .isNewComparedTo(Optional.of(latest)))
        .isTrue();
  }

  @Test
  void announcesItsDistrictAndIdAndRemembersWhenItWasConfirmed() {
    WeatherSnapshot snapshot = WeatherSnapshot.take("Porto", new Warnings(List.of()), NOW);

    snapshot.markPublished(NOW.plusSeconds(1));

    assertThat(snapshot.announcement()).isEqualTo(new WeatherUpdated("Porto", snapshot.getId()));
    assertThat(snapshot.getPublishedAt()).isEqualTo(NOW.plusSeconds(1));
  }

  @Test
  void isTheSameSnapshotWhenRestored() {
    WeatherSnapshot snapshot = WeatherSnapshot.take("Porto", new Warnings(List.of()), NOW);

    WeatherSnapshot restored =
        WeatherSnapshot.restore(
            snapshot.getId(), "Porto", snapshot.getContent(), snapshot.getFetchedAt(), NOW);

    assertThat(restored).isEqualTo(snapshot).hasSameHashCodeAs(snapshot);
    assertThat(restored.getDistrict()).isEqualTo("Porto");
    assertThat(restored.toString()).contains("Porto", "WARNINGS");
  }
}
