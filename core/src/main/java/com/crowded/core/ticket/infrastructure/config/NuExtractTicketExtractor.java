package com.crowded.core.ticket.infrastructure.config;

import com.crowded.core.ticket.domain.repository.TicketExtractor;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Ticket extraction with NuExtract 2.0, an open-source model that runs locally behind a llama.cpp
 * server, so the ticket's text never leaves Crowded's infrastructure (ADR 0010). The model only
 * fills the extraction schema's fields with text copied from the ticket.
 */
final class NuExtractTicketExtractor implements TicketExtractor {

  // NuExtract's template: each field is copied verbatim from the ticket, or null.
  private static final String TEMPLATE =
      """
      {
          "eventCode": "verbatim-string",
          "gate": "verbatim-string",
          "sector": "verbatim-string",
          "seat": "verbatim-string"
      }""";

  // The model's chat format (Qwen2), applied here because llama.cpp's server cannot pass it. The
  // line breaks are the model's own, never the platform's.
  private static final String PROMPT_START =
      "<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n"
          + "<|im_start|>user\n# Template:\n"
          + TEMPLATE
          + "\n";
  private static final String PROMPT_END = "<|im_end|>\n<|im_start|>assistant\n";

  private static final int MAX_ANSWER_TOKENS = 160;

  private final RestClient client;

  NuExtractTicketExtractor(String baseUrl, Duration timeout) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
    requests.setReadTimeout(timeout);
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
  }

  @Override
  public String extract(String textWithoutPersonalData) {
    Map<String, Object> request =
        Map.of(
            "prompt",
            PROMPT_START + textWithoutPersonalData + PROMPT_END,
            "temperature",
            0,
            "n_predict",
            MAX_ANSWER_TOKENS,
            "stop",
            new String[] {"<|im_end|>"});
    JsonNode answer =
        client
            .post()
            .uri("/completion")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(JsonNode.class);
    if (answer == null || !answer.path("content").isString()) {
      throw new IllegalStateException("The model's server answered without content");
    }
    return answer.path("content").asString();
  }
}
