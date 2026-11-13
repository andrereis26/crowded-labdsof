# ADR 0018: OpenStreetMap for maps and geocoding

- Status: accepted
- Date: 2026-11-13
- Deciders: Crowded team (André, Product Owner)
- Amends: [ADR 0010](0010-external-integrations.md) (maps vendor)

## Context

An event needs its venue's location (FR-015): the latitude and longitude are required, they place the venue on the participant's map (FR-056) and they decide who is near enough to report or confirm (FR-064). Organization members know the venue's address, not its coordinates, and asking them to type coordinates invites mistakes that only show up on the day.

ADR 0010 left the maps vendor open, behind an adapter. Creating an event (#36) is the first feature that needs it: the backoffice looks the address up while the member fills in O3 and shows the result on a map below the field, so they can check the pin before publishing. The member does not move the pin: if it is wrong, they correct the address.

## Options considered

- **OpenStreetMap:** Nominatim for geocoding and openstreetmap.org's embeddable map. Free and without a key. Nominatim's public instance allows at most one request per second, with a referer or user agent that identifies the application, and no bulk geocoding.
- **Google Maps Platform:** Geocoding API and Maps Embed API. Better address matching, but needs an API key, a billing account and a key restricted to our domains.
- **Mapbox:** similar to Google, with a key and a free tier.
- **Coordinates typed by the member, or a pin they drop on a map:** no geocoding, but either error-prone or a map library to add, and the member still has to find the place.
- **Geocoding through Core, behind an adapter:** the provider could change without a new backoffice release, and Core could cache and rate-limit, at the cost of an endpoint and an adapter for one form.

## Decision

- **OpenStreetMap, called from the backoffice.** O3 and the venue change in O4 look the address up in Nominatim when the member leaves the field (`backoffice/src/data/geocoding.ts`), limited to Portugal, and show the pin in openstreetmap.org's embedded map, with a link to open it there. The browser sends the backoffice's address as the referer, as Nominatim's policy asks.
- The `Geocoder` type is the adapter: the pages receive it, and tests pass a fake. Core stores the coordinates it receives and checks only their range.
- The participant app's map (FR-056) uses OpenStreetMap too unless its story decides otherwise.

## Consequences

- No key, account or cost. A member creates a few events a week, far below Nominatim's limit; looking up only when the field is left, not on every key, keeps it so.
- Nominatim's matching is weaker than Google's for incomplete addresses. The map lets the member see a wrong match and correct the address; when nothing matches, the form says so and the event cannot be published.
- Members' browsers call nominatim.openstreetmap.org and load tiles from openstreetmap.org. A Content Security Policy, if one is added to the backoffice, must allow both.
- Moving to another provider changes `geocoding.ts` and the map component only. If the public instance becomes a problem (rate limits, availability), the next step is geocoding through Core with a cache, or a hosted Nominatim.
