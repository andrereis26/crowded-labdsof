package com.crowded.jobs.weather.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.jobs.weather.domain.exception.InvalidWeatherDataException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What IPMA sends is checked before it becomes a warning or a forecast. */
class WeatherContentTest {

  private static final Instant START = Instant.parse("2026-11-27T18:00:00Z");
  private static final Instant END = Instant.parse("2026-11-28T03:00:00Z");
  private static final LocalDate DAY = LocalDate.parse("2026-11-27");

  @Test
  void keepsAWarningWithItsTypeAndTextStripped() {
    Warning warning = new Warning(" Vento ", WarningLevel.ORANGE, START, END, " Rajadas. ");

    assertThat(warning.awarenessType()).isEqualTo("Vento");
    assertThat(warning.description()).isEqualTo("Rajadas.");
    assertThat(new Warning("Vento", WarningLevel.ORANGE, START, END, " ").description()).isNull();
  }

  @Test
  void refusesAWarningWithoutItsTypeLevelOrTimes() {
    assertInvalid(() -> new Warning(" ", WarningLevel.RED, START, END, null));
    assertInvalid(() -> new Warning(null, WarningLevel.RED, START, END, null));
    assertInvalid(() -> new Warning("Vento", null, START, END, null));
    assertInvalid(() -> new Warning("Vento", WarningLevel.RED, null, END, null));
    assertInvalid(() -> new Warning("Vento", WarningLevel.RED, START, null, null));
  }

  @Test
  void refusesAWarningThatEndsBeforeItStarts() {
    assertInvalid(() -> new Warning("Vento", WarningLevel.YELLOW, END, START, null));
  }

  @Test
  void warningsAreEqualWhateverOrderIpmaSentThemIn() {
    Warning wind = new Warning("Vento", WarningLevel.YELLOW, START, END, null);
    Warning rain = new Warning("Precipitação", WarningLevel.ORANGE, START, END, null);

    assertThat(new Warnings(List.of(wind, rain))).isEqualTo(new Warnings(List.of(rain, wind)));
    assertThat(new Warnings(List.of(wind)).kind()).isEqualTo(SnapshotKind.WARNINGS);
  }

  @Test
  void keepsAForecastDayWithItsConditionsStripped() {
    ForecastDay day = new ForecastDay(DAY, " Céu limpo ", 8.3, 16.6, 0);

    assertThat(day.conditions()).isEqualTo("Céu limpo");
  }

  @ParameterizedTest
  @CsvSource({
    "Céu limpo, 17, 16, 10",
    "Céu limpo, 8, 16, -1",
    "Céu limpo, 8, 16, 101",
    "Céu limpo, NaN, 16, 10",
    "'', 8, 16, 10",
  })
  void refusesAForecastDayThatIsNotPossible(
      String conditions, double min, double max, double precipitation) {
    assertInvalid(() -> new ForecastDay(DAY, conditions, min, max, precipitation));
  }

  @Test
  void refusesAForecastDayWithoutItsDate() {
    assertInvalid(() -> new ForecastDay(null, "Céu limpo", 8, 16, 0));
  }

  @Test
  void ordersAForecastByDay() {
    ForecastDay first = new ForecastDay(DAY, "Céu limpo", 8, 16, 0);
    ForecastDay second = new ForecastDay(DAY.plusDays(1), "Chuvisco", 9, 14, 80);

    Forecast forecast = new Forecast(START, List.of(second, first));

    assertThat(forecast.days()).containsExactly(first, second);
    assertThat(forecast.kind()).isEqualTo(SnapshotKind.FORECAST);
  }

  @Test
  void refusesAForecastWithoutItsTimeOrDaysOrWithADayTwice() {
    ForecastDay day = new ForecastDay(DAY, "Céu limpo", 8, 16, 0);

    assertInvalid(() -> new Forecast(null, List.of(day)));
    assertInvalid(() -> new Forecast(START, List.of()));
    assertInvalid(() -> new Forecast(START, null));
    assertInvalid(() -> new Forecast(START, List.of(day, day)));
  }

  private static void assertInvalid(Runnable creation) {
    assertThatThrownBy(creation::run).isInstanceOf(InvalidWeatherDataException.class);
  }
}
