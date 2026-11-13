package com.crowded.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * An event's image through the HTTP API, against the demo data (database/README.md): Ribeira Live
 * owns Festival Margens do Douro, Ana holds a ticket for it, Bruno does not.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class EventImageIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final UUID MARGENS = UUID.fromString("30000000-0000-4000-8000-000000000001");
  private static final UUID ANA = UUID.fromString("10000000-0000-4000-8000-000000000011");
  private static final UUID BRUNO = UUID.fromString("10000000-0000-4000-8000-000000000012");
  private static final UUID RIBEIRA_MEMBER =
      UUID.fromString("10000000-0000-4000-8000-000000000021");
  private static final UUID OTHER_MEMBER = UUID.fromString("10000000-0000-4000-8000-000000000022");
  private static final String SECRET = "Location 41.1579N 8.6291W";

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  @Test
  void theOrganizationUploadsReplacesAndRemovesTheImageThatConnectedParticipantsSee()
      throws Exception {
    byte[] file = pngWithText();

    HttpResponse<byte[]> uploaded = send(RIBEIRA_MEMBER, "PUT", file);
    assertThat(uploaded.statusCode()).isEqualTo(204);

    HttpResponse<byte[]> seen = send(ANA, "GET", null);
    assertThat(seen.statusCode()).isEqualTo(200);
    assertThat(seen.headers().firstValue("Content-Type")).hasValue("image/png");
    assertThat(seen.headers().firstValue("Cache-Control").orElseThrow())
        .contains("private")
        .contains("no-cache");
    assertThat(new String(seen.body(), StandardCharsets.ISO_8859_1)).doesNotContain(SECRET);
    String etag = seen.headers().firstValue("ETag").orElseThrow();
    HttpResponse<byte[]> unchanged =
        HTTP.send(
            request(ANA, "GET", null).header("If-None-Match", etag).build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(unchanged.statusCode()).isEqualTo(304);

    JsonNode mine =
        json.readTree(
            HTTP.send(
                    HttpRequest.newBuilder(uri("/organizations/me/events"))
                        .header("Authorization", "Bearer " + token(RIBEIRA_MEMBER))
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .body());
    JsonNode margens = null;
    for (JsonNode event : mine) {
      if (event.get("id").asString().equals(MARGENS.toString())) {
        margens = event;
      }
    }
    assertThat(margens).isNotNull();
    assertThat(margens.get("imageUpdatedAt").isNull()).isFalse();

    assertThat(send(RIBEIRA_MEMBER, "DELETE", null).statusCode()).isEqualTo(204);
    HttpResponse<byte[]> gone = send(ANA, "GET", null);
    assertThat(gone.statusCode()).isEqualTo(404);
    assertThat(code(gone)).isEqualTo("EVENT_IMAGE_NOT_FOUND");
    assertThat(send(RIBEIRA_MEMBER, "DELETE", null).statusCode()).isEqualTo(204);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_log_entry WHERE event_id = ? AND action = ?",
                Integer.class,
                MARGENS,
                "EVENT_IMAGE_REMOVED"))
        .isPositive();
  }

  @Test
  void refusesWhoMayNotSeeOrChangeTheEventAndFilesThatAreNotImages() throws Exception {
    byte[] file = pngWithText();

    HttpResponse<byte[]> otherOrganization = send(OTHER_MEMBER, "PUT", file);
    assertThat(otherOrganization.statusCode()).isEqualTo(403);
    assertThat(code(otherOrganization)).isEqualTo("NOT_EVENT_ORGANIZATION");
    assertThat(send(ANA, "PUT", file).statusCode()).isEqualTo(403);
    assertThat(send(BRUNO, "GET", null).statusCode()).isEqualTo(403);

    HttpResponse<byte[]> gif =
        send(RIBEIRA_MEMBER, "PUT", "GIF89a fake".getBytes(StandardCharsets.ISO_8859_1));
    assertThat(gif.statusCode()).isEqualTo(400);
    assertThat(code(gif)).isEqualTo("IMAGE_TYPE_NOT_ACCEPTED");
    HttpResponse<byte[]> large = send(RIBEIRA_MEMBER, "PUT", new byte[5 * 1024 * 1024 + 10]);
    assertThat(large.statusCode()).isEqualTo(400);
    assertThat(code(large)).isEqualTo("IMAGE_TOO_LARGE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_image WHERE event_id = ?", Integer.class, MARGENS))
        .isZero();
  }

  /** A real PNG with a text chunk standing for its metadata. */
  private static byte[] pngWithText() throws IOException {
    BufferedImage image = new BufferedImage(32, 18, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    byte[] png = out.toByteArray();
    byte[] data = ("Comment\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1);
    byte[] type = "tEXt".getBytes(StandardCharsets.ISO_8859_1);
    CRC32 crc = new CRC32();
    crc.update(type);
    crc.update(data);
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    file.write(png, 0, png.length - 12);
    file.writeBytes(bigEndian(data.length));
    file.writeBytes(type);
    file.writeBytes(data);
    file.writeBytes(bigEndian((int) crc.getValue()));
    file.write(png, png.length - 12, 12);
    return file.toByteArray();
  }

  private static byte[] bigEndian(int value) {
    return new byte[] {
      (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value
    };
  }

  private String code(HttpResponse<byte[]> response) {
    return json.readTree(new String(response.body(), StandardCharsets.UTF_8))
        .get("code")
        .asString();
  }

  private HttpResponse<byte[]> send(UUID user, String method, byte[] body)
      throws IOException, InterruptedException {
    return HTTP.send(request(user, method, body).build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  private HttpRequest.Builder request(UUID user, String method, byte[] body) {
    return HttpRequest.newBuilder(uri("/events/" + MARGENS + "/image"))
        .header("Authorization", "Bearer " + token(user))
        .header("Content-Type", "application/octet-stream")
        .method(
            method,
            body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
