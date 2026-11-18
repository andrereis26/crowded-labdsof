package com.crowded.core.ticket.application.service;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.TooManyRequestsException;
import com.crowded.core.shared.domain.repository.RateLimiter;
import com.crowded.core.ticket.application.dto.CompleteTicketImportCommand;
import com.crowded.core.ticket.application.dto.ExtractionResult;
import com.crowded.core.ticket.application.dto.ImportTicketCommand;
import com.crowded.core.ticket.application.dto.TicketImportResult;
import com.crowded.core.ticket.domain.model.ReadTicket;
import com.crowded.core.ticket.domain.model.TicketImport;
import com.crowded.core.ticket.domain.repository.EntryCodeCipher;
import com.crowded.core.ticket.domain.repository.TicketFileReader;
import com.crowded.core.ticket.domain.repository.TicketImportRepository;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Imports a ticket file: reads its QR code locally, asks the AI for the event code with the
 * ticket's text without personal data, and connects the participant when the code matches a
 * registered event. Otherwise it keeps the import pending, with the entry code encrypted, until the
 * event code is known, which the participant then types. Imports are rate-limited per participant
 * (ADR 0004).
 */
@Service
public class TicketImportService {

  private static final Logger log = LoggerFactory.getLogger(TicketImportService.class);

  private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

  private final TicketFileReader reader;
  private final TicketExtractionRunner extraction;
  private final EntryCodeCipher cipher;
  private final TicketConnectionService connecting;
  private final TicketImportRepository imports;
  private final RateLimiter limiter;
  private final UserRepository users;
  private final int importsPerHour;

  TicketImportService(
      TicketFileReader reader,
      TicketExtractionRunner extraction,
      EntryCodeCipher cipher,
      TicketConnectionService connecting,
      TicketImportRepository imports,
      RateLimiter limiter,
      UserRepository users,
      @Value("${crowded.rate-limits.ticket-imports-per-user:20}") int importsPerHour) {
    this.reader = reader;
    this.extraction = extraction;
    this.cipher = cipher;
    this.connecting = connecting;
    this.imports = imports;
    this.limiter = limiter;
    this.users = users;
    this.importsPerHour = importsPerHour;
  }

  /**
   * Imports the command's ticket file: connects the participant to the registered event its
   * extracted code names, or keeps the import pending until the event code is known. Only an active
   * participant may import; the role is read from the database, never from the token.
   *
   * @throws NotAllowedException when the user is not an active participant
   * @throws com.crowded.core.shared.domain.exception.ConflictException with {@code
   *     TICKET_ALREADY_IMPORTED} when another account imported the same ticket
   * @throws TooManyRequestsException when the participant imported too many tickets this hour
   * @throws com.crowded.core.shared.domain.exception.RuleViolationException with {@code
   *     QR_CODE_NOT_FOUND} when the file is not a readable ticket with a QR code; nothing is stored
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when entry codes cannot
   *     be encrypted, because no key is configured
   */
  public TicketImportResult importTicket(ImportTicketCommand command) {
    User user = participant(command.ownerId());
    if (!limiter.tryAcquire(
        "ticket-import:" + command.ownerId(), importsPerHour, RATE_LIMIT_WINDOW)) {
      log.warn("Refused a ticket import by user {}: rate limit", command.ownerId());
      throw new TooManyRequestsException(
          "RATE_LIMITED", "Too many attempts. Try again in a few minutes.");
    }
    ReadTicket ticket =
        reader
            .read(command.file())
            .orElseThrow(
                () -> {
                  log.info("Refused a ticket import by user {}: no QR code", command.ownerId());
                  return ReadTicket.noQrCode();
                });

    // Encrypted first, so that without a key nothing reaches the AI.
    String encryptedEntryCode = cipher.encrypt(ticket.entryCode());
    ExtractionResult extracted = extraction.extract(ticket.textWithoutPersonalData());
    TicketImport pending =
        TicketImport.start(command.ownerId(), encryptedEntryCode, extracted.extraction());
    Optional<TicketImportResult> connected = connecting.connectByExtractedCode(user, pending);
    if (connected.isPresent()) {
      return connected.get();
    }
    imports.save(pending);
    log.info(
        "Ticket import {} by user {} waits for the event code: extraction={}",
        pending.getId(),
        command.ownerId(),
        extracted.outcome());
    return TicketImportResult.needsEventCode(
        pending.getId(), pending.getExtraction().extractedFields());
  }

  /**
   * Completes the participant's pending import with the event code they typed: connects them to the
   * registered event of that code, which the server decides, never an event the client names. The
   * import is forgotten with its entry code once it ends, connected or refused.
   *
   * @throws NotAllowedException when the user is not an active participant
   * @throws NotFoundException with {@code IMPORT_NOT_FOUND} when the participant has no such
   *     pending import: it never existed, expired or is another participant's
   * @throws NotFoundException with {@code EVENT_NOT_ON_CROWDED} when no registered event has the
   *     code; the import and its entry code are deleted
   * @throws ConflictException with {@code TICKET_ALREADY_IMPORTED} when another account imported
   *     the same ticket; the import is deleted
   */
  public TicketImportResult completeWithEventCode(CompleteTicketImportCommand command) {
    User user = participant(command.ownerId());
    TicketImport pending =
        imports
            .findById(command.importId())
            .filter(found -> found.isOwnedBy(command.ownerId()))
            .orElseThrow(
                () -> {
                  log.info(
                      "Refused an event code by user {}: no pending import {}",
                      command.ownerId(),
                      command.importId());
                  return new NotFoundException(
                      "IMPORT_NOT_FOUND", "There is no pending ticket import.");
                });
    Optional<TicketImportResult> connected;
    try {
      connected = connecting.connectByTypedCode(user, pending, command.eventCode());
    } finally {
      imports.delete(pending.getId());
    }
    return connected.orElseThrow(
        () -> {
          log.info(
              "Ticket import {} by user {} refused: the typed code matches no event",
              pending.getId(),
              command.ownerId());
          return new NotFoundException(
              "EVENT_NOT_ON_CROWDED", "No registered event has this code.");
        });
  }

  private User participant(UUID userId) {
    User user = users.findById(userId).filter(User::isParticipant).orElse(null);
    if (user == null) {
      log.warn("Refused a ticket import by user {}: not a participant", userId);
      throw new NotAllowedException("PARTICIPANT_ONLY", "Only a participant can import a ticket.");
    }
    return user;
  }
}
