package com.crowded.core.event.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.crowded.core.event.application.dto.ApplyWeatherCommand;
import com.crowded.core.event.application.service.WeatherService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.MessageConversionException;

/** Jobs' WeatherUpdated applies its snapshot; only a message that cannot be read is not retried. */
class WeatherUpdatedListenerTest {

  @Test
  void appliesTheSnapshotOfTheMessage() {
    WeatherService weather = mock(WeatherService.class);
    UUID snapshot = UUID.randomUUID();

    new WeatherUpdatedListener(weather)
        .onWeatherUpdated(new ApplyWeatherCommand("Porto", snapshot));

    verify(weather).apply(snapshot);
  }

  @Test
  void retriesEverythingButAnUnreadableMessage() {
    assertThat(WeatherMessagingConfiguration.isRetryable(new IllegalStateException("db down")))
        .isTrue();
    assertThat(
            WeatherMessagingConfiguration.isRetryable(
                new IllegalStateException(new MessageConversionException("not JSON"))))
        .isFalse();
  }
}
