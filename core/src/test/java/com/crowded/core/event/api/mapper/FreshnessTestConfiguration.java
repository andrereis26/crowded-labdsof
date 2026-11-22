package com.crowded.core.event.api.mapper;

import com.crowded.core.event.domain.model.InformationKind;
import com.crowded.core.event.domain.service.FreshnessPolicy;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** The freshness rule with the periods of {@code application.yml}, for the controller slices. */
@TestConfiguration
@Import(FreshnessViews.class)
public class FreshnessTestConfiguration {

  @Bean
  FreshnessPolicy freshnessPolicy() {
    return new FreshnessPolicy(
        Map.of(
            InformationKind.CROWD, Duration.ofMinutes(20),
            InformationKind.QUEUE, Duration.ofMinutes(20),
            InformationKind.ESTIMATE, Duration.ofMinutes(30),
            InformationKind.WEATHER, Duration.ofHours(3)));
  }
}
