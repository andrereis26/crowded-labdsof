package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.SourceType;
import java.time.Instant;

/**
 * Who gave a piece of information and when (API Outline, Common types).
 *
 * @param stale whether on-site information is out of date at the time of the response; false for
 *     what is not on-site, such as an official change
 * @param staleAt when on-site information goes out of date, so a client that keeps the page open or
 *     offline can flag it without asking again; null for what is not on-site
 */
public record ProvenanceResponse(
    SourceType sourceType,
    String sourceName,
    Instant lastUpdatedAt,
    boolean confirmed,
    boolean stale,
    Instant staleAt) {}
