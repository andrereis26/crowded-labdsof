package com.crowded.core.event.domain.model;

import java.util.Objects;

/**
 * A predefined kind of report, such as the crowd level or a queue.
 *
 * @param personal whether its reports are about the reporter, such as a lost item: they are never
 *     public and never ask others to confirm them
 * @param promptsEnabledByDefault whether it asks for confirmations at an event that did not choose
 */
public record ReportCategory(String code, boolean personal, boolean promptsEnabledByDefault) {

  public ReportCategory {
    Objects.requireNonNull(code, "code");
  }
}
