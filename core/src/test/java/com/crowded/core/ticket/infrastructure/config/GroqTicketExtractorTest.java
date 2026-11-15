package com.crowded.core.ticket.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.ticket.domain.repository.TicketExtractor;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GroqTicketExtractorTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final String MODEL = "qwen/qwen3.8-27b";

  private final AtomicReference<String> request = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private HttpServer server;

  /** A Groq stand-in that records the request and gives a fixed answer. */
  private String serve(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] answer = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, answer.length);
          exchange.getResponseBody().write(answer);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void sendsTheTicketTextAndReturnsTheModelsAnswer() throws IOException {
    String url =
        serve(
            200,
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"eventCode\\\":\\\"MARG-2026\\\"}\"}}]}");

    String answer = new GroqTicketExtractor(url, "key", MODEL, TIMEOUT).extract("Porta: A");

    assertThat(answer).isEqualTo("{\"eventCode\":\"MARG-2026\"}");
    assertThat(authorization.get()).isEqualTo("Bearer key");
    assertThat(request.get())
        .contains(
            MODEL,
            "Porta: A",
            "json_object",
            "\"temperature\":0",
            "max_completion_tokens",
            "eventCode");
  }

  @Test
  void failsWhenTheProviderAnswersWithoutContent() throws IOException {
    String url = serve(200, "{\"choices\":[]}");

    TicketExtractor extractor = new GroqTicketExtractor(url, "key", MODEL, TIMEOUT);

    assertThatThrownBy(() -> extractor.extract("Porta: A"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsWhenTheProviderRefusesTheRequest() throws IOException {
    String url = serve(401, "{}");

    TicketExtractor extractor = new GroqTicketExtractor(url, "wrong", MODEL, TIMEOUT);

    assertThatThrownBy(() -> extractor.extract("Porta: A")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void usesGroqOnlyWhenItsKeyIsConfigured() {
    TicketExtractorConfiguration configuration = new TicketExtractorConfiguration();

    assertThat(configuration.ticketExtractor("http://localhost:1", "key", MODEL, TIMEOUT))
        .isInstanceOf(GroqTicketExtractor.class);
    TicketExtractor none = configuration.ticketExtractor("http://localhost:1", " ", MODEL, TIMEOUT);
    assertThatThrownBy(() -> none.extract("Porta: A")).isInstanceOf(IllegalStateException.class);
  }
}
