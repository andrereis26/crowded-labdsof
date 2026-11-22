package com.crowded.core.event.infrastructure.persistence.mapper;

import com.crowded.core.event.domain.model.Alert;
import com.crowded.core.event.infrastructure.persistence.entity.AlertEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps sent alerts to {@code alert} rows. */
@Mapper
public interface AlertPersistenceMapper {

  @Mapping(target = "status", constant = "SENT")
  @Mapping(target = "recipientsCount", source = "recipients")
  AlertEntity toEntity(Alert alert);
}
