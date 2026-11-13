package com.crowded.core.ticket.domain.model;

/**
 * Gate, sector and seat of a participant's own ticket, as printed on it. Each one may be missing;
 * blank values count as missing.
 */
public record TicketDetails(String gate, String sector, String seat) {

  /** Details of a ticket on which none was found. */
  public static final TicketDetails NONE = new TicketDetails(null, null, null);

  public TicketDetails {
    gate = blankToNull(gate);
    sector = blankToNull(sector);
    seat = blankToNull(seat);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
