package com.crowded.core.ticket.application.dto;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Import the ticket in {@code file} for the signed-in participant {@code ownerId}. A missing file
 * is an empty one, which no reader takes for a ticket.
 */
public record ImportTicketCommand(UUID ownerId, byte[] file) {

  public ImportTicketCommand {
    file = file == null ? new byte[0] : file.clone();
  }

  @Override
  public byte[] file() {
    return file.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ImportTicketCommand command
        && Objects.equals(ownerId, command.ownerId)
        && Arrays.equals(file, command.file);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hashCode(ownerId) + Arrays.hashCode(file);
  }

  @Override
  public String toString() {
    return "ImportTicketCommand[ownerId=" + ownerId + ", file=" + file.length + " bytes]";
  }
}
