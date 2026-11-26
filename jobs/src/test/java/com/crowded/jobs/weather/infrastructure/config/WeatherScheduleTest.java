package com.crowded.jobs.weather.infrastructure.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.crowded.jobs.weather.application.service.WeatherWatch;
import org.junit.jupiter.api.Test;

/** Each scheduled run is one run of the weather watch. */
class WeatherScheduleTest {

  @Test
  void runsTheWatch() {
    WeatherWatch watch = mock(WeatherWatch.class);

    new WeatherSchedule(watch).run();

    verify(watch).check();
  }
}
