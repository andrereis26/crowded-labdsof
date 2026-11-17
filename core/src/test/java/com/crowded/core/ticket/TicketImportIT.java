package com.crowded.core.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import com.crowded.core.ticket.domain.repository.TicketImportRepository;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A participant imports a ticket PDF through the API. */
@ResourceLock(IntegrationTest.SHARED_STATE)
class TicketImportIT extends IntegrationTest {

  // Ana, a participant of the demo data (database/README.md).
  private static final UUID PARTICIPANT = UUID.fromString("10000000-0000-4000-8000-000000000011");
  // Bruno and Carla, the other demo participants, each for the tests that count their imports.
  private static final UUID BRUNO = UUID.fromString("10000000-0000-4000-8000-000000000012");
  private static final UUID CARLA = UUID.fromString("10000000-0000-4000-8000-000000000013");
  // A member of Ribeira Live in the demo data.
  private static final UUID MEMBER = UUID.fromString("10000000-0000-4000-8000-000000000021");
  private static final String ENTRY_CODE = "QR-IT-" + UUID.randomUUID();

  @LocalServerPort private int port;
  @Autowired private StringRedisTemplate redis;
  @Autowired private TicketImportRepository imports;
  @Autowired private JsonMapper json;

  private RestClient client;

  @BeforeEach
  void createClient() {
    client = RestClient.create("http://localhost:" + port);
  }

  private record Response(HttpStatusCode status, JsonNode body) {}

  private Response upload(byte[] file, String token) {
    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add(
        "file",
        new ByteArrayResource(file) {
          @Override
          public String getFilename() {
            return "bilhete.pdf";
          }
        });
    RestClient.RequestBodySpec request =
        client.post().uri("/tickets").contentType(MediaType.MULTIPART_FORM_DATA).body(parts);
    if (token != null) {
      request = request.header("Authorization", "Bearer " + token);
    }
    return request.exchange(
        (req, res) -> {
          byte[] body = res.getBody().readAllBytes();
          return new Response(res.getStatusCode(), body.length == 0 ? null : json.readTree(body));
        });
  }

  private Set<String> pendingKeys() {
    return redis.keys("ticket-import:*");
  }

  @Test
  void keepsTheImportPendingAndAsksForTheEventCode() {
    Response response =
        upload(
            TicketPdfs.ticketOfEvent("SEM-EVENTO-" + ENTRY_CODE, ENTRY_CODE), token(PARTICIPANT));

    assertThat(response.status().value()).isEqualTo(200);
    assertThat(response.body().get("status").asString()).isEqualTo("NEEDS_EVENT_CODE");
    assertThat(response.body().get("ticket").get("gate").asString()).isEqualTo("A");
    UUID importId = UUID.fromString(response.body().get("importId").asString());
    assertThat(response.body().has("ticket")).isTrue();
    assertThat(imports.findById(importId))
        .hasValueSatisfying(pending -> assertThat(pending.getOwnerId()).isEqualTo(PARTICIPANT));
    assertThat(redis.opsForValue().get("ticket-import:" + importId))
        .doesNotContain(ENTRY_CODE, "Joana Silva", "123456789");
  }

  @Test
  void refusesATicketWithoutAQrCodeAndStoresNothing() {
    Set<String> before = pendingKeys();

    Response response = upload(TicketPdfs.withoutQrCode(), token(PARTICIPANT));

    assertThat(response.status().value()).isEqualTo(400);
    assertThat(response.body().get("code").asString()).isEqualTo("QR_CODE_NOT_FOUND");
    assertThat(pendingKeys()).isEqualTo(before);
  }

  @Test
  void refusesAFileThatIsNotAPdf() {
    Response response = upload("not a ticket".getBytes(StandardCharsets.UTF_8), token(PARTICIPANT));

    assertThat(response.status().value()).isEqualTo(400);
    assertThat(response.body().get("code").asString()).isEqualTo("QR_CODE_NOT_FOUND");
  }

  @Test
  void readsAFileUpToTheRoutersLimit() {
    byte[] twoMegabytes = new byte[2 * 1024 * 1024];

    Response response = upload(twoMegabytes, token(CARLA));

    assertThat(response.status().value()).isEqualTo(400);
    assertThat(response.body().get("code").asString()).isEqualTo("QR_CODE_NOT_FOUND");
  }

  @Test
  void limitsTheImportsOfOneParticipantPerHour() {
    String token = token(BRUNO);
    byte[] notAPdf = "not a ticket".getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < 20; i++) {
      assertThat(upload(notAPdf, token).status().value()).isEqualTo(400);
    }

    Response response = upload(TicketPdfs.ticket(ENTRY_CODE), token);

    assertThat(response.status().value()).isEqualTo(429);
    assertThat(response.body().get("code").asString()).isEqualTo("RATE_LIMITED");
  }

  @Test
  void letsOnlyParticipantsImport() {
    Response response = upload(TicketPdfs.ticket(ENTRY_CODE), token(MEMBER));

    assertThat(response.status().value()).isEqualTo(403);
    assertThat(response.body().get("code").asString()).isEqualTo("PARTICIPANT_ONLY");
  }

  @Test
  void requiresASignedInUser() {
    assertThat(upload(TicketPdfs.ticket(ENTRY_CODE), null).status().value()).isEqualTo(401);
  }
}
