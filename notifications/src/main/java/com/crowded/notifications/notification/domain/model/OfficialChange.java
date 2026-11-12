package com.crowded.notifications.notification.domain.model;

import static com.crowded.notifications.notification.domain.model.PortugueseDates.date;
import static com.crowded.notifications.notification.domain.model.PortugueseDates.dateTime;
import static com.crowded.notifications.notification.domain.model.PortugueseDates.sameDay;
import static com.crowded.notifications.notification.domain.model.PortugueseDates.sameTime;
import static com.crowded.notifications.notification.domain.model.PortugueseDates.time;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An official change that Core recorded (FR-016), with the values it changed. A value the change
 * did not touch, or that Core did not record, is {@code null}.
 *
 * @param ticketStillValid whether tickets stay valid, for a postponement or cancellation
 * @param refundInfo where to ask for a refund, for a postponement or cancellation
 * @param message the organization's text to the participants
 */
public record OfficialChange(
    UUID id,
    UUID eventId,
    ChangeType type,
    Instant previousStart,
    Instant newStart,
    Instant previousDoorsOpenAt,
    Instant newDoorsOpenAt,
    String previousVenueName,
    String newVenueName,
    String newAddress,
    Boolean ticketStillValid,
    String refundInfo,
    String message) {

  /**
   * What the notification of this change tells a participant: what changes for them, with the new
   * date, time or venue, and, when they have a ticket, whether it is still valid and where to ask
   * for a refund (FR-075). The organization's message comes last, on its own line.
   */
  public NotificationText describeFor(Recipient recipient) {
    List<String> facts = new ArrayList<>();
    String title =
        switch (type) {
          case CANCELLATION -> cancellation(facts);
          case POSTPONEMENT -> postponement(facts);
          case TIME_CHANGE -> timeChange(facts);
          case VENUE_CHANGE -> venueChange(facts);
          case DETAILS_UPDATE -> "Detalhes atualizados";
        };
    if (recipient.ticket() && ticketStillValid != null) {
      facts.add(
          ticketStillValid
              ? "O teu bilhete continua válido."
              : "O teu bilhete deixa de ser válido.");
    }
    if (recipient.ticket() && hasText(refundInfo)) {
      facts.add("Reembolso: " + refundInfo.strip());
    }
    String body = String.join(" ", facts);
    if (hasText(message)) {
      body = body.isEmpty() ? message.strip() : body + "\n" + message.strip();
    }
    return new NotificationText(title, body);
  }

  private String cancellation(List<String> facts) {
    facts.add("A organização cancelou o evento.");
    return "Cancelado";
  }

  private String postponement(List<String> facts) {
    if (previousStart != null && newStart != null) {
      facts.add(
          "Era "
              + (sameTime(previousStart, newStart) ? date(previousStart) : dateTime(previousStart))
              + ".");
    }
    return newStart == null ? "Adiado" : "Adiado para " + dateTime(newStart);
  }

  private String timeChange(List<String> facts) {
    String title;
    if (newStart != null) {
      if (previousStart != null && sameDay(previousStart, newStart)) {
        title = "Nova hora: " + time(newStart);
        facts.add("Era às " + time(previousStart) + ".");
      } else {
        title = "Nova data: " + dateTime(newStart);
        if (previousStart != null) {
          facts.add("Era " + dateTime(previousStart) + ".");
        }
      }
      if (newDoorsOpenAt != null) {
        facts.add("Portas abrem às " + time(newDoorsOpenAt) + ".");
      }
    } else if (newDoorsOpenAt != null) {
      title = "Portas abrem às " + time(newDoorsOpenAt);
      if (previousDoorsOpenAt != null) {
        facts.add("Era às " + time(previousDoorsOpenAt) + ".");
      }
    } else {
      title = "Novo horário";
    }
    return title;
  }

  private String venueChange(List<String> facts) {
    if (hasText(newAddress)) {
      facts.add(newAddress.strip() + ".");
    }
    if (hasText(previousVenueName)) {
      facts.add("Era em " + previousVenueName.strip() + ".");
    }
    return hasText(newVenueName) ? "Novo local: " + newVenueName.strip() : "Novo local";
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
