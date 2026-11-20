package com.crowded.core.user.api.dto;

/**
 * How a client receives its tokens (ADR 0021): in the body, for the phone app, or in cookies that
 * page scripts cannot read, for browsers.
 */
public enum TokenDelivery {
  BODY,
  COOKIE
}
