package com.crowded.core.event;

/** Where a piece of event information comes from, as the {@code source_type} enum type. */
public enum SourceType {
  OFFICIAL_SOURCE,
  ORGANIZATION,
  PARTICIPANT_ESTIMATE,
  AI_GENERATED,
  EXTERNAL_SERVICE
}
