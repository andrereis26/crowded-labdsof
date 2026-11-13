package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.EventCategorySettingEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code event_category_setting}. */
interface SpringDataEventCategorySettingRepository
    extends JpaRepository<EventCategorySettingEntity, UUID> {

  List<EventCategorySettingEntity> findByEventIdIn(Collection<UUID> eventIds);
}
