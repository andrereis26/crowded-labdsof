package com.crowded.core.ticket.domain.repository;

import com.crowded.core.ticket.domain.model.ReadTicket;
import java.util.Optional;

/** Reads a ticket file locally, without AI. */
public interface TicketFileReader {

  /**
   * The ticket's QR code and text, or empty when the file is not a readable ticket with a QR code.
   * Never throws for a bad file, and never writes it to disk.
   */
  Optional<ReadTicket> read(byte[] file);
}
