package com.crowded.core.event.application.service;

import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a connected participant, or a member of the owning organization, reads about an event: its
 * page and its change history.
 */
@Service
public class EventViewService {

  private final EventAccess access;
  private final EventPages pages;
  private final EventChangeRepository changes;

  EventViewService(EventAccess access, EventPages pages, EventChangeRepository changes) {
    this.access = access;
    this.pages = pages;
    this.changes = changes;
  }

  /**
   * The event's page.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user may not see it
   */
  public EventPage page(UUID userId, UUID eventId) {
    access.viewable(userId, eventId);
    return pages.current(eventId);
  }

  /**
   * Every official change to the event, the newest first; empty when it has none.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user may not see it
   */
  @Transactional(readOnly = true)
  public List<EventChangeView> history(UUID userId, UUID eventId) {
    access.viewable(userId, eventId);
    return changes.findByEvent(eventId).stream().map(EventChangeView::of).toList();
  }
}
