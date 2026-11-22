package com.crowded.notifications.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OperationalAlertTest {

  private static final UUID EVENT = UUID.randomUUID();

  @ParameterizedTest
  @CsvSource({
    "ENTRANCE_CLOSURE, Entrada fechada",
    "CAPACITY_REACHED, Lotação esgotada",
    "SCHEDULE_DELAY, Atraso no horário",
    "WEATHER_WARNING, Aviso meteorológico",
    "OTHER, Alerta da organização"
  })
  void itsKindIsTheTitleAndTheOrganizationsMessageTheBody(AlertType type, String title) {
    OperationalAlert alert =
        OperationalAlert.restore(UUID.randomUUID(), EVENT, type, " Usem a Porta B. ");

    assertThat(alert.describe()).isEqualTo(new NotificationText(title, "Usem a Porta B."));
  }

  @Test
  void isAboutTheEventItWasSentTo() {
    UUID id = UUID.randomUUID();
    OperationalAlert alert = OperationalAlert.restore(id, EVENT, AlertType.OTHER, "Olá.");

    assertThat(alert.isOf(EVENT)).isTrue();
    assertThat(alert.isOf(UUID.randomUUID())).isFalse();
    assertThat(alert)
        .isEqualTo(OperationalAlert.restore(id, UUID.randomUUID(), AlertType.OTHER, "Outra."))
        .hasSameHashCodeAs(OperationalAlert.restore(id, EVENT, AlertType.OTHER, "Olá."))
        .hasToString("OperationalAlert[" + id + ", OTHER]");
  }
}
