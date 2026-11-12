package com.crowded.core.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registration and sign-in through the HTTP API, against the migrated database with its demo
 * accounts, whose password is {@code crowded-demo} (database/README.md).
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class AuthIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final String DEMO_PASSWORD = "crowded-demo";

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  @Test
  void registersAParticipantWhoSignsInAndUsesTheToken() throws Exception {
    String email = "IT-" + UUID.randomUUID() + "@Crowded.test";

    // A role in the request is ignored: nobody registers as an organization member.
    HttpResponse<String> registered =
        post(
            "/auth/register",
            "{\"email\": \""
                + email
                + "\", \"password\": \"frase-secreta\","
                + " \"role\": \"ORGANIZATION_MEMBER\"}");

    assertThat(registered.statusCode()).isEqualTo(201);
    JsonNode account = json.readTree(registered.body());
    assertThat(account.get("role").asString()).isEqualTo("PARTICIPANT");
    UUID userId = UUID.fromString(account.get("userId").asString());
    assertThat(
            jdbc.queryForObject(
                "SELECT email || ' ' || role || ' ' || active FROM app_user WHERE id = ?",
                String.class,
                userId))
        .isEqualTo(email.toLowerCase(Locale.ROOT) + " PARTICIPANT true");
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM app_user WHERE id = ?", String.class, userId))
        .startsWith("{bcrypt}$2a$10$")
        .doesNotContain("frase-secreta");

    HttpResponse<String> signedIn = signIn(email, "frase-secreta");

    assertThat(signedIn.statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT last_signed_in_at FROM app_user WHERE id = ?", Instant.class, userId))
        .isNotNull();
    JsonNode body = json.readTree(signedIn.body());
    assertThat(body.get("mustChangePassword").asBoolean()).isFalse();
    assertThat(body.get("user").get("id").asString()).isEqualTo(userId.toString());
    assertThat(body.get("user").get("role").asString()).isEqualTo("PARTICIPANT");
    assertThat(body.get("user").get("organizationId").isNull()).isTrue();
    Instant expiresAt = Instant.parse(body.get("expiresAt").asString());
    assertThat(expiresAt)
        .isBetween(
            Instant.now().plus(Duration.ofMinutes(59)), Instant.now().plus(Duration.ofMinutes(61)));

    // Core accepts the token it issued: the request reaches the endpoint, which refuses it.
    HttpResponse<String> withToken =
        send(
            HttpRequest.newBuilder(uri("/events/" + UUID.randomUUID() + "/changes"))
                .header("Authorization", "Bearer " + body.get("accessToken").asString())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"type\": \"CANCELLATION\"}")));
    assertThat(withToken.statusCode()).isEqualTo(404);
  }

  @Test
  void signsInADemoAccountWhateverTheEmailsCase() throws Exception {
    HttpResponse<String> response = signIn("Equipa@Ribeira-Live.test", DEMO_PASSWORD);

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode user = json.readTree(response.body()).get("user");
    assertThat(user.get("role").asString()).isEqualTo("ORGANIZATION_MEMBER");
    assertThat(user.get("organizationId").asString())
        .isEqualTo("20000000-0000-4000-8000-000000000001");
  }

  @Test
  void refusesWrongCredentialsAndADeactivatedMemberWithTheSameGenericError() throws Exception {
    HttpResponse<String> wrongPassword = signIn("ana@crowded.test", "nao-e-esta");
    HttpResponse<String> unknownEmail = signIn("ninguem-" + UUID.randomUUID() + "@x.pt", "x");
    HttpResponse<String> deactivated = signIn("antigo@ribeira-live.test", DEMO_PASSWORD);

    assertThat(wrongPassword.statusCode()).isEqualTo(401);
    assertThat(unknownEmail.statusCode()).isEqualTo(401);
    assertThat(deactivated.statusCode()).isEqualTo(401);
    assertThat(problem(wrongPassword))
        .isEqualTo(problem(unknownEmail))
        .isEqualTo(problem(deactivated))
        .isEqualTo("INVALID_CREDENTIALS: The email or the password is wrong.");
  }

  @Test
  void refusesAMemberWithATemporaryPassword() throws Exception {
    HttpResponse<String> response = signIn("novo@ribeira-live.test", DEMO_PASSWORD);

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(json.readTree(response.body()).get("code").asString())
        .isEqualTo("PASSWORD_CHANGE_REQUIRED");
    assertThat(json.readTree(response.body()).has("accessToken")).isFalse();
  }

  @Test
  void refusesASecondAccountWithTheSameEmailAndInvalidRequests() throws Exception {
    HttpResponse<String> taken =
        post(
            "/auth/register", "{\"email\": \"ANA@crowded.test\", \"password\": \"frase-secreta\"}");
    HttpResponse<String> shortPassword =
        post("/auth/register", "{\"email\": \"curta@crowded.test\", \"password\": \"curta\"}");
    HttpResponse<String> notAnEmail =
        post("/auth/register", "{\"email\": \"ana\", \"password\": \"frase-secreta\"}");

    assertThat(taken.statusCode()).isEqualTo(409);
    assertThat(json.readTree(taken.body()).get("code").asString())
        .isEqualTo("EMAIL_ALREADY_REGISTERED");
    assertThat(shortPassword.statusCode()).isEqualTo(400);
    assertThat(json.readTree(shortPassword.body()).get("code").asString())
        .isEqualTo("PASSWORD_TOO_SHORT");
    assertThat(notAnEmail.statusCode()).isEqualTo(400);
  }

  @Test
  void limitsTheSignInsOfOneEmail() throws Exception {
    String email = "limite-" + UUID.randomUUID() + "@crowded.test";
    for (int attempt = 1; attempt <= 10; attempt++) {
      assertThat(signIn(email, "errada").statusCode()).isEqualTo(401);
    }

    HttpResponse<String> limited = signIn(email, "errada");

    assertThat(limited.statusCode()).isEqualTo(429);
    assertThat(json.readTree(limited.body()).get("code").asString()).isEqualTo("RATE_LIMITED");
  }

  private String problem(HttpResponse<String> response) {
    JsonNode body = json.readTree(response.body());
    return body.get("code").asString() + ": " + body.get("detail").asString();
  }

  private HttpResponse<String> signIn(String email, String password)
      throws IOException, InterruptedException {
    return post(
        "/auth/login", "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}");
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
