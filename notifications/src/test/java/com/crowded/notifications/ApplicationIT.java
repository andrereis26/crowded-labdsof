package com.crowded.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The service starts against PostgreSQL, RabbitMQ and Redis and reports itself healthy. */
class ApplicationIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @ParameterizedTest
  @ValueSource(
      strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
  void reportsUp(String path) throws IOException, InterruptedException {
    HttpResponse<String> response =
        HTTP.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }
}
