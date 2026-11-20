package com.crowded.core.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import com.crowded.core.user.domain.model.RefreshToken;
import com.crowded.core.user.domain.model.Session;
import com.crowded.core.user.domain.model.SessionLifetime;
import com.crowded.core.user.domain.repository.Sessions;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sessions through the HTTP API, against Redis (ADR 0021): cookies for browsers, tokens in the body
 * for the phone app, refresh, sign-out and the Origin check.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class SessionIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final String PASSWORD = "Frase-secr3ta"; // ggignore

  @LocalServerPort private int port;
  @Autowired private JsonMapper json;
  @Autowired private Sessions sessions;

  @Test
  void keepsABrowsersSessionInCookiesThatScriptsCannotRead() throws Exception {
    String email = participant();

    HttpResponse<String> signedIn = send(fromBrowser("/auth/login", signInBody(email, "COOKIE")));

    assertThat(signedIn.statusCode()).isEqualTo(200);
    JsonNode body = json.readTree(signedIn.body());
    assertThat(body.has("accessToken")).isFalse();
    assertThat(body.has("refreshToken")).isFalse();
    assertThat(body.get("user").get("role").asString()).isEqualTo("PARTICIPANT");
    Instant refreshExpiresAt = Instant.parse(body.get("refreshExpiresAt").asString());
    assertThat(refreshExpiresAt)
        .isBetween(
            Instant.now().plus(Duration.ofDays(30)).minusSeconds(60),
            Instant.now().plus(Duration.ofDays(30)));
    Map<String, String> setCookies = setCookies(signedIn);
    assertThat(setCookies.get("crowded_access"))
        .contains("Path=/api", "Max-Age=", "Secure", "HttpOnly", "SameSite=Strict");
    assertThat(setCookies.get("crowded_refresh"))
        .contains("Path=/api/auth", "Secure", "HttpOnly", "SameSite=Strict");

    // The cookie alone signs the browser in.
    HttpResponse<String> me =
        send(HttpRequest.newBuilder(uri("/me")).header("Cookie", cookies(signedIn)).GET());
    assertThat(me.statusCode()).isEqualTo(200);
    JsonNode account = json.readTree(me.body());
    assertThat(account.get("email").asString()).isEqualTo(email);
    assertThat(account.get("role").asString()).isEqualTo("PARTICIPANT");
    assertThat(send(HttpRequest.newBuilder(uri("/me")).GET()).statusCode()).isEqualTo(401);

    // Refreshing with the cookie gives new cookies, and the old refresh token stops working.
    HttpResponse<String> refreshed =
        send(fromBrowser("/auth/refresh", "").header("Cookie", cookies(signedIn)));
    assertThat(refreshed.statusCode()).isEqualTo(200);
    assertThat(json.readTree(refreshed.body()).has("refreshToken")).isFalse();
    assertThat(cookies(refreshed)).isNotEqualTo(cookies(signedIn));
    assertThat(
            send(HttpRequest.newBuilder(uri("/me")).header("Cookie", cookies(refreshed)).GET())
                .statusCode())
        .isEqualTo(200);

    // Signing out revokes the access token at once and ends the session.
    HttpResponse<String> signedOut =
        send(fromBrowser("/auth/logout", "").header("Cookie", cookies(refreshed)));
    assertThat(signedOut.statusCode()).isEqualTo(204);
    assertThat(setCookies(signedOut).get("crowded_access")).contains("Max-Age=0");
    assertThat(setCookies(signedOut).get("crowded_refresh")).contains("Max-Age=0");
    assertThat(
            send(HttpRequest.newBuilder(uri("/me")).header("Cookie", cookies(refreshed)).GET())
                .statusCode())
        .isEqualTo(401);
    HttpResponse<String> afterSignOut =
        send(fromBrowser("/auth/refresh", "").header("Cookie", cookies(refreshed)));
    assertThat(afterSignOut.statusCode()).isEqualTo(401);
    assertThat(json.readTree(afterSignOut.body()).get("code").asString())
        .isEqualTo("INVALID_REFRESH_TOKEN");
  }

  @Test
  void givesThePhoneAppItsTokensInTheBody() throws Exception {
    String email = participant();

    HttpResponse<String> signedIn = send(post("/auth/login", signInBody(email, null)));

    assertThat(signedIn.statusCode()).isEqualTo(200);
    assertThat(signedIn.headers().allValues("Set-Cookie")).isEmpty();
    JsonNode body = json.readTree(signedIn.body());
    Instant expiresAt = Instant.parse(body.get("expiresAt").asString());
    assertThat(expiresAt)
        .isBetween(
            Instant.now().plus(Duration.ofMinutes(14)), Instant.now().plus(Duration.ofMinutes(16)));
    String refreshToken = body.get("refreshToken").asString();

    HttpResponse<String> refreshed =
        send(post("/auth/refresh", "{\"refreshToken\": \"" + refreshToken + "\"}"));
    assertThat(refreshed.statusCode()).isEqualTo(200);
    JsonNode next = json.readTree(refreshed.body());
    assertThat(next.get("refreshToken").asString()).isNotEqualTo(refreshToken);
    String accessToken = next.get("accessToken").asString();
    assertThat(
            send(HttpRequest.newBuilder(uri("/me"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET())
                .statusCode())
        .isEqualTo(200);

    // Seconds after the change, the replaced token is only refused: another tab may hold it.
    HttpResponse<String> replayed =
        send(post("/auth/refresh", "{\"refreshToken\": \"" + refreshToken + "\"}"));
    assertThat(replayed.statusCode()).isEqualTo(401);
    assertThat(json.readTree(replayed.body()).get("code").asString())
        .isEqualTo("REFRESH_TOKEN_ROTATED");

    HttpResponse<String> signedOut =
        send(
            post(
                    "/auth/logout",
                    "{\"refreshToken\": \"" + next.get("refreshToken").asString() + "\"}")
                .header("Authorization", "Bearer " + accessToken));
    assertThat(signedOut.statusCode()).isEqualTo(204);
    assertThat(
            send(HttpRequest.newBuilder(uri("/me"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET())
                .statusCode())
        .isEqualTo(401);
  }

  @Test
  void givesBackofficeAccountsShorterSessions() throws Exception {
    HttpResponse<String> signedIn =
        send(fromBrowser("/auth/login", signInBody("equipa@ribeira-live.test", "COOKIE")));

    assertThat(signedIn.statusCode()).isEqualTo(200);
    Instant refreshExpiresAt =
        Instant.parse(json.readTree(signedIn.body()).get("refreshExpiresAt").asString());
    assertThat(refreshExpiresAt)
        .isBetween(
            Instant.now().plus(Duration.ofHours(12)).minusSeconds(60),
            Instant.now().plus(Duration.ofHours(12)));
  }

  @Test
  void refusesChangesFromAnotherSite() throws Exception {
    String email = participant();
    HttpResponse<String> signedIn = send(fromBrowser("/auth/login", signInBody(email, "COOKIE")));

    HttpResponse<String> crossSite =
        send(
            post("/auth/logout", "")
                .header("Origin", "https://atacante.example")
                .header("Cookie", cookies(signedIn)));
    HttpResponse<String> withoutOrigin =
        send(post("/auth/refresh", "").header("Cookie", cookies(signedIn)));
    HttpResponse<String> loginFromAnotherSite =
        send(
            post("/auth/login", signInBody(email, "COOKIE"))
                .header("Origin", "https://atacante.example"));

    for (HttpResponse<String> refused : List.of(crossSite, withoutOrigin, loginFromAnotherSite)) {
      assertThat(refused.statusCode()).isEqualTo(403);
      assertThat(json.readTree(refused.body()).get("code").asString())
          .isEqualTo("ORIGIN_NOT_ALLOWED");
    }
    // The session survived the forged sign-out.
    assertThat(
            send(HttpRequest.newBuilder(uri("/me")).header("Cookie", cookies(signedIn)).GET())
                .statusCode())
        .isEqualTo(200);
  }

  @Test
  void storesARefreshOnlyOverTheSessionItRead() {
    UUID user = UUID.randomUUID();
    Instant now = Instant.now();
    SessionLifetime lifetime = new SessionLifetime(Duration.ofHours(12), Duration.ofDays(7));
    RefreshToken first = RefreshToken.generate(UUID.randomUUID(), new Random());
    RefreshToken second = RefreshToken.generate(first.sessionId(), new Random());
    RefreshToken third = RefreshToken.generate(first.sessionId(), new Random());
    sessions.save(Session.start(first, user, lifetime, now));

    // Two refreshes read the session at once: the first one stored wins.
    Session a = sessions.findById(first.sessionId()).orElseThrow();
    Session b = sessions.findById(first.sessionId()).orElseThrow();
    a.refresh(first, second, now);
    b.refresh(first, third, now);
    assertThat(sessions.replace(a, first.hash())).isTrue();
    assertThat(sessions.replace(b, first.hash())).isFalse();
    assertThat(sessions.findById(first.sessionId()).orElseThrow().getTokenHash())
        .isEqualTo(second.hash());

    // A session that ended meanwhile, such as a deactivated member's, is not stored again.
    Session late = sessions.findById(first.sessionId()).orElseThrow();
    late.refresh(second, third, now);
    sessions.endAllOf(user);
    assertThat(sessions.replace(late, second.hash())).isFalse();
    assertThat(sessions.findById(first.sessionId())).isEmpty();
  }

  /** Registers a participant and returns their email. */
  private String participant() throws IOException, InterruptedException {
    String email = "sessao-" + UUID.randomUUID() + "@crowded.test";
    HttpResponse<String> registered =
        send(
            post(
                "/auth/register",
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}"));
    assertThat(registered.statusCode()).isEqualTo(201);
    return email;
  }

  private static String signInBody(String email, String delivery) {
    // The demo accounts' password is crowded-demo (database/README.md).
    String password = email.startsWith("sessao-") ? PASSWORD : "crowded-demo";
    return "{\"email\": \""
        + email
        + "\", \"password\": \""
        + password
        + "\""
        + (delivery == null ? "" : ", \"delivery\": \"" + delivery + "\"")
        + "}";
  }

  /** A POST as the browser sends it: from a page of the same host. */
  private HttpRequest.Builder fromBrowser(String path, String body) {
    return post(path, body).header("Origin", "http://localhost:" + port);
  }

  private HttpRequest.Builder post(String path, String body) {
    return HttpRequest.newBuilder(uri(path))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
  }

  /** The cookies a response set, by name, with their attributes. */
  private static Map<String, String> setCookies(HttpResponse<String> response) {
    return response.headers().allValues("Set-Cookie").stream()
        .collect(Collectors.toMap(value -> value.substring(0, value.indexOf('=')), value -> value));
  }

  /** The {@code Cookie} header a browser would send back to {@code /api/auth}. */
  private static String cookies(HttpResponse<String> response) {
    return response.headers().allValues("Set-Cookie").stream()
        .map(value -> value.substring(0, value.indexOf(';')))
        .collect(Collectors.joining("; "));
  }

  private HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
