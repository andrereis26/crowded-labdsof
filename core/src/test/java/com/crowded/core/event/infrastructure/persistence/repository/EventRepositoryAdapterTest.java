package com.crowded.core.event.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.ConfirmationRules;
import com.crowded.core.event.domain.model.ConfirmationSettings;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.NewValues;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.QueuePoints;
import com.crowded.core.event.domain.model.ReportCategory;
import com.crowded.core.event.domain.model.RequestedChange;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.ReportCategoryRepository;
import com.crowded.core.event.infrastructure.persistence.entity.EventCategorySettingEntity;
import com.crowded.core.event.infrastructure.persistence.entity.EventEntity;
import com.crowded.core.event.infrastructure.persistence.entity.QueuePointEntity;
import com.crowded.core.event.infrastructure.persistence.mapper.EventPersistenceMapperImpl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventRepositoryAdapterTest {

  private static final List<ReportCategory> CATALOG =
      List.of(
          new ReportCategory("CROWD_LEVEL", false, true),
          new ReportCategory("ACCESSIBILITY_BARRIER", false, true),
          new ReportCategory("ON_SITE_PROBLEM", false, false),
          new ReportCategory("LOST_AND_FOUND", true, false));

  private final SpringDataEventRepository rows = mock(SpringDataEventRepository.class);
  private final SpringDataQueuePointRepository pointRows =
      mock(SpringDataQueuePointRepository.class);
  private final SpringDataEventCategorySettingRepository settingRows =
      mock(SpringDataEventCategorySettingRepository.class);
  private final ReportCategoryRepository categories = mock(ReportCategoryRepository.class);
  private final EventRepositoryAdapter events =
      new EventRepositoryAdapter(
          rows, pointRows, settingRows, categories, new EventPersistenceMapperImpl());

  private final EventEntity row = row();
  private final List<QueuePointEntity> points = new ArrayList<>();
  private final List<EventCategorySettingEntity> settings = new ArrayList<>();

  @BeforeEach
  void storeTheRows() {
    when(categories.findAll()).thenReturn(CATALOG);
    when(rows.findForUpdateById(row.getId())).thenReturn(Optional.of(row));
    when(rows.findById(row.getId())).thenReturn(Optional.of(row));
    when(pointRows.findByEventIdInOrderByPosition(anyCollection())).thenReturn(points);
    when(settingRows.findByEventIdIn(anyCollection())).thenReturn(settings);
  }

  @Test
  void readsTheLockedRowAsAnEvent() {
    points.add(point("Porta A", 0));
    points.add(point("Bar", 1));
    settings.add(setting("ACCESSIBILITY_BARRIER", false));
    settings.add(setting("ON_SITE_PROBLEM", true));

    Event event = events.findByIdForUpdate(row.getId()).orElseThrow();

    assertThat(event.getId()).isEqualTo(row.getId());
    assertThat(event.getOrganizationId()).isEqualTo(row.getOrganizationId());
    assertThat(event.getProfile().name()).isEqualTo("Concerto");
    assertThat(event.getAdmission().code()).isEqualTo(new EventCode("CONC-2026"));
    assertThat(event.getDistrict().name()).isEqualTo("Porto");
    assertThat(event.getCapacity()).isNull();
    assertThat(event.getState())
        .isEqualTo(
            new EventState(
                EventStatus.SCHEDULED,
                new Schedule(row.getStartDateTime(), row.getDoorsOpenAt(), row.getEndDateTime()),
                new Venue("Coliseu", "Porto", new Coordinates(41.1, -8.6))));
    assertThat(event.getQueuePoints().names()).containsExactly("Porta A", "Bar");
    // The event's own choice wins over a category's default; a personal one never prompts.
    assertThat(event.getConfirmations())
        .isEqualTo(
            new ConfirmationSettings(
                Set.of("CROWD_LEVEL", "ON_SITE_PROBLEM"), new ConfirmationRules(300, 6, 2)));
  }

  @Test
  void readsAPublicEventsAccessCode() {
    row.setType(EventType.PUBLIC);
    row.setTicketEventCode(null);
    row.setAccessCode("AVENIDA26");

    Event event = events.findById(row.getId()).orElseThrow();

    assertThat(event.getAdmission().accessCode()).isEqualTo("AVENIDA26");
  }

  @Test
  void writesTheNewStateToTheSameRow() {
    UUID organization = row.getOrganizationId();
    Event event = events.findByIdForUpdate(row.getId()).orElseThrow();
    event.publishChange(
        new RequestedChange(
            ChangeType.VENUE_CHANGE,
            new NewValues(null, null, null, "Hard Club", "Mercado Ferreira Borges", 41.141, -8.614),
            null,
            null,
            null),
        Provenance.organization("Ribeira Live", Instant.parse("2026-11-10T10:00:00Z")),
        Instant.parse("2026-11-10T10:00:00Z"));

    events.save(event);

    assertThat(row.getStatus()).isEqualTo(EventStatus.RELOCATED);
    assertThat(row.getVenueName()).isEqualTo("Hard Club");
    assertThat(row.getAddress()).isEqualTo("Mercado Ferreira Borges");
    assertThat(row.getLatitude()).isEqualTo(41.141);
    assertThat(row.getLongitude()).isEqualTo(-8.614);
    assertThat(row.getOrganizationId()).isEqualTo(organization);
    assertThat(row.getTicketEventCode()).isEqualTo("CONC-2026");
  }

  @Test
  void keepsTheQueuePointsThatStayAndWritesOnlyTheRest() {
    QueuePointEntity doorA = point("Porta A", 0);
    QueuePointEntity bar = point("Bar", 1);
    QueuePointEntity toilets = point("WC", 2);
    points.addAll(List.of(doorA, bar, toilets));
    Event event = events.findByIdForUpdate(row.getId()).orElseThrow();
    event.defineQueuePoints(QueuePoints.named(List.of("Bar", "Porta A", "Bilheteira")));

    events.save(event);

    verify(pointRows).deleteAll(List.of(toilets));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<QueuePointEntity>> saved = ArgumentCaptor.forClass(List.class);
    verify(pointRows).saveAll(saved.capture());
    assertThat(saved.getValue())
        .extracting(QueuePointEntity::getName, QueuePointEntity::getPosition)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Bar", 0),
            org.assertj.core.groups.Tuple.tuple("Porta A", 1),
            org.assertj.core.groups.Tuple.tuple("Bilheteira", 2));
    // A kept point keeps its id, and the waiting time that refers to it.
    assertThat(saved.getValue().getFirst().getId()).isEqualTo(bar.getId());
    assertThat(saved.getValue().getLast().getEventId()).isEqualTo(row.getId());
  }

  @Test
  void writesOnlyTheCategorySettingsThatDifferFromWhatTheEventHad() {
    EventCategorySettingEntity accessibility = setting("ACCESSIBILITY_BARRIER", false);
    settings.add(accessibility);
    Event event = events.findByIdForUpdate(row.getId()).orElseThrow();
    event.configureConfirmations(
        new ConfirmationSettings(
            Set.of("CROWD_LEVEL", "ACCESSIBILITY_BARRIER", "ON_SITE_PROBLEM"),
            ConfirmationRules.DEFAULT));

    events.save(event);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<EventCategorySettingEntity>> saved = ArgumentCaptor.forClass(List.class);
    verify(settingRows).saveAll(saved.capture());
    assertThat(saved.getValue())
        .extracting(
            EventCategorySettingEntity::getReportCategoryCode,
            EventCategorySettingEntity::isPromptsEnabled)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("ACCESSIBILITY_BARRIER", true),
            org.assertj.core.groups.Tuple.tuple("ON_SITE_PROBLEM", true));
    assertThat(saved.getValue()).contains(accessibility);
    assertThat(row.getVenueRadiusMeters()).isEqualTo(500);
    assertThat(row.getHighConfidenceAnswers()).isEqualTo(8);
    assertThat(row.getMaxPromptsPerHour()).isEqualTo(3);
  }

  @Test
  void addsANewEventWithItsRowFirst() {
    Event stored = events.findById(row.getId()).orElseThrow();
    when(pointRows.findByEventIdInOrderByPosition(anyCollection())).thenReturn(List.of());
    stored.defineQueuePoints(QueuePoints.named(List.of("Porta A")));

    events.add(stored);

    ArgumentCaptor<EventEntity> saved = ArgumentCaptor.forClass(EventEntity.class);
    verify(rows).save(saved.capture());
    verify(rows).flush();
    assertThat(saved.getValue().getId()).isEqualTo(row.getId());
    assertThat(saved.getValue().getOrganizationId()).isEqualTo(row.getOrganizationId());
    assertThat(saved.getValue().getName()).isEqualTo("Concerto");
    verify(pointRows).saveAll(any());
  }

  @Test
  void checksTheCodesUnderTheLockAndWithoutAnEventForANewOne() {
    Instant now = Instant.parse("2026-11-10T10:00:00Z");
    when(rows.existsUpcomingWithCode(
            "CONC-2026", new UUID(0, 0), now, Set.of(EventStatus.CANCELLED, EventStatus.ENDED)))
        .thenReturn(true);

    events.lockCodes();

    verify(rows).lockUntilCommit(EventRepositoryAdapter.EVENT_CODES_LOCK);
    assertThat(events.isCodeUsedByAnotherUpcomingEvent(new EventCode("conc-2026"), null, now))
        .isTrue();
    assertThat(
            events.isCodeUsedByAnotherUpcomingEvent(new EventCode("CONC-2026"), row.getId(), now))
        .isFalse();
  }

  @Test
  void failsWhenTheRowIsGone() {
    Event event = events.findById(row.getId()).orElseThrow();
    when(rows.findById(row.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> events.save(event)).isInstanceOf(IllegalStateException.class);
  }

  private QueuePointEntity point(String name, int position) {
    QueuePointEntity point = new QueuePointEntity();
    point.setId(UUID.randomUUID());
    point.setEventId(row.getId());
    point.setName(name);
    point.setPosition(position);
    return point;
  }

  private EventCategorySettingEntity setting(String code, boolean prompts) {
    EventCategorySettingEntity setting = new EventCategorySettingEntity();
    setting.setId(UUID.randomUUID());
    setting.setEventId(row.getId());
    setting.setReportCategoryCode(code);
    setting.setPromptsEnabled(prompts);
    return setting;
  }

  private static EventEntity row() {
    EventEntity row = new EventEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(UUID.randomUUID());
    row.setName("Concerto");
    row.setDescription("Um concerto.");
    row.setType(EventType.TICKETED);
    row.setTicketEventCode("CONC-2026");
    row.setStatus(EventStatus.SCHEDULED);
    row.setStartDateTime(Instant.parse("2026-11-20T20:00:00Z"));
    row.setDoorsOpenAt(Instant.parse("2026-11-20T18:30:00Z"));
    row.setEndDateTime(Instant.parse("2026-11-20T23:00:00Z"));
    row.setVenueName("Coliseu");
    row.setAddress("Porto");
    row.setDistrict("Porto");
    row.setLatitude(41.1);
    row.setLongitude(-8.6);
    row.setVenueRadiusMeters(300);
    row.setHighConfidenceAnswers(6);
    row.setMaxPromptsPerHour(2);
    return row;
  }
}
