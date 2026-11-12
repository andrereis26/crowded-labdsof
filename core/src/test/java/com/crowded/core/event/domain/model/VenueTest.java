package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import org.junit.jupiter.api.Test;

class VenueTest {

  @Test
  void stripsItsNameAndAddress() {
    Venue venue = new Venue(" Hard Club ", " Porto ", null);

    assertThat(venue.name()).isEqualTo("Hard Club");
    assertThat(venue.address()).isEqualTo("Porto");
  }

  @Test
  void needsANameAndAnAddress() {
    assertThatThrownBy(() -> new Venue(" ", "Porto", null))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo("VENUE_REQUIRED"));
    assertThatThrownBy(() -> new Venue("Hard Club", null, null))
        .isInstanceOf(RuleViolationException.class);
  }

  @Test
  void isTheSamePlaceWhenTheNewCoordinatesAreUnknownOrEqual() {
    Venue known = new Venue("Coliseu", "Porto", new Coordinates(41.1, -8.6));

    assertThat(new Venue("Coliseu", "Porto", null).isSamePlaceAs(known)).isTrue();
    assertThat(new Venue("Coliseu", "Porto", new Coordinates(41.1, -8.6)).isSamePlaceAs(known))
        .isTrue();
    assertThat(new Venue("Coliseu", "Porto", new Coordinates(41.2, -8.6)).isSamePlaceAs(known))
        .isFalse();
    assertThat(new Venue("Coliseu", "Lisboa", null).isSamePlaceAs(known)).isFalse();
  }
}
