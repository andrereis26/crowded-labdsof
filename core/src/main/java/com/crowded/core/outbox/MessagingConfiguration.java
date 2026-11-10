package com.crowded.core.outbox;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The topic exchange every message goes through (Asynchronous Flows). Each consumer declares its
 * own queue and dead-letter queue; Core only declares where it publishes.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class MessagingConfiguration {

  /** Name of the exchange. */
  static final String EXCHANGE = "crowded.events";

  @Bean
  TopicExchange crowdedEvents() {
    return new TopicExchange(EXCHANGE, true, false);
  }
}
