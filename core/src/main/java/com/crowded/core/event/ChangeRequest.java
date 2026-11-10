package com.crowded.core.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Body of {@code POST /events/{eventId}/changes} (API Outline, journey 3).
 *
 * @param type what changes: {@code POSTPONEMENT}, {@code TIME_CHANGE}, {@code VENUE_CHANGE} or
 *     {@code CANCELLATION}
 * @param newValue the changed fields; none for a cancellation
 * @param ticketStillValid for a postponement or cancellation, whether tickets stay valid
 * @param refundInfo for a postponement or cancellation, where to ask for a refund
 * @param message the organization's message to the participants
 */
public record ChangeRequest(
    @NotNull ChangeType type,
    @Valid NewValue newValue,
    Boolean ticketStillValid,
    @Size(max = 2000) String refundInfo,
    @Size(max = 2000) String message) {

  /** The fields a change sets; the ones left out keep their value or move with the start. */
  public record NewValue(
      OffsetDateTime startDateTime,
      OffsetDateTime doorsOpenAt,
      OffsetDateTime endDateTime,
      @Size(max = 200) String venueName,
      @Size(max = 500) String address,
      @DecimalMin("-90") @DecimalMax("90") Double latitude,
      @DecimalMin("-180") @DecimalMax("180") Double longitude) {

    static final NewValue NONE = new NewValue(null, null, null, null, null, null, null);
  }
}
