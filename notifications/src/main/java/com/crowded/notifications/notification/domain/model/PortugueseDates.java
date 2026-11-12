package com.crowded.notifications.notification.domain.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Dates and times as the participant app shows them, in Portugal's time zone: "SÁB 8 NOV" and
 * "21:30".
 */
final class PortugueseDates {

  /** Every event takes place in mainland Portugal. */
  static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

  private static final String[] WEEKDAYS = {"SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM"};
  private static final String[] MONTHS = {
    "JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ"
  };

  private PortugueseDates() {}

  /** "SÁB 8 NOV". */
  static String date(Instant instant) {
    ZonedDateTime local = instant.atZone(LISBON);
    return WEEKDAYS[local.getDayOfWeek().getValue() - 1]
        + " "
        + local.getDayOfMonth()
        + " "
        + MONTHS[local.getMonthValue() - 1];
  }

  /** "21:30". */
  static String time(Instant instant) {
    ZonedDateTime local = instant.atZone(LISBON);
    return String.format("%02d:%02d", local.getHour(), local.getMinute());
  }

  /** "SÁB 8 NOV · 21:30". */
  static String dateTime(Instant instant) {
    return date(instant) + " · " + time(instant);
  }

  static boolean sameDay(Instant one, Instant other) {
    return one.atZone(LISBON).toLocalDate().equals(other.atZone(LISBON).toLocalDate());
  }

  static boolean sameTime(Instant one, Instant other) {
    return time(one).equals(time(other));
  }
}
