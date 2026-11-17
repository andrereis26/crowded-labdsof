package com.crowded.core.ticket.infrastructure.persistence.mapper;

import com.crowded.core.shared.infrastructure.persistence.JsonColumnConverter;
import com.crowded.core.ticket.domain.model.EntryCodeHash;
import com.crowded.core.ticket.domain.model.Ticket;
import com.crowded.core.ticket.domain.model.TicketDetails;
import com.crowded.core.ticket.domain.model.TicketExtraction;
import com.crowded.core.ticket.infrastructure.persistence.entity.TicketEntity;
import com.crowded.core.ticket.infrastructure.persistence.entity.TicketExtractionEntity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Maps tickets and their extractions between the domain and their rows. */
@Mapper
public interface TicketPersistenceMapper {

  @Mapping(source = "details.gate", target = "gate")
  @Mapping(source = "details.sector", target = "sector")
  @Mapping(source = "details.seat", target = "seat")
  @Mapping(source = "entryCodeHash.value", target = "entryCodeHash")
  @Mapping(
      target = "entryCode",
      expression =
          "java(ticket.getEncryptedEntryCode().getBytes(java.nio.charset.StandardCharsets.UTF_8))")
  TicketEntity toEntity(Ticket ticket);

  /** The ticket a row stores, with the participant its connection belongs to. */
  default Ticket toDomain(TicketEntity entity, UUID ownerId) {
    return Ticket.restore(
        Objects.requireNonNull(entity.getId(), "id"),
        ownerId,
        new TicketDetails(entity.getGate(), entity.getSector(), entity.getSeat()),
        new String(
            Objects.requireNonNull(entity.getEntryCode(), "entryCode"), StandardCharsets.UTF_8),
        new EntryCodeHash(entity.getEntryCodeHash()),
        entity.getImportMethod(),
        entity.getMatchMethod());
  }

  /** The row of what the AI read from a stored ticket; the AI made all of it. */
  @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
  @Mapping(source = "extraction.extractedEventCode", target = "extractedEventCode")
  @Mapping(source = "extraction", target = "extractedFields", qualifiedByName = "fieldsAsJson")
  @Mapping(target = "aiGenerated", constant = "true")
  @Mapping(source = "now", target = "createdAt")
  TicketExtractionEntity toExtractionEntity(
      UUID ticketId, TicketExtraction extraction, Instant now, @Context JsonColumnConverter json);

  /** The extracted gate, sector and seat as the {@code jsonb} column keeps them. */
  @Named("fieldsAsJson")
  default String fieldsAsJson(TicketExtraction extraction, @Context JsonColumnConverter json) {
    TicketDetails details = extraction.extractedFields();
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("gate", details.gate());
    fields.put("sector", details.sector());
    fields.put("seat", details.seat());
    return json.toJson(fields);
  }
}
