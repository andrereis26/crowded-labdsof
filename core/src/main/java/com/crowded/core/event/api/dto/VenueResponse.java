package com.crowded.core.event.api.dto;

/** Where an event takes place. */
public record VenueResponse(
    String name, String address, String district, Double latitude, Double longitude) {}
