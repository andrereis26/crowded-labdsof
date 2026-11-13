package com.crowded.core.event.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Which report categories ask other participants to confirm a report at the event, and how.
 *
 * @param categories the codes of those categories; a personal one is refused
 * @param venueRadiusMeters how far from the venue a participant may report or confirm
 * @param highConfidenceAnswers how many answers make a report's confidence high
 * @param maxPromptsPerHour how many confirmations one participant is asked for at most, per hour
 */
public record ConfirmationsRequest(
    @NotNull Set<String> categories,
    @NotNull Integer venueRadiusMeters,
    @NotNull Integer highConfidenceAnswers,
    @NotNull Integer maxPromptsPerHour) {

  public ConfirmationsRequest {
    categories = categories == null ? null : Set.copyOf(categories);
  }
}
