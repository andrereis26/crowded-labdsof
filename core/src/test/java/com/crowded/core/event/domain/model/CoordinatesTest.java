package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoordinatesTest {

  @ParameterizedTest
  @CsvSource({"90.1, 0", "-90.1, 0", "0, 180.1", "0, -180.1"})
  void refusesAPointOffTheMap(double latitude, double longitude) {
    assertThatThrownBy(() -> new Coordinates(latitude, longitude))
        .isInstanceOfSatisfying(
            RuleViolationException.class,
            e -> assertThat(e.getCode()).isEqualTo("COORDINATES_OUT_OF_RANGE"));
  }

  @Test
  void takesBothOrNeither() {
    assertThat(Coordinates.ofNullable(null, null)).isNull();
    assertThat(Coordinates.ofNullable(90.0, -180.0)).isEqualTo(new Coordinates(90, -180));
    assertThatThrownBy(() -> Coordinates.ofNullable(41.1, null))
        .isInstanceOfSatisfying(
            RuleViolationException.class,
            e -> assertThat(e.getCode()).isEqualTo("COORDINATES_INCOMPLETE"));
  }
}
