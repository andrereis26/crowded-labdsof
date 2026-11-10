package com.crowded.core.event;

import java.util.UUID;

/**
 * The change a request recorded or, when it was sent before, the one recorded the first time.
 *
 * @param created whether this request recorded it
 */
record PublishedChange(UUID changeId, Severity severity, boolean created) {}
