package com.crowded.core.ticket.infrastructure.config;

import com.crowded.core.ticket.domain.repository.TicketExtractor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The AI model for ticket extraction (ADR 0010): NuExtract when its server's URL is configured.
 * Without one, every extraction fails and the participant types the event code.
 */
@Configuration(proxyBeanMethods = false)
class TicketExtractorConfiguration {

  @Bean
  TicketExtractor ticketExtractor(
      @Value("${crowded.ai.nuextract.url:}") String url,
      @Value("${crowded.ai.ticket-extraction-timeout:25s}") Duration timeout) {
    if (url.isBlank()) {
      return text -> {
        throw new IllegalStateException("No AI model is configured");
      };
    }
    return new NuExtractTicketExtractor(url, timeout);
  }
}
