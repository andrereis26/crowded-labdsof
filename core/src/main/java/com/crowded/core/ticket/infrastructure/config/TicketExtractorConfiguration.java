package com.crowded.core.ticket.infrastructure.config;

import com.crowded.core.ticket.domain.repository.TicketExtractor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The AI model for ticket extraction (ADR 0020): Groq when its API key is configured. Without one,
 * every extraction fails and the participant types the event code.
 */
@Configuration(proxyBeanMethods = false)
class TicketExtractorConfiguration {

  @Bean
  TicketExtractor ticketExtractor(
      @Value("${crowded.ai.groq.url}") String url,
      @Value("${crowded.ai.groq.api-key:}") String apiKey,
      @Value("${crowded.ai.groq.model}") String model,
      @Value("${crowded.ai.ticket-extraction-timeout:25s}") Duration timeout) {
    if (apiKey.isBlank()) {
      return text -> {
        throw new IllegalStateException("No AI provider is configured");
      };
    }
    return new GroqTicketExtractor(url, apiKey.strip(), model, timeout);
  }
}
