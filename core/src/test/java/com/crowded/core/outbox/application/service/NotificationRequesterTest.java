package com.crowded.core.outbox.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.crowded.core.outbox.domain.model.NotificationRequested;
import com.crowded.core.outbox.domain.repository.OutboxRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationRequesterTest {

  @Test
  void putsANotificationRequestForEveryConnectedParticipantInTheOutbox() {
    OutboxRepository outbox = mock(OutboxRepository.class);
    UUID event = UUID.randomUUID();
    UUID cause = UUID.randomUUID();

    NotificationRequested message =
        new NotificationRequester(outbox).request("OFFICIAL_CHANGE", event, cause);

    assertThat(message.type()).isEqualTo("OFFICIAL_CHANGE");
    assertThat(message.eventId()).isEqualTo(event);
    assertThat(message.causeId()).isEqualTo(cause);
    assertThat(message.userIds()).isEmpty();
    verify(outbox).add(message);
  }
}
