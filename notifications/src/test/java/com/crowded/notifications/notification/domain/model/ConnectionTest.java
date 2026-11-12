package com.crowded.notifications.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConnectionTest {

  @ParameterizedTest(name = "event {0}, overall {1} -> {2}")
  @CsvSource(
      nullValues = "unset",
      value = {
        "unset, unset, true",
        "unset, true, true",
        "unset, false, false",
        "true, false, true",
        "false, true, false",
        "false, unset, false"
      })
  void thePerEventChoiceOverridesTheOverallOneAndEveryTypeIsOnByDefault(
      Boolean eventSetting, Boolean overallSetting, boolean wanted) {
    Connection connection = new Connection(UUID.randomUUID(), true, eventSetting, overallSetting);

    assertThat(connection.wantsNotification()).isEqualTo(wanted);
  }
}
