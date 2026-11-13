package com.crowded.core.event.api.dto;

import java.util.List;

/** Which report categories ask for confirmations at an event, by code, and how. */
public record ConfirmationsResponse(
    List<String> categories,
    int venueRadiusMeters,
    int highConfidenceAnswers,
    int maxPromptsPerHour) {

  public ConfirmationsResponse {
    categories = List.copyOf(categories);
  }
}
