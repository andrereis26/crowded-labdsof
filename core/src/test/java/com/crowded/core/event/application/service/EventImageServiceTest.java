package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventImage;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.StoredEvents;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventImageRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.domain.model.Image;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventImageServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();
  private static final UUID OTHER_MEMBER = UUID.randomUUID();
  private static final UUID PARTICIPANT = UUID.randomUUID();
  private static final byte[] WEBP = {
    'R', 'I', 'F', 'F', 18, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', 'L', 5, 0, 0, 0, 0x2F, 0, 0,
    0, 0, 0
  };

  private final EventRepository events = mock(EventRepository.class);
  private final EventImageRepository images = mock(EventImageRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final AuditLog audit = mock(AuditLog.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final EventImageService service =
      new EventImageService(
          events,
          images,
          new EventAccess(events, users, connections, meters),
          audit,
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);
  private final Event event =
      StoredEvents.restore(
          UUID.randomUUID(),
          ORGANIZATION,
          new EventState(
              EventStatus.SCHEDULED,
              new Schedule(NOW.plusSeconds(86_400), null, null),
              new Venue("Coliseu", "Porto", null)));

  @BeforeEach
  void givenTheEventAndItsUsers() {
    when(events.findById(event.getId())).thenReturn(Optional.of(event));
    when(users.findById(MEMBER))
        .thenReturn(
            Optional.of(User.restore(MEMBER, ORGANIZATION, Role.ORGANIZATION_MEMBER, true)));
    when(users.findById(OTHER_MEMBER))
        .thenReturn(
            Optional.of(
                User.restore(OTHER_MEMBER, UUID.randomUUID(), Role.ORGANIZATION_MEMBER, true)));
    when(users.findById(PARTICIPANT))
        .thenReturn(Optional.of(User.restore(PARTICIPANT, null, Role.PARTICIPANT, true)));
  }

  @Test
  void storesTheImageOfAMembersEventAndAuditsIt() {
    EventImage stored = service.replace(MEMBER, event.getId(), WEBP);

    ArgumentCaptor<EventImage> saved = ArgumentCaptor.forClass(EventImage.class);
    verify(images).save(saved.capture());
    assertThat(saved.getValue()).isEqualTo(stored);
    assertThat(stored.getImage()).isEqualTo(Image.restore("image/webp", WEBP));
    assertThat(stored.getUpdatedAt()).isEqualTo(NOW);
    verify(audit)
        .record(
            MEMBER,
            event.getId(),
            "EVENT_IMAGE_REPLACED",
            "Event",
            event.getId(),
            Map.of("mediaType", "image/webp", "bytes", WEBP.length));
    assertThat(meters.counter("crowded.event.images", "action", "replaced").count()).isOne();
  }

  @Test
  void refusesAnotherOrganizationsMemberAParticipantAndABadFile() {
    assertThatThrownBy(() -> service.replace(OTHER_MEMBER, event.getId(), WEBP))
        .isInstanceOf(NotAllowedException.class);
    assertThatThrownBy(() -> service.replace(PARTICIPANT, event.getId(), WEBP))
        .isInstanceOf(NotAllowedException.class);
    assertThatThrownBy(() -> service.remove(PARTICIPANT, event.getId()))
        .isInstanceOf(NotAllowedException.class);
    assertThatThrownBy(() -> service.replace(MEMBER, event.getId(), new byte[] {1, 2, 3}))
        .isInstanceOf(RuleViolationException.class);
    verify(images, never()).save(any());
    verify(images, never()).deleteByEventId(any());
  }

  @Test
  void refusesAnEventThatDoesNotExist() {
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(() -> service.replace(MEMBER, unknown, WEBP))
        .isInstanceOfSatisfying(
            NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("EVENT_NOT_FOUND"));
  }

  @Test
  void removesTheImageAndAuditsItOnlyWhenThereWasOne() {
    when(images.deleteByEventId(event.getId())).thenReturn(true, false);

    service.remove(MEMBER, event.getId());
    service.remove(MEMBER, event.getId());

    verify(audit)
        .record(MEMBER, event.getId(), "EVENT_IMAGE_REMOVED", "Event", event.getId(), Map.of());
    assertThat(meters.counter("crowded.event.images", "action", "removed").count()).isOne();
  }

  @Test
  void givesTheImageToWhoeverMaySeeTheEvent() {
    EventImage image = EventImage.upload(event.getId(), WEBP, NOW);
    when(images.findByEventId(event.getId())).thenReturn(Optional.of(image));

    assertThat(service.find(MEMBER, event.getId())).isEqualTo(image);
    assertThatThrownBy(() -> service.find(OTHER_MEMBER, event.getId()))
        .isInstanceOf(NotAllowedException.class);
    verify(audit, never())
        .record(any(), any(), eq("EVENT_IMAGE_REPLACED"), anyString(), any(), anyMap());
  }

  @Test
  void saysWhenTheEventHasNoImage() {
    assertThatThrownBy(() -> service.find(MEMBER, event.getId()))
        .isInstanceOfSatisfying(
            NotFoundException.class,
            e -> assertThat(e.getCode()).isEqualTo("EVENT_IMAGE_NOT_FOUND"));
  }
}
