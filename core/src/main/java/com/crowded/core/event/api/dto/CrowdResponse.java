package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.CrowdLevel;

/** The crowd level participants read now, with who published it and when. */
public record CrowdResponse(CrowdLevel level, ProvenanceResponse provenance) {}
