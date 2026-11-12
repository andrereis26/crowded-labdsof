package com.crowded.notifications.notification.domain.model;

import java.util.Objects;

/** What a notification says, in European Portuguese. */
public record NotificationText(String title, String body) {

  public NotificationText {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
  }
}
