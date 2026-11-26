package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** IPMA's weather for an event: the forecast of its days and the warnings while it happens. */
class EventWeatherTest {

  private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
  private static final District PORTO = new District("Porto");
  // A night event: from 21:00 on the 27th to 02:00 on the 28th, Lisbon time (UTC in winter).
  private static final Instant START = Instant.parse("2026-11-27T21:00:00Z");
  private static final Instant END = Instant.parse("2026-11-28T02:00:00Z");

  private final Event event =
      StoredEvents.restore(
          UUID.randomUUID(),
          UUID.randomUUID(),
          new EventState(
              EventStatus.SCHEDULED,
              new Schedule(START, null, END),
              new Venue("Coliseu", "Porto", null)));
  private final EventWeather weather = EventWeather.none(event.getId());

  @Test
  void keepsTheForecastOfTheEventsDaysOnly() {
    WeatherForecast first = day("2026-11-27");
    WeatherForecast second = day("2026-11-28");

    List<WeatherWarning> notify =
        weather.apply(
            new ForecastSnapshot(PORTO, List.of(day("2026-11-26"), second, first)), event, NOW);

    assertThat(notify).isEmpty();
    assertThat(weather.getForecast()).containsExactly(first, second);
  }

  @Test
  void hasNoForecastBeforeIpmaForecastsTheEventsDays() {
    weather.apply(new ForecastSnapshot(PORTO, List.of(day("2026-11-27"))), event, NOW);

    weather.apply(new ForecastSnapshot(PORTO, List.of(day("2026-11-29"))), event, NOW);

    assertThat(weather.getForecast()).isEmpty();
  }

  @Test
  void notifiesANewOrangeWarningDuringTheEventOnce() {
    List<WeatherWarning> notify = warn(issued("Vento", WarningLevel.ORANGE));

    assertThat(notify).singleElement().satisfies(this::isNotifiedOrangeWind);
    assertThat(weather.getWarnings()).containsExactlyElementsOf(notify);
    assertThat(warn(issued("Vento", WarningLevel.ORANGE))).isEmpty();
  }

