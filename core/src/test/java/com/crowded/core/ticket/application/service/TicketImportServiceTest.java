package com.crowded.core.ticket.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Admission;
import com.crowded.core.event.domain.model.Capacity;
import com.crowded.core.event.domain.model.ConfirmationRules;
import com.crowded.core.event.domain.model.ConfirmationSettings;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventProfile;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.QueuePoints;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.domain.exception.TooManyRequestsException;
import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.shared.domain.repository.RateLimiter;
import com.crowded.core.ticket.application.dto.CompleteTicketImportCommand;
import com.crowded.core.ticket.application.dto.ImportTicketCommand;
import com.crowded.core.ticket.application.dto.ImportedEvent;
import com.crowded.core.ticket.application.dto.TicketImportResult;
import com.crowded.core.ticket.application.dto.TicketImportStatus;
import com.crowded.core.ticket.domain.model.EntryCodeHash;
import com.crowded.core.ticket.domain.model.ImportMethod;
import com.crowded.core.ticket.domain.model.MatchMethod;
import com.crowded.core.ticket.domain.model.ReadTicket;
import com.crowded.core.ticket.domain.model.Ticket;
import com.crowded.core.ticket.domain.model.TicketDetails;
import com.crowded.core.ticket.domain.model.TicketImport;
import com.crowded.core.ticket.domain.repository.EntryCodeCipher;
import com.crowded.core.ticket.domain.repository.TicketExtractor;
import com.crowded.core.ticket.domain.repository.TicketFileReader;
import com.crowded.core.ticket.domain.repository.TicketImportRepository;
import com.crowded.core.ticket.domain.repository.TicketRepository;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TicketImportServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
  private static final Instant START = NOW.plus(12, ChronoUnit.DAYS);
  private static final Event FESTIVAL =
      Event.restore(
          UUID.randomUUID(),
          UUID.randomUUID(),
          new EventProfile("Festival Margens do Douro", "Dois dias de música."),
          new Admission(EventType.TICKETED, new EventCode("MARG-2026")),
          new EventState(
              EventStatus.SCHEDULED,
              new Schedule(
                  START, START.minus(2, ChronoUnit.HOURS), START.plus(5, ChronoUnit.HOURS)),
              new Venue("Cais da Ribeira", "Porto", new Coordinates(41.14, -8.61))),
          new District("Porto"),
          new Capacity(8000),
          QueuePoints.NONE,
          new ConfirmationSettings(Set.of(), ConfirmationRules.DEFAULT),
          null);
  private static final UUID OWNER = UUID.fromString("10000000-0000-4000-8000-000000000011");
  private static final String ENTRY_CODE = "QR-ENTRY-0001";
  private static final String TICKET_TEXT =
      """
      Festival Margens do Douro
      Código do evento: MARG-2026
      Nome: Joana Silva
      Email: joana@exemplo.pt
      NIF: 123456789
      Porta A Lugar 14
      """
          + ENTRY_CODE;
  private static final byte[] TICKET_FILE = "a ticket".getBytes(StandardCharsets.UTF_8);

  /** Reads {@link #TICKET_FILE} as the ticket above, and anything else as no ticket. */
  private static final TicketFileReader READER =
      file ->
          new String(file, StandardCharsets.UTF_8).equals("a ticket")
              ? Optional.of(new ReadTicket(ENTRY_CODE, TICKET_TEXT))
              : Optional.empty();

  /** Reversible stand-in for the AES cipher. */
  private static final EntryCodeCipher CIPHER =
      new EntryCodeCipher() {
        @Override
        public String encrypt(String entryCode) {
          return "enc(" + new StringBuilder(entryCode).reverse() + ")";
        }

        @Override
        public String decrypt(String encrypted) {
          return new StringBuilder(encrypted.substring(4, encrypted.length() - 1))
              .reverse()
              .toString();
        }
      };

  private static final RateLimiter UNLIMITED = (key, limit, window) -> true;

  /** Keeps imports in memory, as the Redis adapter does. */
  private static final class InMemoryImports implements TicketImportRepository {
    final Map<UUID, TicketImport> saved = new HashMap<>();
    boolean down;

    @Override
    public void save(TicketImport ticketImport) {
      saved.put(ticketImport.getId(), ticketImport);
    }

    @Override
    public Optional<TicketImport> findById(UUID importId) {
      return Optional.ofNullable(saved.get(importId));
    }

    @Override
    public void delete(UUID importId) {
      if (down) {
        throw new UnavailableException("TICKET_IMPORT_UNAVAILABLE", "Down");
      }
      saved.remove(importId);
    }
  }

  private static final UUID MEMBER = UUID.fromString("10000000-0000-4000-8000-000000000021");

  private final UserRepository users = mock(UserRepository.class);

  {
    when(users.findById(OWNER))
        .thenReturn(Optional.of(User.restore(OWNER, null, Role.PARTICIPANT, true)));
    when(users.findById(MEMBER))
        .thenReturn(
            Optional.of(User.restore(MEMBER, UUID.randomUUID(), Role.ORGANIZATION_MEMBER, true)));
  }

  private final AtomicReference<String> sentToAi = new AtomicReference<>();
  private final InMemoryImports imports = new InMemoryImports();
  private final TicketExtractionRunner extraction =
      new TicketExtractionRunner(
          text -> {
            sentToAi.set(text);
            return "{\"eventCode\": \"MARG-2026\", \"gate\": \"A\", \"seat\": \"14\"}";
          },
          Duration.ofSeconds(5),
          new SimpleMeterRegistry());

  private final EventRepository events = mock(EventRepository.class);
  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final TicketRepository tickets = mock(TicketRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  private TicketImportService service(
      TicketExtractionRunner extraction, EntryCodeCipher cipher, RateLimiter limiter) {
    TicketConnectionService connecting =
        new TicketConnectionService(
            events,
            connections,
            tickets,
            cipher,
            (eventId, code) -> new EntryCodeHash(eventId + "|" + code),
            Clock.fixed(NOW, ZoneOffset.UTC),
            meters);
    return new TicketImportService(
        READER, extraction, cipher, connecting, imports, limiter, users, 20);
  }

  private void festivalIsRegistered() {
    when(events.findUpcomingTicketedByEventCode(new EventCode("MARG-2026"), NOW))
        .thenReturn(Optional.of(FESTIVAL));
  }

  private double connections(MatchMethod method, String outcome) {
    return meters
        .get("crowded.tickets.connection")
        .tag("method", method.name())
        .tag("outcome", outcome)
        .counter()
        .count();
  }

  private TicketImportService service() {
    return service(extraction, CIPHER, UNLIMITED);
  }

  private static ImportTicketCommand command(byte[] file) {
    return new ImportTicketCommand(OWNER, file);
  }

  @AfterEach
  void closeExtraction() {
    extraction.close();
  }

  @Test
  void sendsTheAiNoPersonalDataAndNoEntryCode() {
    service().importTicket(command(TICKET_FILE));

    assertThat(sentToAi.get())
        .contains("MARG-2026", "Lugar 14")
        .doesNotContain("Joana Silva", "joana@exemplo.pt", "123456789", ENTRY_CODE);
  }

  @Test
  void keepsTheImportPendingWithTheEntryCodeEncrypted() {
    TicketImportResult result = service().importTicket(command(TICKET_FILE));

    TicketImport pending = imports.saved.get(result.importId());
    assertThat(pending.getOwnerId()).isEqualTo(OWNER);
    assertThat(pending.getEncryptedEntryCode()).doesNotContain(ENTRY_CODE);
    assertThat(CIPHER.decrypt(pending.getEncryptedEntryCode())).isEqualTo(ENTRY_CODE);
    assertThat(pending.getExtraction().extractedEventCode()).isEqualTo("MARG-2026");
  }

  @Test
  void asksForTheEventCodeWithTheTicketsDetails() {
    TicketImportResult result = service().importTicket(command(TICKET_FILE));

    assertThat(result.status()).isEqualTo(TicketImportStatus.NEEDS_EVENT_CODE);
    assertThat(result.ticket()).isEqualTo(new TicketDetails("A", null, "14"));
  }

  @Test
  void connectsAtOnceWhenTheExtractedCodeMatchesARegisteredEvent() {
    festivalIsRegistered();

    TicketImportResult result = service().importTicket(command(TICKET_FILE));

    assertThat(result.status()).isEqualTo(TicketImportStatus.CONNECTED);
    assertThat(result.importId()).isNull();
    assertThat(result.ticketId()).isEqualTo(result.connectionId());
    assertThat(result.ticket()).isEqualTo(new TicketDetails("A", null, "14"));
    assertThat(imports.saved).isEmpty();
    verify(connections).addTicketConnection(any());
    verify(tickets).add(any(), any(), eq(NOW));
    assertThat(connections(MatchMethod.EXTRACTED_CODE, "CONNECTED")).isEqualTo(1);
  }

  @Test
  void showsTheRegisteredEventNotTheExtraction() {
    festivalIsRegistered();

    ImportedEvent event = service().importTicket(command(TICKET_FILE)).event();

    assertThat(event)
        .isEqualTo(
            new ImportedEvent(
                FESTIVAL.getId(),
                "Festival Margens do Douro",
                START,
                START.minus(2, ChronoUnit.HOURS),
                "Cais da Ribeira"));
  }

  @Test
  void keepsTheTicketPendingWhenNoRegisteredEventHasTheCode() {
    TicketImportResult result = service().importTicket(command(TICKET_FILE));

    assertThat(result.status()).isEqualTo(TicketImportStatus.NEEDS_EVENT_CODE);
    assertThat(result.event()).isNull();
    assertThat(result.ticketId()).isNull();
    verifyNoInteractions(tickets);
    verify(connections, never()).addTicketConnection(any());
    assertThat(connections(MatchMethod.EXTRACTED_CODE, "NOT_MATCHED")).isEqualTo(1);
  }

  @Test
  void refusesATicketThatAnotherAccountImportedAndKeepsNothing() {
    festivalIsRegistered();
    Ticket theirs =
        Ticket.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TicketDetails.NONE,
            CIPHER.encrypt(ENTRY_CODE),
            new EntryCodeHash(FESTIVAL.getId() + "|" + ENTRY_CODE),
            ImportMethod.PDF_UPLOAD,
            MatchMethod.EXTRACTED_CODE);
    when(tickets.findByEntryCodeHash(theirs.getEntryCodeHash())).thenReturn(Optional.of(theirs));

    assertThatExceptionOfType(ConflictException.class)
        .isThrownBy(() -> service().importTicket(command(TICKET_FILE)))
        .satisfies(e -> assertThat(e.getCode()).isEqualTo("TICKET_ALREADY_IMPORTED"));

    assertThat(imports.saved).isEmpty();
    assertThat(connections(MatchMethod.EXTRACTED_CODE, "ALREADY_IMPORTED")).isEqualTo(1);
  }

  @Test
  void answersConnectedAgainWhenTheOwnerImportsTheSameTicket() {
    festivalIsRegistered();
    Ticket own =
        Ticket.restore(
            UUID.randomUUID(),
            OWNER,
            new TicketDetails("A", null, "14"),
            CIPHER.encrypt(ENTRY_CODE),
            new EntryCodeHash(FESTIVAL.getId() + "|" + ENTRY_CODE),
            ImportMethod.PDF_UPLOAD,
            MatchMethod.EXTRACTED_CODE);
    when(tickets.findByEntryCodeHash(own.getEntryCodeHash())).thenReturn(Optional.of(own));
    when(connections.findById(own.getId()))
        .thenReturn(
            Optional.of(EventConnection.restore(own.getId(), OWNER, FESTIVAL.getId(), NOW)));

    TicketImportResult result = service().importTicket(command(TICKET_FILE));

    assertThat(result.status()).isEqualTo(TicketImportStatus.CONNECTED);
    assertThat(result.ticketId()).isEqualTo(own.getId());
    verify(connections, never()).addTicketConnection(any());
    assertThat(connections(MatchMethod.EXTRACTED_CODE, "ALREADY_CONNECTED")).isEqualTo(1);
  }

  @Test
  void showsNoTicketWhenTheParticipantWasConnectedToTheEventWithoutOne() {
    festivalIsRegistered();
    when(connections.findByUserAndEvent(OWNER, FESTIVAL.getId()))
        .thenReturn(
            Optional.of(EventConnection.restore(UUID.randomUUID(), OWNER, FESTIVAL.getId(), NOW)));

    TicketImportResult result = service().importTicket(command(TICKET_FILE));

    assertThat(result.status()).isEqualTo(TicketImportStatus.CONNECTED);
    assertThat(result.ticketId()).isNull();
    assertThat(result.ticket()).isEqualTo(TicketDetails.NONE);
  }

  @Test
  void stillKeepsTheImportWhenTheAiIsUnavailable() {
    TicketExtractor unavailable =
        text -> {
          throw new IllegalStateException("down");
        };
    try (TicketExtractionRunner runner =
        new TicketExtractionRunner(unavailable, Duration.ofSeconds(5), new SimpleMeterRegistry())) {
      TicketImportResult result =
          service(runner, CIPHER, UNLIMITED).importTicket(command(TICKET_FILE));

      assertThat(result.status()).isEqualTo(TicketImportStatus.NEEDS_EVENT_CODE);
      assertThat(result.ticket()).isEqualTo(TicketDetails.NONE);
      assertThat(imports.saved).containsKey(result.importId());
    }
  }

  @Test
  void refusesAFileWithoutAQrCodeAndStoresNothing() {
    assertThatExceptionOfType(RuleViolationException.class)
        .isThrownBy(
            () -> service().importTicket(command("no ticket".getBytes(StandardCharsets.UTF_8))))
        .satisfies(e -> assertThat(e.getCode()).isEqualTo("QR_CODE_NOT_FOUND"));

    assertThat(sentToAi.get()).isNull();
    assertThat(imports.saved).isEmpty();
  }

  @Test
  void limitsTheImportsOfEachParticipantPerHour() {
    AtomicReference<String> limitedKey = new AtomicReference<>();
    RateLimiter exhausted =
        (key, limit, window) -> {
          limitedKey.set(key + " " + limit + " " + window);
          return false;
        };

    assertThatExceptionOfType(TooManyRequestsException.class)
        .isThrownBy(() -> service(extraction, CIPHER, exhausted).importTicket(command(TICKET_FILE)))
        .satisfies(e -> assertThat(e.getCode()).isEqualTo("RATE_LIMITED"));

    assertThat(limitedKey.get()).isEqualTo("ticket-import:" + OWNER + " 20 PT1H");
    assertThat(imports.saved).isEmpty();
  }

  @Test
  void sendsNothingToTheAiWithoutAnEncryptionKey() {
    EntryCodeCipher noKey =
        new EntryCodeCipher() {
          @Override
          public String encrypt(String entryCode) {
            throw new UnavailableException("TICKET_IMPORT_UNAVAILABLE", "No key");
          }

          @Override
          public String decrypt(String encrypted) {
            throw new UnavailableException("TICKET_IMPORT_UNAVAILABLE", "No key");
          }
        };

    assertThatExceptionOfType(UnavailableException.class)
        .isThrownBy(() -> service(extraction, noKey, UNLIMITED).importTicket(command(TICKET_FILE)));

    assertThat(sentToAi.get()).isNull();
    assertThat(imports.saved).isEmpty();
  }

  @Test
  void letsOnlyAnActiveParticipantImport() {
    assertThatExceptionOfType(NotAllowedException.class)
        .isThrownBy(() -> service().importTicket(new ImportTicketCommand(MEMBER, TICKET_FILE)))
        .satisfies(e -> assertThat(e.getCode()).isEqualTo("PARTICIPANT_ONLY"));
    assertThatExceptionOfType(NotAllowedException.class)
        .isThrownBy(
            () -> service().importTicket(new ImportTicketCommand(UUID.randomUUID(), TICKET_FILE)));

    assertThat(sentToAi.get()).isNull();
    assertThat(imports.saved).isEmpty();
  }

  private TicketImport pendingImport() {
    TicketImportResult result = service().importTicket(command(TICKET_FILE));
    return imports.saved.get(result.importId());
  }

  private void typedCodeIsRegistered() {
    when(events.findUpcomingTicketedByEventCode(new EventCode("TYPD-2026"), NOW))
        .thenReturn(Optional.of(FESTIVAL));
  }

  @Test
  void connectsToTheEventOfTheTypedCodeAndForgetsTheImport() {
    typedCodeIsRegistered();
    TicketImport pending = pendingImport();

    TicketImportResult result =
        service()
            .completeWithEventCode(
                new CompleteTicketImportCommand(OWNER, pending.getId(), " typd-2026 "));

    assertThat(result.status()).isEqualTo(TicketImportStatus.CONNECTED);
    assertThat(result.importId()).isNull();
    assertThat(result.event().name()).isEqualTo("Festival Margens do Douro");
    assertThat(result.ticket()).isEqualTo(new TicketDetails("A", null, "14"));
    assertThat(imports.saved).isEmpty();
    verify(connections).addTicketConnection(any());
    verify(tickets).add(any(), any(), eq(NOW));
    assertThat(connections(MatchMethod.TYPED_CODE, "CONNECTED")).isEqualTo(1);
  }

  @Test
  void keepsTheImportToTryAgainWhenConnectingFails() {
    typedCodeIsRegistered();
    TicketImport pending = pendingImport();
    when(tickets.findByEntryCodeHash(any()))
        .thenThrow(new UnavailableException("DATABASE_UNAVAILABLE", "Down"));

    assertThatExceptionOfType(UnavailableException.class)
        .isThrownBy(
            () ->
                service()
                    .completeWithEventCode(
                        new CompleteTicketImportCommand(OWNER, pending.getId(), "TYPD-2026")));

    assertThat(imports.saved).containsKey(pending.getId());
  }

  @Test
  void answersTheConnectionEvenWhenTheImportCannotBeDeletedNow() {
    typedCodeIsRegistered();
    TicketImport pending = pendingImport();
    imports.down = true;

    TicketImportResult result =
        service()
            .completeWithEventCode(
                new CompleteTicketImportCommand(OWNER, pending.getId(), "TYPD-2026"));

    assertThat(result.status()).isEqualTo(TicketImportStatus.CONNECTED);
    verify(tickets).add(any(), any(), eq(NOW));
  }

  @Test
  void deletesTheImportWhenTheTypedCodeMatchesNoEvent() {
    TicketImport pending = pendingImport();

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service()
                    .completeWithEventCode(
                        new CompleteTicketImportCommand(OWNER, pending.getId(), "NONE-2026")))
        .satisfies(e -> assertThat(e.getCode()).isEqualTo("EVENT_NOT_ON_CROWDED"));

    assertThat(imports.saved).isEmpty();
    verifyNoInteractions(tickets);
    verify(connections, never()).addTicketConnection(any());
  }

  @Test
  void deletesTheImportWhenAnotherAccountImportedTheTicket() {
    typedCodeIsRegistered();
    TicketImport pending = pendingImport();
    Ticket theirs =
        Ticket.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TicketDetails.NONE,
            CIPHER.encrypt(ENTRY_CODE),
            new EntryCodeHash(FESTIVAL.getId() + "|" + ENTRY_CODE),
            ImportMethod.PDF_UPLOAD,
            MatchMethod.EXTRACTED_CODE);
    when(tickets.findByEntryCodeHash(theirs.getEntryCodeHash())).thenReturn(Optional.of(theirs));

    assertThatExceptionOfType(ConflictException.class)
        .isThrownBy(
            () ->
                service()
                    .completeWithEventCode(
                        new CompleteTicketImportCommand(OWNER, pending.getId(), "TYPD-2026")))
        .satisfies(e -> assertThat(e.getCode()).isEqualTo("TICKET_ALREADY_IMPORTED"));

    assertThat(imports.saved).isEmpty();
  }

  @Test
  void knowsNoImportThatDoesNotExistOrBelongsToAnotherParticipant() {
    typedCodeIsRegistered();
    TicketImport pending = pendingImport();
    UUID other = UUID.fromString("10000000-0000-4000-8000-000000000012");
    when(users.findById(other))
        .thenReturn(Optional.of(User.restore(other, null, Role.PARTICIPANT, true)));

    for (CompleteTicketImportCommand command :
        new CompleteTicketImportCommand[] {
          new CompleteTicketImportCommand(OWNER, UUID.randomUUID(), "TYPD-2026"),
          new CompleteTicketImportCommand(other, pending.getId(), "TYPD-2026")
        }) {
      assertThatExceptionOfType(NotFoundException.class)
          .isThrownBy(() -> service().completeWithEventCode(command))
          .satisfies(e -> assertThat(e.getCode()).isEqualTo("IMPORT_NOT_FOUND"));
    }

    assertThat(imports.saved).containsKey(pending.getId());
    verifyNoInteractions(tickets);
  }

  @Test
  void letsOnlyAnActiveParticipantTypeTheCode() {
    TicketImport pending = pendingImport();

    assertThatExceptionOfType(NotAllowedException.class)
        .isThrownBy(
            () ->
                service()
                    .completeWithEventCode(
                        new CompleteTicketImportCommand(MEMBER, pending.getId(), "TYPD-2026")))
        .satisfies(e -> assertThat(e.getCode()).isEqualTo("PARTICIPANT_ONLY"));

    assertThat(imports.saved).containsKey(pending.getId());
  }

  @Test
  void neverPrintsTheFile() {
    assertThat(command(TICKET_FILE).toString()).doesNotContain("a ticket");
    assertThat(command(TICKET_FILE)).isEqualTo(command(TICKET_FILE));
    assertThat(command(TICKET_FILE)).hasSameHashCodeAs(command(TICKET_FILE));
    assertThat(command(null).file()).isEmpty();
  }
}
