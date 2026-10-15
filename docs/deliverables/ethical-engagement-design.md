# Ethical Engagement Design

## Mechanism

Crowded encourages participation through **recognition** and a **private contribution history**:

- **Recognition:** when the organization marks a participant's report as helpful, the participant is notified of its impact, for example "the incident was resolved" or "an alert was sent to 1 240 people" (FR-054).
- **Contribution history:** each participant can see a private list of their reports and confirmations, with the outcome of each one: confirmed by other participants, grouped into an incident, marked as helpful, or resolved (FR-038).

There are no points, badges, rankings or public counters. Recognition is only visible to the participant who earned it.

## 1. Behaviour It Encourages

The mechanism encourages participants to **report disruptions accurately and early** (crowd levels, queues, closed entrances, accessibility barriers and on-site problems) and to **answer confirmation prompts honestly**.

## 2. Why It Benefits the User and the Community

- Participant reports fill the gap when the organization publishes nothing: confirmed reports become participant estimates that everyone at the event can see (FR-059).
- Honest confirmations raise or lower a report's confidence, so other participants only see estimates that people at the venue agree with (FR-057, FR-058).
- Organizers learn about problems sooner and handle one incident instead of many duplicates (FR-053), which supports the incident-response criterion in the [Product Vision](product-vision.md#success-criteria).
- Recognition closes the loop: knowing that a report led to an action makes people more likely to report the next disruption.

## 3. How Misuse and Manipulation Are Prevented

- **Recognition depends on usefulness, not volume.** Only the organization can mark a report as helpful, so sending more reports earns nothing.
- **Nothing to compete for.** Without points, rankings or public counters, there is no score to game.
- **Reports must come from the venue.** Reporting in non-personal categories and confirming require the user to be near the venue; the location is checked once and is not stored (FR-064).
- **Limits on volume.** Reports and confirmation answers are rate-limited (SR-011), and only a limited random sample of other users, excluding the reporter, is asked to confirm (FR-057).
- **Duplicates add no impact.** Similar reports are grouped into one incident (FR-053), so repeating the same report does not increase its impact.
- **Unconfirmed reports stay hidden.** Only reports with Medium or High confidence are shown, always labelled as estimates and with the number of people who confirmed them (FR-059). The confidence level itself is only shown to the organization.

## 4. Could It Exclude, Expose or Discourage Users?

| Risk | Who is affected | Mitigation |
|---|---|---|
| **Exclusion** | Users who do not allow location cannot report in non-personal categories or confirm, so they cannot earn recognition | Recognition is never needed to use the product: every piece of information stays available to everyone. Personal categories such as lost and found do not require location. |
| **Exposure** | Reporters, whose identity could reveal where they were | Recognition and the history are private. The reporter's identity is never public (SR-007) and confirmations are anonymous. |
| **Discouragement** | Users whose reports are not marked as helpful | The history shows every outcome, not only "helpful", so users see that their report was counted, confirmed or grouped. There is no negative feedback or public failure. |
| **Prompt fatigue** | Users asked to confirm too often | Only a small sample is asked, each user is asked a limited number of times (FR-057), and prompts can be turned off per event (FR-041). |

## 5. How Users Control or Opt Out

- Recognition notifications can be turned off overall or for each event (FR-041, FR-065).
- Confirmation prompts can be turned off for each event (FR-041).
- The contribution history is private and is deleted with the account (FR-042).
- Not reporting or confirming has no consequence for the user.
- Answering an interest poll never follows the event or subscribes the user to anything: following is a separate button on the poll page (FR-027). Poll answers are never shown individually to the organization or to other users (SR-019).
