# ADR 0021: Browser sessions in HttpOnly cookies, with refresh tokens

- Status: accepted
- Date: 2026-11-19
- Deciders: Crowded team
- Amends: [ADR 0005](0005-authentication-in-core-with-jwt.md) (token lifetime, how clients receive and send tokens, refresh) and [ADR 0004](0004-redis-for-revocation-rate-limiting-and-cache.md) (Redis also keeps refresh tokens and revoked token ids)

## Context

Core signs in with a JWT valid for 1 hour, returned in the response body, with no refresh token (ADR 0005). The backoffice keeps it in memory, so reloading the page signs the user out. Keeping it in `sessionStorage` or `localStorage` would survive a reload, but any script on the page could read it, so one XSS bug would hand the token to an attacker.

The token in a header has other costs. An `<img>` tag cannot send it, so the backoffice fetches event images with JavaScript, and the participant app will have to do the same. `EventSource` cannot send it either, so the live streams (ADR 0007) need a workaround in browsers. After an hour every user signs in again. Signing out cannot end one session: Core only revokes all of a user's tokens issued before a time (ADR 0004), for a deactivated member.

The router serves the backoffice (`/backoffice/`), the participant web app (`/`) and the API (`/api/`) from one origin. The participant app also runs on Android and iOS, where cookies are awkward and the phone has secure storage.

## Options considered

1. **Token in web storage** (`sessionStorage` or `localStorage`). Survives a reload, but page scripts can read it, and images and live streams still need the header.
2. **Server-side sessions**: an opaque session cookie looked up in a store. Rejected in ADR 0005: every service would query the session store on each request.
3. **A backend-for-frontend** that keeps the tokens and proxies the API. Keeps tokens out of the browser, but adds a component between the router and the services.
4. **The JWT in an HttpOnly cookie for browsers, and rotating refresh tokens kept in Redis.** Phones keep the tokens in secure storage and send the header, as today.

## Decision

Option 4.

**Tokens.**

- The access token stays a JWT signed by Core (ADR 0005), now valid for **15 minutes**. Core keeps reading the role and organization from the database on each request (SR-001), so a short token never carries stale rights.
- A **refresh token** is 256 random bits. Core keeps only its SHA-256 hash in Redis, with the user and the session it belongs to. Each use **rotates** it: the old one is spent and a new one is issued in the same session.
- Session lifetimes, set by the role at sign-in:

  | Role | Ends after this long without use | Ends this long after sign-in, at most |
  |---|---|---|
  | Participant | 30 days | 90 days |
  | Organization member, administrator | 12 hours | 7 days |

  Backoffice accounts change events and accounts, so their sessions are shorter.
- **Reuse is theft.** A spent refresh token used again ends its whole session. Within 10 seconds of its rotation it is only refused, without ending the session: another tab refreshed at the same time, and the client retries with the new cookie.

**How clients receive and send them.**

- **Browsers** (the backoffice and the participant web app) ask for cookies with `"delivery": "COOKIE"` on `POST /auth/login` and `POST /auth/password`. Core sets two cookies and leaves the tokens out of the body:

  | Cookie | Holds | Attributes |
  |---|---|---|
  | `crowded_access` | Access token | `HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=900` |
  | `crowded_refresh` | Refresh token | `HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=` the idle lifetime |

  The refresh cookie only goes to `/api/auth/...`, so ordinary API calls never carry it. Core does not see the `/api` prefix, which the router removes, so the cookie path is configuration.
- **Phones** keep the default `"delivery": "BODY"`: the body carries `accessToken` and `refreshToken`, the app keeps them in the platform's secure storage (Keychain, Keystore) and sends `Authorization: Bearer`.
- **Core and Notifications read the `Authorization` header first, then the `crowded_access` cookie.** Jobs is not reachable through the router and keeps the header only.
- `POST /auth/refresh` takes the refresh cookie, or `refreshToken` in the body, and answers as the sign-in did. Clients call it once when a request gets `401`, then repeat the request. If it fails, the user is signed out.

**Cross-site requests.** Core turned CSRF protection off because it read no cookie; now it does. Both cookies are `SameSite=Strict`. Core and Notifications also refuse, with `403 ORIGIN_NOT_ALLOWED`, a POST, PUT, PATCH or DELETE whose `Origin` header names another host than the one the request was sent to, and one that carries a Crowded cookie without an `Origin`. Browsers send `Origin` with every such request; the router passes the `Host` the browser used, port included. The phone app sends neither header nor cookie, and a browser cannot add the bearer header to a cross-site request, so the phone app is never refused.

**Signing out and revocation.**

- `POST /auth/logout` ends the session: it deletes the refresh token, puts the access token's `jti` in a Redis deny-list until the token would have expired, and clears both cookies. Page scripts cannot delete an HttpOnly cookie, so only Core can sign a browser out. The phone app sends its refresh token in the body.
- Core and Notifications refuse a token whose `jti` is in the deny-list, next to the per-user revocation of ADR 0004.
- Deactivating a member, issuing a new temporary password, choosing a new password and deleting the account end every session of that user: the per-user revocation, plus deleting their refresh tokens.
- When Redis cannot be read, sign-in, refresh and token checks fail safe, as ADR 0004 requires.

**Who is signed in.** `GET /me` returns the signed-in user, or `401`. Each app calls it at start-up and keeps the result in its state: the backoffice in a React context, with a guard component on its routes. The guard only decides what to show; Core decides what each request may do. Both apps share the origin, so they share the session. A participant who opens the backoffice is told the account is for the app and can sign out. The same applies to a member in the participant app.

**Images and live streams.** In browsers, `<img src="/api/events/{id}/image">` and `EventSource` send the cookie, so event images and SSE streams need no JavaScript workaround in the backoffice or the participant web app. The phone app sends the header with its image and stream requests. When the access token expires, the server ends a stream. The client then refreshes the session and opens the stream again.

## Consequences

- No token is readable by page scripts in a browser. A reload or a new tab keeps the session, and sign-out ends it at once in every service.
- Sessions last days or weeks instead of an hour, and a stolen refresh token is detected the next time either holder uses it.
- Core keeps the refresh tokens in Redis, which writes them to disk (append-only file). If that data is lost, every user signs in again.
- Cookies make cross-site request forgery possible again. `SameSite=Strict` and the Origin check cover it. Comparing `Origin` with `Host` needs no list of origins per environment, but the router must keep passing the browser's `Host`.
- `Secure` cookies need HTTPS, so the router now serves it, with a Let's Encrypt certificate that a `certbot` service keeps (deployment/README.md, TLS). They also work on `http://localhost` in Chrome and Firefox, as the local stack runs; `crowded.sessions.cookie-secure` turns the attribute off for a browser that does not treat localhost as secure.
- Two ways to receive tokens (cookie and body) mean both have to be tested, in Core and in each client.
- Access tokens expire every 15 minutes, so clients refresh more often. Each refresh is one Redis read and one write.
