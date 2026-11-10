package com.crowded.core.event.domain.model;

/** Where a piece of event information comes from. */
public enum SourceType {
  OFFICIAL_SOURCE,
  ORGANIZATION,
  PARTICIPANT_ESTIMATE,
  AI_GENERATED,
  EXTERNAL_SERVICE
}
