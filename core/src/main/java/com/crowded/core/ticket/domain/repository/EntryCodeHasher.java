package com.crowded.core.ticket.domain.repository;

import com.crowded.core.ticket.domain.model.EntryCodeHash;
import java.util.UUID;

/** Hashes a ticket's entry code with a secret key, so the same ticket gives the same hash. */
public interface EntryCodeHasher {

  /**
   * The hash of {@code entryCode} for {@code eventId}: the same ticket always gives the same one,
   * and the same code on another event gives another.
   *
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when no key is configured
   */
  EntryCodeHash hash(UUID eventId, String entryCode);
}
