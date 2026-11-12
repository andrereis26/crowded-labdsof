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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Platform administration through the HTTP API (#140): organizations and members, the temporary
 * password shown once, deactivation that revokes the member's tokens, and the audit log.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class AdminIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final UUID ADMIN = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID PARTICIPANT = UUID.fromString("10000000-0000-4000-8000-000000000011");
  private static final UUID MEMBER = UUID.fromString("10000000-0000-4000-8000-000000000021");

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  @Test
  void createsAnOrganizationWithAMemberAndListsThem() throws Exception {
    String name = "Festival IT " + UUID.randomUUID();

    HttpResponse<String> created =
        send(ADMIN, "POST", "/admin/organizations", "{\"name\": \"" + name + "\"}");

    assertThat(created.statusCode()).isEqualTo(201);
    UUID organization =
        UUID.fromString(json.readTree(created.body()).get("organizationId").asString());
    assertThat(
            send(
                    ADMIN,
                    "POST",
                    "/admin/organizations",
                    "{\"name\": \"" + name.toUpperCase(Locale.ROOT) + "\"}")
                .statusCode())
        .isEqualTo(409);

    String email = "membro-" + UUID.randomUUID() + "@it.test";
    HttpResponse<String> added =
        send(
            ADMIN,
            "POST",
            "/admin/organizations/" + organization + "/members",
            "{\"name\": \"Rita Ferreira\", \"email\": \"" + email.toUpperCase(Locale.ROOT) + "\"}");

    assertThat(added.statusCode()).isEqualTo(201);
    JsonNode password = json.readTree(added.body());
    String temporary = password.get("temporaryPassword").asString();
    assertThat(temporary).matches("[A-Za-z2-9]{3}-[A-Za-z2-9]{3}-[A-Za-z2-9]{4}");
    assertThat(Instant.parse(password.get("expiresAt").asString()))
        .isBetween(
            Instant.now().plus(Duration.ofDays(7)).minusSeconds(60),
            Instant.now().plus(Duration.ofDays(7)).plusSeconds(60));
    UUID member = UUID.fromString(password.get("userId").asString());
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT email, name, role::text, organization_id, must_change_password, password_hash"
                + " FROM app_user WHERE id = ?",
            member);
    assertThat(row)
        .containsEntry("email", email)
        .containsEntry("name", "Rita Ferreira")
        .containsEntry("role", "ORGANIZATION_MEMBER")
        .containsEntry("organization_id", organization)
        .containsEntry("must_change_password", true);
    assertThat((String) row.get("password_hash")).startsWith("{bcrypt}").doesNotContain(temporary);

    HttpResponse<String> list = send(ADMIN, "GET", "/admin/organizations", null);

    assertThat(list.statusCode()).isEqualTo(200);
    JsonNode listed = null;
    for (JsonNode o : json.readTree(list.body())) {
      if (o.get("id").asString().equals(organization.toString())) {
        listed = o;
      }
    }
    assertThat(listed).isNotNull();
    assertThat(listed.get("name").asString()).isEqualTo(name);
    assertThat(listed.get("eventCount").asLong()).isZero();
    JsonNode listedMember = listed.get("members").get(0);
    assertThat(listedMember.get("email").asString()).isEqualTo(email);
    assertThat(listedMember.get("mustChangePassword").asBoolean()).isTrue();
    assertThat(listedMember.has("passwordHash")).isFalse();

    assertThat(audited(ADMIN, "ORGANIZATION_CREATED", organization)).isOne();
    assertThat(audited(ADMIN, "MEMBER_CREATED", member)).isOne();
  }

  @Test
  void deactivatingAMemberRevokesTheirTokens() throws Exception {
    UUID member = newMember();
    // A request the member's token reaches: the event does not exist (404), not 401.
    String someEvent = "/events/" + UUID.randomUUID() + "/changes";
    String cancel = "{\"type\": \"CANCELLATION\"}";
    String earlierToken = token(member);
    assertThat(send(earlierToken, "POST", someEvent, cancel).statusCode()).isEqualTo(404);

    HttpResponse<String> deactivated =
        send(ADMIN, "PATCH", "/admin/members/" + member, "{\"active\": false}");

    assertThat(deactivated.statusCode()).isEqualTo(200);
    assertThat(json.readTree(deactivated.body()).get("active").asBoolean()).isFalse();
    assertThat(send(earlierToken, "POST", someEvent, cancel).statusCode()).isEqualTo(401);
    assertThat(audited(ADMIN, "MEMBER_DEACTIVATED", member)).isOne();

    HttpResponse<String> reset =
        send(
            ADMIN,
            "PATCH",
            "/admin/members/" + member,
            "{\"active\": true, \"resetPassword\": true}");

    assertThat(reset.statusCode()).isEqualTo(200);
    JsonNode body = json.readTree(reset.body());
    assertThat(body.get("active").asBoolean()).isTrue();
    assertThat(body.get("temporaryPassword").asString()).hasSize(12);
    assertThat(audited(ADMIN, "MEMBER_REACTIVATED", member)).isOne();
    assertThat(audited(ADMIN, "MEMBER_PASSWORD_RESET", member)).isOne();
  }

  @Test
  void refusesEveryoneButTheAdministrator() throws Exception {
    for (UUID caller : new UUID[] {PARTICIPANT, MEMBER}) {
      HttpResponse<String> list = send(caller, "GET", "/admin/organizations", null);
      HttpResponse<String> create =
          send(caller, "POST", "/admin/organizations", "{\"name\": \"Intrusa\"}");

      assertThat(list.statusCode()).isEqualTo(403);
      assertThat(json.readTree(list.body()).get("code").asString()).isEqualTo("ADMINISTRATOR_ONLY");
      assertThat(create.statusCode()).isEqualTo(403);
    }
    assertThat(
            send(PARTICIPANT, "PATCH", "/admin/members/" + MEMBER, "{\"active\": false}")
                .statusCode())
        .isEqualTo(403);
    assertThat(
            jdbc.queryForObject("SELECT active FROM app_user WHERE id = ?", Boolean.class, MEMBER))
        .isTrue();
    assertThat(send((String) null, "GET", "/admin/organizations", null).statusCode())
        .isEqualTo(401);
  }

  @Test
  void refusesAMemberOfAnUnknownOrganizationAndAnUnknownMember() throws Exception {
    assertThat(
            send(
                    ADMIN,
                    "POST",
                    "/admin/organizations/" + UUID.randomUUID() + "/members",
                    "{\"name\": \"Rita\", \"email\": \"rita-" + UUID.randomUUID() + "@it.test\"}")
                .statusCode())
        .isEqualTo(404);
    assertThat(
            send(ADMIN, "PATCH", "/admin/members/" + PARTICIPANT, "{\"active\": false}")
                .statusCode())
        .isEqualTo(404);
    assertThat(
            send(
                    ADMIN,
                    "POST",
                    "/admin/organizations/20000000-0000-4000-8000-000000000001/members",
                    "{\"name\": \"\", \"email\": \"nao-e-email\"}")
                .statusCode())
        .isEqualTo(400);
  }

  private UUID newMember() throws Exception {
    HttpResponse<String> added =
        send(
            ADMIN,
            "POST",
            "/admin/organizations/20000000-0000-4000-8000-000000000001/members",
            "{\"name\": \"Membro IT\", \"email\": \"it-" + UUID.randomUUID() + "@it.test\"}");
    assertThat(added.statusCode()).isEqualTo(201);
    return UUID.fromString(json.readTree(added.body()).get("userId").asString());
  }

  private Integer audited(UUID actor, String action, UUID entity) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_log_entry WHERE actor_id = ? AND action = ? AND entity_id = ?",
        Integer.class,
        actor,
        action,
        entity);
  }

  private HttpResponse<String> send(UUID user, String method, String path, String body)
      throws IOException, InterruptedException {
    return send(token(user), method, path, body);
  }

  private HttpResponse<String> send(String token, String method, String path, String body)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
