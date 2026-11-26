package com.crowded.jobs.weather.infrastructure.config;

import com.crowded.jobs.weather.application.service.WeatherWatch;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the weather watch every 5 minutes, so a new IPMA warning is detected within 10 minutes of
 * its publication. A run starts only after the previous one ended.
 */
@Component
class WeatherSchedule {

  private final WeatherWatch watch;

  WeatherSchedule(WeatherWatch watch) {
    this.watch = watch;
  }

  @Scheduled(
      fixedDelayString = "${crowded.weather.interval:5m}",
      initialDelayString = "${crowded.weather.initial-delay:20s}")
  void run() {
    watch.check();
  }
}
