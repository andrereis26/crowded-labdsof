package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;

/**
 * How an event asks participants to confirm reports.
 *
 * @param venueRadiusMeters how far from the venue a participant may report or confirm
 * @param highConfidenceAnswers how many answers make a report's confidence high
 * @param maxPromptsPerHour how many confirmations one participant is asked for at most, per hour
 */
public record ConfirmationRules(
    int venueRadiusMeters, int highConfidenceAnswers, int maxPromptsPerHour) {

  /** The rules of an event whose organization has not chosen others. */
  public static final ConfirmationRules DEFAULT = new ConfirmationRules(500, 8, 3);

  /**
   * @throws RuleViolationException when a value is outside its range
   */
  public ConfirmationRules {
    check(venueRadiusMeters, 100, 5000, "VENUE_RADIUS_OUT_OF_RANGE", "The venue radius", "m");
    check(
        highConfidenceAnswers,
        2,
        50,
        "HIGH_CONFIDENCE_ANSWERS_OUT_OF_RANGE",
        "The answers for high confidence",
        "");
    check(
        maxPromptsPerHour,
        1,
        10,
        "MAX_PROMPTS_OUT_OF_RANGE",
        "The confirmations asked per participant and hour",
        "");
  }

  private static void check(int value, int min, int max, String code, String what, String unit) {
    if (value < min || value > max) {
      throw new RuleViolationException(
          code, what + " must be between " + min + unit + " and " + max + unit + ".");
    }
  }
}
