package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  /** The points named {@code names}, in that order, without waiting times. */
  public static QueuePoints named(List<String> names) {
    return new QueuePoints(names.stream().map(name -> new QueuePoint(name, null)).toList());
  }

  /** The points' names, in order. */
  public List<String> names() {
    return points.stream().map(QueuePoint::name).toList();
  }

  /** The points with a published waiting time, in order. */
  public List<QueuePoint> timed() {
    return points.stream().filter(point -> point.time() != null).toList();
  }

  /**
   * These points with the waiting times {@code waits}, published by {@code provenance}; a point
   * without a wait keeps its time.
   *
   * @throws RuleViolationException when there is no wait, or a wait names no point or the same
   *     point as another
   */
  QueuePoints withTimes(List<QueueWait> waits, Provenance provenance) {
    if (waits.isEmpty()) {
      throw new RuleViolationException(
          "QUEUE_TIMES_REQUIRED", "Give the waiting time of at least one queue point.");
    }
    Map<String, QueueTime> times = new HashMap<>();
    for (QueueWait wait : waits) {
      if (!names().contains(wait.pointName())) {
        throw new RuleViolationException(
            "QUEUE_POINT_UNKNOWN",
            "This event has no entrance or queue point named " + wait.pointName() + ".");
      }
      if (times.put(wait.pointName(), new QueueTime(wait.waitMinutes(), provenance)) != null) {
        throw new RuleViolationException(
            "QUEUE_POINT_REPEATED",
            "Give one waiting time for each queue point: " + wait.pointName() + ".");
      }
    }
    return timedWith(times);
  }

  /** These points, each with the time of the point of the same name in {@code previous}. */
  QueuePoints keepingTimesOf(QueuePoints previous) {
    Map<String, QueueTime> times = new HashMap<>();
    previous.timed().forEach(point -> times.put(point.name(), point.time()));
    return timedWith(times);
  }

  private QueuePoints timedWith(Map<String, QueueTime> times) {
    return new QueuePoints(
        points.stream()
            .map(
                point ->
                    times.containsKey(point.name())
                        ? new QueuePoint(point.name(), times.get(point.name()))
                        : point)
            .toList());
  }
}
