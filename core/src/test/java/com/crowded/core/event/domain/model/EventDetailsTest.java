package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The value objects an event is made of: each refuses an invalid value. */
class EventDetailsTest {

  private static final List<ReportCategory> CATALOG =
      List.of(
          new ReportCategory("CROWD_LEVEL", false, true),
          new ReportCategory("ON_SITE_PROBLEM", false, false),
          new ReportCategory("LOST_AND_FOUND", true, false));

  @Test
  void aProfileHasANameAndADescriptionWithinTheirLimits() {
    assertThat(new EventProfile("  Fado  ", " Noite de fado. "))
        .isEqualTo(new EventProfile("Fado", "Noite de fado."));
    assertThatThrownBy(() -> new EventProfile(" ", "Texto"))
        .hasFieldOrPropertyWithValue("code", "NAME_REQUIRED");
    assertThatThrownBy(() -> new EventProfile("x".repeat(121), "Texto"))
        .hasFieldOrPropertyWithValue("code", "NAME_TOO_LONG");
    assertThatThrownBy(() -> new EventProfile("Fado", null))
        .hasFieldOrPropertyWithValue("code", "DESCRIPTION_REQUIRED");
    assertThatThrownBy(() -> new EventProfile("Fado", "x".repeat(2001)))
        .hasFieldOrPropertyWithValue("code", "DESCRIPTION_TOO_LONG");
  }

  @Test
  void aCapacityIsAtLeastOnePerson() {
    assertThat(new Capacity(1).people()).isEqualTo(1);
    assertThatThrownBy(() -> new Capacity(0))
        .isInstanceOf(RuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "CAPACITY_OUT_OF_RANGE");
    assertThatThrownBy(() -> new Capacity(1_000_001))
        .hasFieldOrPropertyWithValue("code", "CAPACITY_OUT_OF_RANGE");
  }

  @Test
  void aCodeIsKeptInUpperCaseWithoutSurroundingSpaces() {
    assertThat(new EventCode(" marg-2026 ").value()).isEqualTo("MARG-2026");
    assertThatThrownBy(() -> new EventCode("  "))
        .hasFieldOrPropertyWithValue("code", "CODE_REQUIRED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ABC", "SÃO-JOÃO", "MARG 2026", "-MARG", "MARG--2026", "MARG-"})
  void refusesACodeThatIsTooShortOrHasOtherCharacters(String code) {
    assertThatThrownBy(() -> new EventCode(code))
        .hasFieldOrPropertyWithValue("code", "CODE_INVALID");
  }

  @Test
  void refusesACodeLongerThanSixtyFourCharacters() {
    assertThatNoException().isThrownBy(() -> new EventCode("A".repeat(64)));
    assertThatThrownBy(() -> new EventCode("A".repeat(65)))
        .hasFieldOrPropertyWithValue("code", "CODE_INVALID");
  }

  @Test
  void aDistrictIsOneOfPortugalsDistrictsOrRegions() {
    assertThat(new District(" Bragança ").name()).isEqualTo("Bragança");
    assertThat(new District("Região Autónoma dos Açores").name()).startsWith("Região");
    assertThatThrownBy(() -> new District("Galiza"))
        .hasFieldOrPropertyWithValue("code", "DISTRICT_UNKNOWN");
    assertThatThrownBy(() -> new District(null))
        .hasFieldOrPropertyWithValue("code", "DISTRICT_UNKNOWN");
  }

  @Test
  void queuePointsHaveDistinctNamesWithinTheirLimits() {
    assertThat(QueuePoints.named(List.of(" Porta A ", "Bar")).names())
        .containsExactly("Porta A", "Bar");
    assertThatThrownBy(() -> QueuePoints.named(List.of("Porta A", "porta a")))
        .hasFieldOrPropertyWithValue("code", "QUEUE_POINT_REPEATED");
    assertThatThrownBy(() -> new QueuePoint(""))
        .hasFieldOrPropertyWithValue("code", "QUEUE_POINT_NAME_REQUIRED");
    assertThatThrownBy(() -> new QueuePoint("x".repeat(61)))
        .hasFieldOrPropertyWithValue("code", "QUEUE_POINT_NAME_TOO_LONG");
    assertThatThrownBy(() -> QueuePoints.named(Collections.nCopies(31, "x")))
        .hasFieldOrPropertyWithValue("code", "TOO_MANY_QUEUE_POINTS");
  }

  @Test
  void confirmationRulesStayWithinTheirRanges() {
    assertThatNoException().isThrownBy(() -> new ConfirmationRules(100, 2, 1));
    assertThatNoException().isThrownBy(() -> new ConfirmationRules(50_000, 50, 10));
    assertThatThrownBy(() -> new ConfirmationRules(50_001, 8, 3))
        .hasFieldOrPropertyWithValue("code", "VENUE_RADIUS_OUT_OF_RANGE");
    assertThatThrownBy(() -> new ConfirmationRules(99, 8, 3))
        .hasFieldOrPropertyWithValue("code", "VENUE_RADIUS_OUT_OF_RANGE");
    assertThatThrownBy(() -> new ConfirmationRules(500, 51, 3))
        .hasFieldOrPropertyWithValue("code", "HIGH_CONFIDENCE_ANSWERS_OUT_OF_RANGE");
    assertThatThrownBy(() -> new ConfirmationRules(500, 8, 0))
        .hasFieldOrPropertyWithValue("code", "MAX_PROMPTS_OUT_OF_RANGE");
  }

  @Test
  void theDefaultSettingsPromptForTheCategoriesEnabledByDefault() {
    ConfirmationSettings defaults = ConfirmationSettings.defaults(CATALOG);

    assertThat(defaults.promptCategories()).containsExactly("CROWD_LEVEL");
    assertThat(defaults.rules()).isEqualTo(new ConfirmationRules(500, 8, 3));
  }

  @Test
  void chosenSettingsUseOnlyKnownCategoriesThatAreNotPersonal() {
    ConfirmationSettings chosen =
        ConfirmationSettings.chosen(Set.of("ON_SITE_PROBLEM"), ConfirmationRules.DEFAULT, CATALOG);

    assertThat(chosen.promptsFor("ON_SITE_PROBLEM")).isTrue();
    assertThat(chosen.promptsFor("CROWD_LEVEL")).isFalse();
    assertThatThrownBy(
            () ->
                ConfirmationSettings.chosen(
                    Set.of("LOST_AND_FOUND"), ConfirmationRules.DEFAULT, CATALOG))
        .hasFieldOrPropertyWithValue("code", "PERSONAL_CATEGORY_NOT_CONFIRMED");
    assertThatThrownBy(
            () ->
                ConfirmationSettings.chosen(Set.of("WEATHER"), ConfirmationRules.DEFAULT, CATALOG))
        .hasFieldOrPropertyWithValue("code", "REPORT_CATEGORY_UNKNOWN");
  }

  @Test
  void aScheduleNeedsItsStart() {
    assertThatThrownBy(() -> new Schedule(null, null, null))
        .hasFieldOrPropertyWithValue("code", "START_REQUIRED");
  }
}
