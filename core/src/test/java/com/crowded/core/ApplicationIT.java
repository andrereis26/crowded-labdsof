package com.crowded.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The service starts against PostgreSQL, RabbitMQ and Redis, reports itself and each of them
 * healthy, and gives Prometheus its metrics, all without a token.
 */
class ApplicationIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort private int port;
  @Autowired private JsonMapper json;

  @ParameterizedTest
  @ValueSource(
      strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
  void reportsUp(String path) throws IOException, InterruptedException {
    HttpResponse<String> response = get(path);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  @Test
  void reportsEachDependencyWithoutItsDetails() throws IOException, InterruptedException {
    JsonNode components = json.readTree(get("/actuator/health").body()).get("components");

    for (String dependency : new String[] {"db", "rabbit", "redis"}) {
      assertThat(components.get(dependency).get("status").asString()).isEqualTo("UP");
      assertThat(components.get(dependency).has("details")).isFalse();
    }
  }

  @Test
  void givesPrometheusTheRequestLatencyAndTheOutboxLag() throws IOException, InterruptedException {
    get("/actuator/health");

    HttpResponse<String> response = get("/actuator/prometheus");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("http_server_requests_seconds_bucket{")
        .contains("crowded_outbox_lag_seconds_count")
        .contains("crowded_outbox_waiting_seconds");
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return HTTP.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
