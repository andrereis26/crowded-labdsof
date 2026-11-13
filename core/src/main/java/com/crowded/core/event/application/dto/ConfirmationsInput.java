package com.crowded.core.event.application.dto;

import java.util.Set;

/**
 * Which report categories ask for confirmations at an event, and the rules of those confirmations.
 *
 * @param categories the codes of the categories that ask for them
 */
public record ConfirmationsInput(
    Set<String> categories,
    int venueRadiusMeters,
    int highConfidenceAnswers,
    int maxPromptsPerHour) {

  public ConfirmationsInput {
    categories = categories == null ? null : Set.copyOf(categories);
  }
}
