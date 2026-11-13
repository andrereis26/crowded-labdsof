package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.List;

/**
 * An event's entrances and queue points, in the order its organization lists them, each named once.
 *
 * @param points at most {@value #MAX} points, without two of the same name
 */
public record QueuePoints(List<QueuePoint> points) {

  static final int MAX = 30;

  /** An event without entrances or queue points yet. */
  public static final QueuePoints NONE = new QueuePoints(List.of());

  /**
   * @throws RuleViolationException when there are too many points or two with the same name
   */
  public QueuePoints {
    points = List.copyOf(points);
    if (points.size() > MAX) {
      throw new RuleViolationException(
          "TOO_MANY_QUEUE_POINTS", "An event has at most " + MAX + " entrances and queue points.");
    }
    for (int i = 0; i < points.size(); i++) {
      for (int j = i + 1; j < points.size(); j++) {
        if (points.get(i).isNamedLike(points.get(j))) {
          throw new RuleViolationException(
              "QUEUE_POINT_REPEATED",
              "Each entrance or queue point has its own name: " + points.get(j).name() + ".");
        }
      }
    }
  }

  /** The points named {@code names}, in that order. */
  public static QueuePoints named(List<String> names) {
    return new QueuePoints(names.stream().map(QueuePoint::new).toList());
  }

  /** The points' names, in order. */
  public List<String> names() {
    return points.stream().map(QueuePoint::name).toList();
  }
}
