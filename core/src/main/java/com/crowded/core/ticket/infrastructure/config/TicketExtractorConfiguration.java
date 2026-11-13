package com.crowded.core.ticket.infrastructure.config;

import com.crowded.core.ticket.domain.repository.TicketExtractor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The AI provider for ticket extraction (ADR 0010). None is chosen yet, so every extraction fails
 * and the participant types the event code; a provider's adapter replaces this bean.
 */
@Configuration(proxyBeanMethods = false)
class TicketExtractorConfiguration {

  @Bean
  @ConditionalOnMissingBean
  TicketExtractor ticketExtractor() {
    return text -> {
      throw new IllegalStateException("No AI provider is configured");
    };
  }
}
