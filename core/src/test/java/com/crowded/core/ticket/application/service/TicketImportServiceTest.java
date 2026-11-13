package com.crowded.core.ticket.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.domain.exception.TooManyRequestsException;
import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.shared.domain.repository.RateLimiter;
import com.crowded.core.ticket.application.dto.ImportTicketCommand;
import com.crowded.core.ticket.application.dto.TicketImportResult;
import com.crowded.core.ticket.application.dto.TicketImportStatus;
import com.crowded.core.ticket.domain.model.ReadTicket;
import com.crowded.core.ticket.domain.model.TicketDetails;
import com.crowded.core.ticket.domain.model.TicketImport;
import com.crowded.core.ticket.domain.repository.EntryCodeCipher;
import com.crowded.core.ticket.domain.repository.TicketExtractor;
import com.crowded.core.ticket.domain.repository.TicketFileReader;
import com.crowded.core.ticket.domain.repository.TicketImportRepository;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TicketImportServiceTest {

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

    @Override
    public void save(TicketImport ticketImport) {
      saved.put(ticketImport.getId(), ticketImport);
    }

    @Override
    public Optional<TicketImport> findById(UUID importId) {
      return Optional.ofNullable(saved.get(importId));
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

  private TicketImportService service(
      TicketExtractionRunner extraction, EntryCodeCipher cipher, RateLimiter limiter) {
    return new TicketImportService(READER, extraction, cipher, imports, limiter, users, 20);
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

  @Test
  void neverPrintsTheFile() {
    assertThat(command(TICKET_FILE).toString()).doesNotContain("a ticket");
    assertThat(command(TICKET_FILE)).isEqualTo(command(TICKET_FILE));
    assertThat(command(TICKET_FILE)).hasSameHashCodeAs(command(TICKET_FILE));
    assertThat(command(null).file()).isEmpty();
  }
}
