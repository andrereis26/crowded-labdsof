package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;

/**
 * A waiting time the organization reads at one of its event's queue points, before it is published.
 *
 * @param pointName stripped
 * @param waitMinutes from 0 to {@value QueueTime#MAX_MINUTES}
 */
public record QueueWait(String pointName, int waitMinutes) {

  /**
   * @throws RuleViolationException when the minutes are out of range
   */
  public QueueWait {
    pointName = pointName == null ? "" : pointName.strip();
    QueueTime.checkMinutes(waitMinutes);
  }
}
