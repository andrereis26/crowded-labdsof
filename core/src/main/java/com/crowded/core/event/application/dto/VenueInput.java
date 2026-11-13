package com.crowded.core.event.application.dto;

/** Where a new event takes place, as the member typed it and the map placed it. */
public record VenueInput(
    String name, String address, String district, Double latitude, Double longitude) {}
