package com.crowded.core.ticket.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TicketExtractionTest {

  @Test
  void takesTheFieldsOfTheSchema() {
    assertThat(
            TicketExtraction.fromProviderFields(
                Map.of("eventCode", " MARG-2026 ", "gate", "A", "sector", "2", "seat", "14")))
        .contains(new TicketExtraction("MARG-2026", new TicketDetails("A", "2", "14")));
  }

  @Test
  void takesMissingNullAndBlankFieldsAsNotFound() {
    Map<String, Object> fields = new HashMap<>();
    fields.put("eventCode", "");
    fields.put("gate", "B");
    fields.put("seat", null);

    assertThat(TicketExtraction.fromProviderFields(fields))
        .hasValueSatisfying(
            extraction -> {
              assertThat(extraction.hasEventCode()).isFalse();
              assertThat(extraction.extractedFields())
                  .isEqualTo(new TicketDetails("B", null, null));
            });
  }

  @Test
  void discardsAnAnswerWithOtherFields() {
    assertThat(TicketExtraction.fromProviderFields(Map.of("eventCode", "X", "action", "delete")))
        .isEmpty();
  }

  @Test
  void discardsAnAnswerWhoseFieldsAreNotText() {
    assertThat(TicketExtraction.fromProviderFields(Map.of("eventCode", Map.of("value", "X"))))
        .isEmpty();
    assertThat(TicketExtraction.fromProviderFields(Map.of("eventCode", List.of("X")))).isEmpty();
    assertThat(TicketExtraction.fromProviderFields(Map.of("seat", 14))).isEmpty();
    assertThat(TicketExtraction.fromProviderFields(null)).isEmpty();
  }

  @Test
  void hasAnEventCodeOnlyWhenOneWasFound() {
    assertThat(new TicketExtraction("MARG-2026", null).hasEventCode()).isTrue();
    assertThat(new TicketExtraction(" ", null).hasEventCode()).isFalse();
    assertThat(new TicketExtraction(null, null).extractedFields()).isEqualTo(TicketDetails.NONE);
    assertThat(TicketExtraction.NOTHING.hasEventCode()).isFalse();
  }
}
