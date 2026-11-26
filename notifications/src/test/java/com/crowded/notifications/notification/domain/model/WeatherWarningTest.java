package com.crowded.notifications.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What participants read of an IPMA warning, in the time of the event's district. */
class WeatherWarningTest {

  private static final UUID EVENT = UUID.randomUUID();

  @Test
  void saysTheLevelTheTypeIpmasTextAndBothDaysOfAWarningThatEndsTheNextDay() {
    WeatherWarning warning =
        warning(
            WarningLevel.ORANGE,
            "Vento",
            "2026-11-27T18:00:00Z",
            "2026-11-28T03:00:00Z",
            " Rajadas até 90 km/h. ",
            "Europe/Lisbon");

    assertThat(warning.describe())
        .isEqualTo(
            new NotificationText(
                "Aviso laranja de vento",
                "Rajadas até 90 km/h. Em vigor das 18:00 de 27/11 às 03:00 de 28/11."));
    assertThat(warning.isOf(EVENT)).isTrue();
    assertThat(warning.isOf(UUID.randomUUID())).isFalse();
  }

  @Test
  void saysARedWarningIsCriticalAndShowsItsTimeInTheAzores() {
    WeatherWarning warning =
        warning(
            WarningLevel.RED,
            "Agitação Marítima",
            "2026-11-27T12:00:00Z",
            "2026-11-27T21:00:00Z",
            null,
            "Atlantic/Azores");

    assertThat(warning.describe())
        .isEqualTo(
            new NotificationText(
                "Crítico · Aviso vermelho de agitação marítima",
                "Em vigor das 11:00 às 20:00 de 27/11."));
  }

  @Test
  void isTheSameWarningWithTheSameId() {
    WeatherWarning warning =
        warning(
            WarningLevel.YELLOW,
            "Nevoeiro",
            "2026-11-27T12:00:00Z",
            "2026-11-27T21:00:00Z",
            "",
            "Europe/Lisbon");

    WeatherWarning same =
        WeatherWarning.restore(
            warning.getId(),
            EVENT,
            "Nevoeiro",
            WarningLevel.YELLOW,
            Instant.parse("2026-11-27T12:00:00Z"),
            Instant.parse("2026-11-27T21:00:00Z"),
            null,
            ZoneId.of("Europe/Lisbon"));

    assertThat(same).isEqualTo(warning).hasSameHashCodeAs(warning);
    assertThat(warning.toString()).contains("YELLOW");
    assertThat(warning.describe().body()).isEqualTo("Em vigor das 12:00 às 21:00 de 27/11.");
  }

  private static WeatherWarning warning(
      WarningLevel level, String type, String start, String end, String text, String zone) {
    return WeatherWarning.restore(
        UUID.randomUUID(),
        EVENT,
        type,
        level,
        Instant.parse(start),
        Instant.parse(end),
        text,
        ZoneId.of(zone));
  }
}
