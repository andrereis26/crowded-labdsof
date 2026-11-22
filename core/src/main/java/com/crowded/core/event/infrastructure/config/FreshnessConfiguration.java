package com.crowded.core.event.infrastructure.config;

import com.crowded.core.event.domain.model.InformationKind;
import com.crowded.core.event.domain.service.FreshnessPolicy;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Sets how long each kind of on-site information stays current, from {@code crowded.freshness}. */
@Configuration
class FreshnessConfiguration {

  @Bean
  FreshnessPolicy freshnessPolicy(
      @Value("${crowded.freshness.crowd}") Duration crowd,
      @Value("${crowded.freshness.queue}") Duration queue,
      @Value("${crowded.freshness.estimate}") Duration estimate,
      @Value("${crowded.freshness.weather}") Duration weather) {
    return new FreshnessPolicy(
        Map.of(
            InformationKind.CROWD, crowd,
            InformationKind.QUEUE, queue,
            InformationKind.ESTIMATE, estimate,
            InformationKind.WEATHER, weather));
  }
}
