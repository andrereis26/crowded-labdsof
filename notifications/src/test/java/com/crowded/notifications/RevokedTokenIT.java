package com.crowded.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

/** A token Core revoked in Redis is refused here too (ADR 0004). */
@ResourceLock(IntegrationTest.SHARED_STATE)
class RevokedTokenIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @LocalServerPort private int port;
  @Autowired private StringRedisTemplate redis;

  @Test
  void refusesATokenIssuedBeforeItsUsersTokensWereRevoked() throws Exception {
    UUID user = UUID.randomUUID();
    Instant revokedAt = Instant.now();
    String earlier = token(user, revokedAt.minusSeconds(5));
    String later = token(user, revokedAt.plusMillis(1));
    // Accepted: no endpoint answers the path yet, so 404, not 401.
    assertThat(get(earlier)).isEqualTo(404);

    redis
        .opsForValue()
        .set(
            "revoked-before:" + user,
            Long.toString(revokedAt.toEpochMilli()),
            Duration.ofMinutes(5));

    assertThat(get(earlier)).isEqualTo(401);
    assertThat(get(later)).isEqualTo(404);
  }

  private int get(String token) throws IOException, InterruptedException {
    return HTTP.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/notifications"))
                .header("Authorization", "Bearer " + token)
                .build(),
            HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }
}
