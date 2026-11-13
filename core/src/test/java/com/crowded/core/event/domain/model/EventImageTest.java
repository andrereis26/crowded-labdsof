package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.domain.model.Image;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventImageTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

  /** The smallest WebP the rule accepts: a header and a lossless picture chunk. */
  static final byte[] WEBP = {
    'R', 'I', 'F', 'F', 18, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', 'L', 5, 0, 0, 0, 0x2F, 0, 0,
    0, 0, 0
  };

  @Test
  void uploadsAnImageForTheEvent() {
    EventImage image = EventImage.upload(EVENT, WEBP, NOW);

    assertThat(image.getEventId()).isEqualTo(EVENT);
    assertThat(image.getImage()).isEqualTo(Image.restore("image/webp", WEBP));
    assertThat(image.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void refusesAFileThatIsNotAnAcceptedImage() {
    assertThatThrownBy(() -> EventImage.upload(EVENT, new byte[] {1, 2, 3}, NOW))
        .isInstanceOfSatisfying(
            RuleViolationException.class,
            e -> assertThat(e.getCode()).isEqualTo("IMAGE_TYPE_NOT_ACCEPTED"));
  }

  @Test
  void isTheEventsImageWhateverItsContent() {
    EventImage first = EventImage.upload(EVENT, WEBP, NOW);
    EventImage stored =
        EventImage.restore(EVENT, Image.restore("image/webp", WEBP), NOW.plusSeconds(60));

    assertThat(stored).isEqualTo(first).hasSameHashCodeAs(first);
    assertThat(EventImage.upload(UUID.randomUUID(), WEBP, NOW)).isNotEqualTo(first);
  }
}
