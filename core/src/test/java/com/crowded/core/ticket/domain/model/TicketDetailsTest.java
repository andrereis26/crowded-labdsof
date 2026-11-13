package com.crowded.core.ticket.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketDetailsTest {

  @Test
  void stripsTheDetailsAndTakesBlankOnesAsMissing() {
    assertThat(new TicketDetails(" A ", "", "  "))
        .isEqualTo(new TicketDetails("A", null, null))
        .isNotEqualTo(TicketDetails.NONE);
    assertThat(new TicketDetails(null, " ", "")).isEqualTo(TicketDetails.NONE);
  }
}
