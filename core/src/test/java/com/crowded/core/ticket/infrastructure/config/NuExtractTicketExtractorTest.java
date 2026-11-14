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

class NuExtractTicketExtractorTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final AtomicReference<String> request = new AtomicReference<>();
  private HttpServer server;

  /** A llama.cpp server stand-in that records the request and gives a fixed answer. */
  private String serve(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/completion",
        exchange -> {
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
  void sendsTheTicketTextWithTheTemplateAndReturnsTheModelsAnswer() throws IOException {
    String url = serve(200, "{\"content\":\"{\\\"eventCode\\\": \\\"MARG-2026\\\"}\"}");

    String answer = new NuExtractTicketExtractor(url, TIMEOUT).extract("Porta: A");

    assertThat(answer).isEqualTo("{\"eventCode\": \"MARG-2026\"}");
    assertThat(request.get())
        .contains("# Template:", "verbatim-string", "Porta: A", "\"temperature\":0");
  }

  @Test
  void failsWhenTheServerAnswersWithoutContent() throws IOException {
    String url = serve(200, "{}");

    TicketExtractor extractor = new NuExtractTicketExtractor(url, TIMEOUT);

    assertThatThrownBy(() -> extractor.extract("Porta: A"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsWhenTheServerFails() throws IOException {
    String url = serve(503, "{}");

    TicketExtractor extractor = new NuExtractTicketExtractor(url, TIMEOUT);

    assertThatThrownBy(() -> extractor.extract("Porta: A")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void usesNuExtractOnlyWhenItsUrlIsConfigured() {
    TicketExtractorConfiguration configuration = new TicketExtractorConfiguration();

    assertThat(configuration.ticketExtractor("http://localhost:1", TIMEOUT))
        .isInstanceOf(NuExtractTicketExtractor.class);
    TicketExtractor none = configuration.ticketExtractor("", TIMEOUT);
    assertThatThrownBy(() -> none.extract("Porta: A")).isInstanceOf(IllegalStateException.class);
  }
}