  @Test
  void showsAYellowWarningWithoutNotifyingIt() {
    assertThat(warn(issued("Nevoeiro", WarningLevel.YELLOW))).isEmpty();

    assertThat(weather.getWarnings())
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.getLevel()).isEqualTo(WarningLevel.YELLOW);
              assertThat(warning.getNotifiedLevel()).isNull();
            });
  }

  @Test
  void notifiesAWarningRaisedToRedOnceMore() {
    WeatherWarning orange = warn(issued("Vento", WarningLevel.ORANGE)).getFirst();

    List<WeatherWarning> notify = warn(issued("Vento", WarningLevel.RED));

    assertThat(notify).containsExactly(orange);
    assertThat(orange.getLevel()).isEqualTo(WarningLevel.RED);
    assertThat(orange.getNotifiedLevel()).isEqualTo(WarningLevel.RED);
    assertThat(orange.getLevel().severity()).isEqualTo(Severity.CRITICAL);
  }

  @Test
  void doesNotNotifyALevelAgainAfterIpmaLoweredAndRaisedIt() {
    WeatherWarning warning = warn(issued("Vento", WarningLevel.ORANGE)).getFirst();

    assertThat(warn(issued("Vento", WarningLevel.YELLOW))).isEmpty();
    assertThat(warn(issued("Vento", WarningLevel.ORANGE))).isEmpty();
    assertThat(warning.getNotifiedLevel()).isEqualTo(WarningLevel.ORANGE);
  }

  @Test
  void leavesOutTheWarningsOutsideTheEvent() {
    IssuedWarning before =
        new IssuedWarning(
            "Vento", WarningLevel.RED, START.minusSeconds(7200), START.minusSeconds(3600), null);
    IssuedWarning after =
        new IssuedWarning(
            "Vento", WarningLevel.RED, END.plusSeconds(1), END.plusSeconds(3600), null);

    assertThat(warn(before, after)).isEmpty();
    assertThat(weather.getWarnings()).isEmpty();
  }

  @Test
  void dropsTheWarningsIpmaNoLongerHas() {
    warn(issued("Vento", WarningLevel.ORANGE), issued("Nevoeiro", WarningLevel.YELLOW));

    warn(issued("Nevoeiro", WarningLevel.YELLOW));

    assertThat(weather.getWarnings())
        .extracting(WeatherWarning::getAwarenessType)
        .containsExactly("Nevoeiro");
  }

  @Test
  void updatesAWarningsTimeOnlyWhenIpmaChangedIt() {
    WeatherWarning warning = warn(issued("Vento", WarningLevel.ORANGE)).getFirst();
    Instant later = NOW.plusSeconds(600);

    weather.apply(
        new WarningsSnapshot(PORTO, later, List.of(issued("Vento", WarningLevel.ORANGE))),
        event,
        later);
    assertThat(warning.getProvenance()).isEqualTo(Provenance.ipma(NOW));

    weather.apply(
        new WarningsSnapshot(
            PORTO,
            later,
            List.of(new IssuedWarning("Vento", WarningLevel.ORANGE, START, END, "Mais forte."))),
        event,
        later);
    assertThat(warning.getProvenance()).isEqualTo(Provenance.ipma(later));
    assertThat(warning.getDescription()).isEqualTo("Mais forte.");
  }

  @Test
  void listsTheMostSevereWarningFirst() {
    warn(issued("Nevoeiro", WarningLevel.YELLOW), issued("Vento", WarningLevel.RED));

    assertThat(weather.getWarnings())
        .extracting(WeatherWarning::getLevel)
        .containsExactly(WarningLevel.RED, WarningLevel.YELLOW);
  }

  @Test
  void isTheSameWeatherForTheSameEvent() {
    EventWeather restored = EventWeather.restore(event.getId(), List.of(), List.of());

    assertThat(restored).isEqualTo(weather).hasSameHashCodeAs(weather);
    assertThat(restored.getEventId()).isEqualTo(event.getId());
    assertThat(restored.toString()).contains(event.getId().toString());
  }

  @Test
  void aWarningIsTheSameWhenRestored() {
    WeatherWarning warning = warn(issued("Vento", WarningLevel.ORANGE)).getFirst();

    WeatherWarning restored =
        WeatherWarning.restore(
            warning.getId(),
            event.getId(),
            "Vento",
            START,
            END,
            WarningLevel.ORANGE,
            WarningLevel.ORANGE,
            null,
            Provenance.ipma(NOW));

    assertThat(restored).isEqualTo(warning).hasSameHashCodeAs(warning);
    assertThat(restored.getEventId()).isEqualTo(event.getId());
    assertThat(restored.toString()).contains("Vento", "ORANGE");
  }

  @Test
  void refusesImpossibleWeather() {
    assertThatThrownBy(() -> new IssuedWarning("Vento", WarningLevel.RED, END, START, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new WeatherForecast(
                    LocalDate.parse("2026-11-27"), "Chuvisco", 15, 9, 80, Provenance.ipma(NOW)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new WeatherForecast(
                    LocalDate.parse("2026-11-27"), "Chuvisco", 9, 15, 101, Provenance.ipma(NOW)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aScheduleKnowsItsDaysAndWhenItOverlapsAPeriod() {
    Schedule night = new Schedule(START, null, END);

    assertThat(night.days(ZoneId.of("Europe/Lisbon")))
        .containsExactly(LocalDate.parse("2026-11-27"), LocalDate.parse("2026-11-28"));
    assertThat(night.overlaps(END, END.plusSeconds(60))).isTrue();
    assertThat(night.overlaps(START.minusSeconds(60), START)).isTrue();
    assertThat(night.overlaps(END.plusSeconds(1), END.plusSeconds(60))).isFalse();
    assertThat(new Schedule(START, null, null).days(ZoneId.of("Atlantic/Azores")))
        .containsExactly(LocalDate.parse("2026-11-27"));
  }

  @Test
  void aDistrictUsesItsLegalTime() {
    assertThat(PORTO.zone()).isEqualTo(ZoneId.of("Europe/Lisbon"));
    assertThat(new District("Região Autónoma dos Açores").zone())
        .isEqualTo(ZoneId.of("Atlantic/Azores"));
    assertThat(new District("Região Autónoma da Madeira").zone())
        .isEqualTo(ZoneId.of("Atlantic/Madeira"));
  }

  @Test
  void onlyOrangeAndRedAreNotified() {
    assertThat(WarningLevel.YELLOW.isNotified()).isFalse();
    assertThat(WarningLevel.ORANGE.isNotified()).isTrue();
    assertThat(WarningLevel.ORANGE.severity()).isEqualTo(Severity.WARNING);
    assertThat(WarningLevel.YELLOW.severity()).isEqualTo(Severity.INFO);
  }

  private List<WeatherWarning> warn(IssuedWarning... issued) {
    return weather.apply(new WarningsSnapshot(PORTO, NOW, List.of(issued)), event, NOW);
  }

  private void isNotifiedOrangeWind(WeatherWarning warning) {
    assertThat(warning.getEventId()).isEqualTo(event.getId());
    assertThat(warning.getAwarenessType()).isEqualTo("Vento");
    assertThat(warning.getLevel()).isEqualTo(WarningLevel.ORANGE);
    assertThat(warning.getNotifiedLevel()).isEqualTo(WarningLevel.ORANGE);
    assertThat(warning.getStart()).isEqualTo(START);
    assertThat(warning.getEnd()).isEqualTo(END);
    assertThat(warning.getDescription()).isEqualTo("Rajadas.");
    assertThat(warning.getProvenance()).isEqualTo(Provenance.ipma(NOW));
  }

  private static IssuedWarning issued(String type, WarningLevel level) {
    return new IssuedWarning(type, level, START, END, "Rajadas.");
  }

  private static WeatherForecast day(String date) {
    return new WeatherForecast(LocalDate.parse(date), "Céu limpo", 8, 16, 0, Provenance.ipma(NOW));
  }
}
