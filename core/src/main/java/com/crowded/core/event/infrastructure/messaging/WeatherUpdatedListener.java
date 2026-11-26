package com.crowded.core.event.infrastructure.messaging;

import com.crowded.core.event.application.dto.ApplyWeatherCommand;
import com.crowded.core.event.application.service.WeatherService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes Jobs' {@code WeatherUpdated}. The message is acknowledged only after the weather is
 * committed; a failure is retried, then dead-lettered ({@link WeatherMessagingConfiguration}).
 */
@Component
class WeatherUpdatedListener {

  private final WeatherService weather;

  WeatherUpdatedListener(WeatherService weather) {
    this.weather = weather;
  }

  @RabbitListener(queues = WeatherMessagingConfiguration.QUEUE)
  void onWeatherUpdated(ApplyWeatherCommand message) {
    weather.apply(message.snapshotId());
  }
}
