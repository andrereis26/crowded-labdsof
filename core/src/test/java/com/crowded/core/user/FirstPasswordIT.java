package com.crowded.core.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A member's first sign-in through the HTTP API: the temporary password from the administrator only
 * lets them choose their own, and stops working once they do or once it expires.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class FirstPasswordIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final UUID ADMIN = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final String CHOSEN = "A-minha-pr0pria"; // ggignore

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  @Test
  void aMemberReplacesTheTemporaryPasswordAndSignsIn() throws Exception {
    String email = "primeiro-" + UUID.randomUUID() + "@it.test";
    JsonNode added = addMember(email);
    String temporary = added.get("temporaryPassword").asString();
    UUID member = UUID.fromString(added.get("userId").asString());

    HttpResponse<String> withTemporary = signIn(email, temporary);
    assertThat(withTemporary.statusCode()).isEqualTo(403);
    assertThat(code(withTemporary)).isEqualTo("PASSWORD_CHANGE_REQUIRED");
    assertThat(withTemporary.body()).doesNotContain("accessToken");

    assertThat(code(choose(email, temporary, "fraca"))).isEqualTo("PASSWORD_TOO_SHORT");
    assertThat(code(choose(email, temporary, temporary))).isEqualTo("PASSWORD_UNCHANGED");
    assertThat(code(choose(email, "errada", CHOSEN))).isEqualTo("INVALID_CREDENTIALS");

    HttpResponse<String> chosen = choose(email, temporary, CHOSEN);
    assertThat(chosen.statusCode()).isEqualTo(200);
    JsonNode session = json.readTree(chosen.body());
    assertThat(session.get("mustChangePassword").asBoolean()).isFalse();
    assertThat(session.get("user").get("role").asString()).isEqualTo("ORGANIZATION_MEMBER");
    // The token works: a member is refused the administration (403), not unauthenticated (401).
    assertThat(get("/admin/organizations", session.get("accessToken").asString()).statusCode())
        .isEqualTo(403);

    assertThat(code(signIn(email, temporary))).isEqualTo("INVALID_CREDENTIALS");
    assertThat(signIn(email, CHOSEN).statusCode()).isEqualTo(200);
    assertThat(code(choose(email, CHOSEN, "Outra-pr0pria!"))) // ggignore
        .isEqualTo("NO_TEMPORARY_PASSWORD");
    assertThat(
            jdbc.queryForObject(
                "SELECT must_change_password::text || ' ' || (temporary_password_expires_at IS NULL)"
                    + " FROM app_user WHERE id = ?",
                String.class,
                member))
        .isEqualTo("false true");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_log_entry WHERE actor_id = ? AND action = ?",
                Integer.class,
                member,
                "MEMBER_PASSWORD_CHOSEN"))
        .isOne();
  }

  @Test
  void anExpiredTemporaryPasswordIsRefusedWithItsReason() throws Exception {
    String email = "expirado-" + UUID.randomUUID() + "@it.test";
    JsonNode added = addMember(email);
    String temporary = added.get("temporaryPassword").asString();
    jdbc.update(
        "UPDATE app_user SET temporary_password_expires_at = now() - interval '1 minute'"
            + " WHERE id = ?",
        UUID.fromString(added.get("userId").asString()));

    HttpResponse<String> signIn = signIn(email, temporary);
    assertThat(signIn.statusCode()).isEqualTo(403);
    assertThat(code(signIn)).isEqualTo("TEMPORARY_PASSWORD_EXPIRED");
    assertThat(code(choose(email, temporary, CHOSEN))).isEqualTo("TEMPORARY_PASSWORD_EXPIRED");
  }

  private JsonNode addMember(String email) throws Exception {
    HttpResponse<String> added =
        send(
            HttpRequest.newBuilder(
                    uri("/admin/organizations/20000000-0000-4000-8000-000000000001/members"))
                .header("Authorization", "Bearer " + token(ADMIN))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"name\": \"Membro IT\", \"email\": \"" + email + "\"}")));
    assertThat(added.statusCode()).isEqualTo(201);
    return json.readTree(added.body());
  }

  private String code(HttpResponse<String> response) {
    return json.readTree(response.body()).get("code").asString();
  }

  private HttpResponse<String> signIn(String email, String password)
      throws IOException, InterruptedException {
    return post(
        "/auth/login", "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}");
  }

  private HttpResponse<String> choose(String email, String temporary, String chosen)
      throws IOException, InterruptedException {
    return post(
        "/auth/password",
        "{\"email\": \""
            + email
            + "\", \"temporaryPassword\": \""
            + temporary
            + "\", \"newPassword\": \""
            + chosen
            + "\"}");
  }

  private HttpResponse<String> get(String path, String token)
      throws IOException, InterruptedException {
    return send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token));
  }

  private HttpResponse<String> post(String path, String body)
      throws IOException, InterruptedException {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)));
  }

  private HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
