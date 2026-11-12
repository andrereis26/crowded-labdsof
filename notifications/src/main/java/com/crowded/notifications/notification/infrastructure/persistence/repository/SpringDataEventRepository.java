package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.infrastructure.persistence.entity.EventEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to Core's {@code event}, read only. */
interface SpringDataEventRepository extends Repository<EventEntity, UUID> {

  List<EventEntity> findByIdIn(Collection<UUID> ids);
}
