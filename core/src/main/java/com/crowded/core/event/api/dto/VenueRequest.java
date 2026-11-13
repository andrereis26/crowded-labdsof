package com.crowded.core.event.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Where a new event takes place.
 *
 * @param district a district of Portugal or an autonomous region, as IPMA names it
 * @param latitude where the address is on the map, as the backoffice found it
 */
public record VenueRequest(
    @Size(max = 200) String name,
    @Size(max = 300) String address,
    @Size(max = 60) String district,
    Double latitude,
    Double longitude) {}
