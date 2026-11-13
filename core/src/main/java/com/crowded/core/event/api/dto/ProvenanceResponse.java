package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.SourceType;
import java.time.Instant;

/** Who gave a piece of information and when (API Outline, Common types). */
public record ProvenanceResponse(
    SourceType sourceType, String sourceName, Instant lastUpdatedAt, boolean confirmed) {}
