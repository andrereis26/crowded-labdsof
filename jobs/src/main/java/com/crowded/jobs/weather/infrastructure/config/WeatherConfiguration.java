package com.crowded.jobs.weather.infrastructure.config;

import com.crowded.jobs.weather.domain.repository.WeatherSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** IPMA, the weather source; its address changes only to point tests at a stand-in. */
@Configuration(proxyBeanMethods = false)
class WeatherConfiguration {

  @Bean
  WeatherSource weatherSource(
      @Value("${crowded.weather.ipma-url:https://api.ipma.pt/open-data}") String url,
      @Value("${crowded.weather.timeout:10s}") Duration timeout,
      @Value("${crowded.weather.first-retry:1s}") Duration firstRetry,
      JsonMapper json,
      MeterRegistry meters) {
    return new IpmaWeatherSource(url, timeout, firstRetry, json, meters);
  }
}
