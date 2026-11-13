package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.ReportCategory;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.ReportCategoryRepository;
import com.crowded.core.event.infrastructure.persistence.entity.EventCategorySettingEntity;
import com.crowded.core.event.infrastructure.persistence.entity.EventEntity;
import com.crowded.core.event.infrastructure.persistence.entity.QueuePointEntity;
import com.crowded.core.event.infrastructure.persistence.mapper.EventPersistenceMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * {@link EventRepository} on PostgreSQL: an event is its {@code event} row with its {@code
 * queue_point} and {@code event_category_setting} rows.
 */
@Repository
class EventRepositoryAdapter implements EventRepository {

  /** Key of the advisory lock that event codes are checked and taken under. */
  static final long EVENT_CODES_LOCK = 0x45564e54434f4445L;

  /** Events whose code is free again. */
  private static final Set<EventStatus> CLOSED =
      EnumSet.of(EventStatus.CANCELLED, EventStatus.ENDED);

  /** Stands for "no event" in the code check, which no event has as its id. */
  private static final UUID NO_EVENT = new UUID(0, 0);

  private final SpringDataEventRepository events;
  private final SpringDataQueuePointRepository points;
  private final SpringDataEventCategorySettingRepository settings;
  private final ReportCategoryRepository categories;
  private final EventPersistenceMapper mapper;

  EventRepositoryAdapter(
      SpringDataEventRepository events,
      SpringDataQueuePointRepository points,
      SpringDataEventCategorySettingRepository settings,
      ReportCategoryRepository categories,
      EventPersistenceMapper mapper) {
    this.events = events;
    this.points = points;
    this.settings = settings;
    this.categories = categories;
    this.mapper = mapper;
  }

  @Override
  public Optional<Event> findByIdForUpdate(UUID id) {
    return events.findForUpdateById(id).map(entity -> toDomain(List.of(entity)).getFirst());
  }

  @Override
  public Optional<Event> findById(UUID id) {
    return events.findById(id).map(entity -> toDomain(List.of(entity)).getFirst());
  }

  @Override
  public List<Event> findByOrganization(UUID organizationId) {
    return toDomain(events.findByOrganizationIdOrderByStartDateTimeDesc(organizationId));
  }

  @Override
  public void add(Event event) {
    EventEntity entity = new EventEntity();
    entity.setId(event.getId());
    entity.setOrganizationId(event.getOrganizationId());
    mapper.updateEntity(event, entity);
    events.save(entity);
    // The queue points and settings reference the row, so it goes first.
    events.flush();
    saveChildren(event);
  }

  @Override
  public void save(Event event) {
    // The row read (and locked) by findByIdForUpdate, from the same transaction.
    EventEntity entity =
        events
            .findById(event.getId())
            .orElseThrow(() -> new IllegalStateException("Event " + event.getId() + " is gone"));
    mapper.updateEntity(event, entity);
    saveChildren(event);
  }

  @Override
  public Map<UUID, Long> countByOrganization() {
    return events.countGroupedByOrganization().stream()
        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
  }

  @Override
  public void lockCodes() {
    events.lockUntilCommit(EVENT_CODES_LOCK);
  }

  @Override
  public boolean isCodeUsedByAnotherUpcomingEvent(EventCode code, UUID eventId, Instant now) {
    return events.existsUpcomingWithCode(
        code.value(), eventId == null ? NO_EVENT : eventId, now, CLOSED);
  }

  private List<Event> toDomain(List<EventEntity> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = rows.stream().map(EventEntity::getId).toList();
    Map<UUID, List<QueuePointEntity>> pointsByEvent =
        points.findByEventIdInOrderByPosition(ids).stream()
            .collect(Collectors.groupingBy(QueuePointEntity::getEventId));
    Map<UUID, List<EventCategorySettingEntity>> settingsByEvent =
        settings.findByEventIdIn(ids).stream()
            .collect(Collectors.groupingBy(EventCategorySettingEntity::getEventId));
    List<ReportCategory> catalog = categories.findAll();
    return rows.stream()
        .map(
            row ->
                mapper.toDomain(
                    row,
                    pointsByEvent.getOrDefault(row.getId(), List.of()),
                    settingsByEvent.getOrDefault(row.getId(), List.of()),
                    catalog))
        .toList();
  }

  /** Writes only what differs, so a point keeps its id, and its waiting time, while it stays. */
  private void saveChildren(Event event) {
    List<UUID> id = List.of(event.getId());
    saveQueuePoints(event, points.findByEventIdInOrderByPosition(id));
    saveCategorySettings(event, settings.findByEventIdIn(id));
  }

  private void saveQueuePoints(Event event, List<QueuePointEntity> stored) {
    List<String> names = event.getQueuePoints().names();
    Map<String, QueuePointEntity> byName =
        stored.stream().collect(Collectors.toMap(QueuePointEntity::getName, Function.identity()));
    List<QueuePointEntity> removed =
        stored.stream().filter(p -> !names.contains(p.getName())).toList();
    points.deleteAll(removed);
    List<QueuePointEntity> changed = new ArrayList<>();
    for (int position = 0; position < names.size(); position++) {
      QueuePointEntity point = byName.get(names.get(position));
      if (point == null) {
        point = new QueuePointEntity();
        point.setId(UUID.randomUUID());
        point.setEventId(event.getId());
        point.setName(names.get(position));
      } else if (point.getPosition() == position) {
        continue;
      }
      point.setPosition(position);
      changed.add(point);
    }
    points.saveAll(changed);
  }

  private void saveCategorySettings(Event event, Collection<EventCategorySettingEntity> stored) {
    Map<String, EventCategorySettingEntity> byCode = new HashMap<>();
    stored.forEach(s -> byCode.put(s.getReportCategoryCode(), s));
    List<EventCategorySettingEntity> changed = new ArrayList<>();
    for (ReportCategory category : categories.findAll()) {
      if (category.personal()) {
        continue;
      }
      boolean prompts = event.getConfirmations().promptsFor(category.code());
      EventCategorySettingEntity setting = byCode.get(category.code());
      boolean current =
          setting == null ? category.promptsEnabledByDefault() : setting.isPromptsEnabled();
      if (prompts == current) {
        continue;
      }
      if (setting == null) {
        setting = new EventCategorySettingEntity();
        setting.setId(UUID.randomUUID());
        setting.setEventId(event.getId());
        setting.setReportCategoryCode(category.code());
      }
      setting.setPromptsEnabled(prompts);
      changed.add(setting);
    }
    settings.saveAll(changed);
  }
}
