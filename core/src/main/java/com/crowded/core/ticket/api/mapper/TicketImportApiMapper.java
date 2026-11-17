package com.crowded.core.ticket.api.mapper;

import com.crowded.core.ticket.api.dto.ImportedEventResponse;
import com.crowded.core.ticket.api.dto.TicketDetailsResponse;
import com.crowded.core.ticket.api.dto.TicketImportResponse;
import com.crowded.core.ticket.application.dto.ImportTicketCommand;
import com.crowded.core.ticket.application.dto.ImportedEvent;
import com.crowded.core.ticket.application.dto.TicketImportResult;
import com.crowded.core.ticket.domain.model.TicketDetails;
import java.util.UUID;
import org.mapstruct.Mapper;

/** Maps the ticket import endpoint's request and response to and from the application layer. */
@Mapper
public interface TicketImportApiMapper {

  ImportTicketCommand toCommand(UUID ownerId, byte[] file);

  TicketImportResponse toResponse(TicketImportResult result);

  TicketDetailsResponse toResponse(TicketDetails details);

  ImportedEventResponse toResponse(ImportedEvent event);
}
