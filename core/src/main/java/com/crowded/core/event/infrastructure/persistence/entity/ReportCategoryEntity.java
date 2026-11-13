package com.crowded.core.event.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Row of {@code report_category}. Maps only the columns Core uses so far. */
@Entity
@Table(name = "report_category")
public class ReportCategoryEntity {

  @Id private String code;

  private boolean personal;

  @Column(name = "prompts_enabled_by_default")
  private boolean promptsEnabledByDefault;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public boolean isPersonal() {
    return personal;
  }

  public void setPersonal(boolean personal) {
    this.personal = personal;
  }

  public boolean isPromptsEnabledByDefault() {
    return promptsEnabledByDefault;
  }

  public void setPromptsEnabledByDefault(boolean promptsEnabledByDefault) {
    this.promptsEnabledByDefault = promptsEnabledByDefault;
  }
}
