package com.crowded.notifications.notification.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.notifications.IntegrationTest;
import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.application.service.ParticipantNotifier;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The notification center and the live stream, over HTTP with a participant's token, for official
 * changes stored as Core's {@code NotificationRequested} asks. Uses the demo data
 * (database/seed/R__demo_data.sql).
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class NotificationCenterIT extends IntegrationTest {

  // Rock no Parque, cancelled; Bruno has a ticket.
  private static final UUID ROCK = UUID.fromString("30000000-0000-4000-8000-000000000005");
  private static final UUID ROCK_CANCELLED =
      UUID.fromString("50000000-0000-4000-8000-000000000003");
  private static final UUID BRUNO = UUID.fromString("10000000-0000-4000-8000-000000000012");
  private static final UUID CARLA = UUID.fromString("10000000-0000-4000-8000-000000000013");

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  @LocalServerPort private int port;
  @Autowired private ParticipantNotifier notifier;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  @Test
  void aParticipantSeesTheirNotificationsNewestFirstAndReadsOne() throws Exception {
    UUID first = notifyBruno();
    UUID second = notifyBruno();

    HttpResponse<String> listed = get("/notifications", BRUNO);
    assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
    JsonNode before = json.readTree(listed.body());
    List<String> ids =
        before.get("notifications").valueStream().map(n -> n.get("id").asString()).toList();
    assertThat(ids).containsSubsequence(second.toString(), first.toString());
    JsonNode newest = before.get("notifications").get(ids.indexOf(second.toString()));
    assertThat(newest.get("type").asString()).isEqualTo("OFFICIAL_CHANGE");
    assertThat(newest.get("title").asString()).isEqualTo("Cancelado");
    assertThat(newest.get("eventId").asString()).isEqualTo(ROCK.toString());
    assertThat(newest.get("eventName").asString()).isEqualTo(eventName(ROCK));
    assertThat(newest.get("causeId").asString()).isEqualTo(ROCK_CANCELLED.toString());
    assertThat(newest.get("createdAt").asString()).isNotBlank();
    assertThat(newest.get("readAt").isNull()).isTrue();

    assertThat(post("/notifications/" + second + "/read", BRUNO).statusCode()).isEqualTo(204);
    assertThat(post("/notifications/" + second + "/read", BRUNO).statusCode()).isEqualTo(204);

    JsonNode after = json.readTree(get("/notifications", BRUNO).body());
    assertThat(after.get("unreadCount").asLong()).isEqualTo(before.get("unreadCount").asLong() - 1);
    assertThat(readAt(second)).isNotNull();
    assertThat(readAt(first)).isNull();
  }

  @Test
  void aParticipantCannotSeeOrReadAnotherParticipantsNotification() throws Exception {
    UUID brunos = notifyBruno();

    String carlas = get("/notifications", CARLA).body();
    HttpResponse<String> read = post("/notifications/" + brunos + "/read", CARLA);

    assertThat(carlas).doesNotContain(brunos.toString());
    assertThat(read.statusCode()).isEqualTo(404);
    assertThat(json.readTree(read.body()).get("code").asString())
        .isEqualTo("NOTIFICATION_NOT_FOUND");
    assertThat(readAt(brunos)).isNull();
  }

  @Test
  void anOpenStreamReceivesANewNotificationOnceAndStampsItDelivered() throws Exception {
    BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    CompletableFuture<HttpResponse<Stream<String>>> stream =
        HTTP.sendAsync(
            request("/notifications/stream", BRUNO).GET().build(),
            HttpResponse.BodyHandlers.ofLines());
    try {
      HttpResponse<Stream<String>> response = stream.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));
      Thread.ofVirtual().start(() -> response.body().forEach(lines::add));
      assertThat(next(lines, ":open")).as("the stream is open").isNotNull();

      NotifyParticipantsCommand command = command();
      notifier.notify(command);
      UUID id = idOf(command);

      assertThat(next(lines, "event:notification")).isNotNull();
      String data = next(lines, "data:");
      assertThat(data).isNotNull();
      JsonNode event = json.readTree(data.substring("data:".length()));
      assertThat(event.get("notification").get("id").asString()).isEqualTo(id.toString());
      assertThat(event.get("notification").get("eventName").asString()).isEqualTo(eventName(ROCK));
      assertThat(event.get("unreadCount").asLong()).isPositive();
      assertThat(deliveredAt(id)).isNotNull();

      notifier.notify(command);
      assertThat(next(lines, "event:notification", Duration.ofSeconds(2)))
          .as("a redelivered message sends no second banner")
          .isNull();
    } finally {
      stream.cancel(true);
      if (stream.isDone() && !stream.isCompletedExceptionally() && !stream.isCancelled()) {
        stream.join().body().close();
      }
    }
  }

  @Test
  void theStreamNeedsAToken() throws Exception {
    HttpResponse<Void> response =
        HTTP.send(
            HttpRequest.newBuilder(uri("/notifications/stream")).build(),
            HttpResponse.BodyHandlers.discarding());

    assertThat(response.statusCode()).isEqualTo(401);
  }

  private UUID notifyBruno() {
    NotifyParticipantsCommand command = command();
    notifier.notify(command);
    return idOf(command);
  }

  private static NotifyParticipantsCommand command() {
    return new NotifyParticipantsCommand(
        UUID.randomUUID(), NotificationType.OFFICIAL_CHANGE, ROCK, ROCK_CANCELLED, List.of(BRUNO));
  }

  private UUID idOf(NotifyParticipantsCommand command) {
    return jdbc.queryForObject(
        "SELECT id FROM notification WHERE deduplication_key = ?",
        UUID.class,
        Notification.deduplicationKey(BRUNO, command.messageId()));
  }

  private String eventName(UUID eventId) {
    return jdbc.queryForObject("SELECT name FROM event WHERE id = ?", String.class, eventId);
  }

  private Object readAt(UUID id) {
    return jdbc.queryForObject("SELECT read_at FROM notification WHERE id = ?", Object.class, id);
  }

  private Object deliveredAt(UUID id) {
    Instant deadline = Instant.now().plus(TIMEOUT);
    Object stamped = null;
    while (stamped == null && Instant.now().isBefore(deadline)) {
      stamped =
          jdbc.queryForObject(
              "SELECT delivered_at FROM notification WHERE id = ?", Object.class, id);
      if (stamped == null) {
        sleep();
      }
    }
    return stamped;
  }

  private static String next(BlockingQueue<String> lines, String prefix)
      throws InterruptedException {
    return next(lines, prefix, TIMEOUT);
  }

  /** The next line that starts with {@code prefix}, or null when none comes within {@code wait}. */
  private static String next(BlockingQueue<String> lines, String prefix, Duration wait)
      throws InterruptedException {
    Instant deadline = Instant.now().plus(wait);
    while (Instant.now().isBefore(deadline)) {
      String line =
          lines.poll(Duration.between(Instant.now(), deadline).toMillis(), TimeUnit.MILLISECONDS);
      if (line != null && line.startsWith(prefix)) {
        return line;
      }
    }
    return null;
  }

  private HttpResponse<String> get(String path, UUID user)
      throws IOException, InterruptedException {
    return HTTP.send(request(path, user).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, UUID user)
      throws IOException, InterruptedException {
    return HTTP.send(
        request(path, user).POST(HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder request(String path, UUID user) {
    return HttpRequest.newBuilder(uri(path))
        .header("Authorization", "Bearer " + token(user, Instant.now()));
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static void sleep() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted", e);
    }
  }
}
