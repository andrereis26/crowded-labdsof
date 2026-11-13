package com.crowded.core.ticket.infrastructure.persistence.mapper;

import com.crowded.core.ticket.domain.model.TicketDetails;
import com.crowded.core.ticket.domain.model.TicketExtraction;
import com.crowded.core.ticket.domain.model.TicketImport;
import com.crowded.core.ticket.infrastructure.persistence.entity.TicketImportEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps pending ticket imports to the documents stored in Redis, and back. */
@Mapper
public interface TicketImportPersistenceMapper {

  @Mapping(target = "extractedEventCode", source = "extraction.extractedEventCode")
  @Mapping(target = "gate", source = "extraction.extractedFields.gate")
  @Mapping(target = "sector", source = "extraction.extractedFields.sector")
  @Mapping(target = "seat", source = "extraction.extractedFields.seat")
  TicketImportEntity toEntity(TicketImport ticketImport);

  default TicketImport toDomain(TicketImportEntity entity) {
    return TicketImport.restore(
        entity.id(),
        entity.ownerId(),
        entity.encryptedEntryCode(),
        new TicketExtraction(
            entity.extractedEventCode(),
            new TicketDetails(entity.gate(), entity.sector(), entity.seat())));
  }
}
