package com.crowded.notifications.notification.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An official change that Core recorded (FR-016). The new date and time stay in the change, which
 * the clients read and show in the participant's time zone; the notification only says what kind of
 * change it is.
 *
 * @param startChanged whether the change moved the start
 * @param doorsOpenChanged whether the change moved the doors opening time
 * @param previousVenueName the venue before a venue change; {@code null} otherwise
 * @param newVenueName the venue after a venue change; {@code null} otherwise
 * @param newAddress the address after a venue change; {@code null} otherwise
 * @param ticketStillValid whether tickets stay valid, for a postponement or cancellation
 * @param refundInfo where to ask for a refund, for a postponement or cancellation
 * @param message the organization's text to the participants
 */
public record OfficialChange(
    UUID id,
    UUID eventId,
    ChangeType type,
    boolean startChanged,
    boolean doorsOpenChanged,
    String previousVenueName,
    String newVenueName,
    String newAddress,
    Boolean ticketStillValid,
    String refundInfo,
    String message) {

  /**
   * What the notification of this change tells a participant: what kind of change it is, the new
   * venue, and, when they have a ticket, whether it is still valid and where to ask for a refund
   * (FR-075). The organization's message comes last, on its own line.
   */
  public NotificationText describeFor(Recipient recipient) {
    List<String> facts = new ArrayList<>();
    String title =
        switch (type) {
          case CANCELLATION -> {
            facts.add("A organização cancelou o evento.");
            yield "Cancelado";
          }
          case POSTPONEMENT -> {
            facts.add("A organização adiou o evento.");
            yield "Adiado";
          }
          case TIME_CHANGE -> timeChange(facts);
          case VENUE_CHANGE -> venueChange(facts);
          case DETAILS_UPDATE -> {
            facts.add("A organização atualizou o evento.");
            yield "Detalhes atualizados";
          }
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

  private String timeChange(List<String> facts) {
    if (startChanged && doorsOpenChanged) {
      facts.add("A organização mudou a hora de início e a de abertura das portas.");
      return "Nova hora de início";
    }
    if (startChanged) {
      facts.add("A organização mudou a hora de início.");
      return "Nova hora de início";
    }
    if (doorsOpenChanged) {
      facts.add("A organização mudou a hora de abertura das portas.");
      return "Nova hora de abertura";
    }
    facts.add("A organização mudou o horário.");
    return "Novo horário";
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
