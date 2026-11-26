package com.crowded.jobs.weather.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The topic exchange every message goes through (Asynchronous Flows). Each consumer declares its
 * own queue; Jobs only declares where it publishes.
 */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

  /** Name of the exchange. */
  static final String EXCHANGE = "crowded.events";

  @Bean
  TopicExchange crowdedEvents() {
    return new TopicExchange(EXCHANGE, true, false);
  }
}
