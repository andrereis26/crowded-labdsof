package com.crowded.core.event.infrastructure.persistence.mapper;

import com.crowded.core.event.domain.model.EventImage;
import com.crowded.core.event.infrastructure.persistence.entity.EventImageEntity;
import com.crowded.core.shared.domain.model.Image;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps {@code event_image} rows to event images, and event images to rows. */
@Mapper
public interface EventImagePersistenceMapper {

  default EventImage toDomain(EventImageEntity entity) {
    return EventImage.restore(
        entity.getEventId(),
        Image.restore(entity.getMediaType(), entity.getContent()),
        entity.getUpdatedAt());
  }

  @Mapping(target = "mediaType", expression = "java(image.getImage().mediaType())")
  @Mapping(target = "content", expression = "java(image.getImage().content())")
  EventImageEntity toEntity(EventImage image);
}
