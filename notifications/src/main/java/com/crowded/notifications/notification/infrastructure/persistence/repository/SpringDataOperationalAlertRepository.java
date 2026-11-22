package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.infrastructure.persistence.entity.AlertEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to Core's {@code alert}. */
interface SpringDataOperationalAlertRepository extends Repository<AlertEntity, UUID> {

  Optional<AlertEntity> findById(UUID id);
}
