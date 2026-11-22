package com.crowded.core.event.domain.model;

/**
 * The kinds of on-site information, the ones that describe what is happening at the venue now and
 * go out of date. An official change or an alert is an announcement, not on-site information.
 */
public enum InformationKind {
  CROWD,
  QUEUE,
  ESTIMATE,
  WEATHER
}
