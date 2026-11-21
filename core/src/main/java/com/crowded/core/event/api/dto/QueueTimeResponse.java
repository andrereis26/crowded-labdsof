package com.crowded.core.event.api.dto;

/** The waiting time participants read at a queue point now, with who published it and when. */
public record QueueTimeResponse(String pointName, int waitMinutes, ProvenanceResponse provenance) {}
