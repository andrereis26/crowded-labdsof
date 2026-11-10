package com.crowded.core.event.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** The changed fields of a change; the ones left out keep their value or move with the start. */
public record NewValueRequest(
    OffsetDateTime startDateTime,
    OffsetDateTime doorsOpenAt,
    OffsetDateTime endDateTime,
    @Size(max = 200) String venueName,
    @Size(max = 500) String address,
    @DecimalMin("-90") @DecimalMax("90") Double latitude,
    @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
