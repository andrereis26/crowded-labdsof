package com.crowded.core.event.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Row of {@code event_category_setting}: whether a report category asks for confirmations at one
 * event, instead of the category's default.
 */
@Entity
@Table(name = "event_category_setting")
public class EventCategorySettingEntity {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "report_category_code", nullable = false)
  private String reportCategoryCode;

  @Column(name = "prompts_enabled")
  private boolean promptsEnabled;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public String getReportCategoryCode() {
    return reportCategoryCode;
  }

  public void setReportCategoryCode(String reportCategoryCode) {
    this.reportCategoryCode = reportCategoryCode;
  }

  public boolean isPromptsEnabled() {
    return promptsEnabled;
  }

  public void setPromptsEnabled(boolean promptsEnabled) {
    this.promptsEnabled = promptsEnabled;
  }
}
