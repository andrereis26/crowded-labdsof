# User Research

## Research Method

To better understand the problems that Crowded is trying to solve, the team conducted user research through a **review of documented user experiences** from existing event platforms.

The platforms reviewed were:

* **Songkick**
* **Bandsintown**
* **Eventbrite**
* **Festing**

The team analysed user reviews related to **discovering, planning and attending events**. The research focused mainly on the following areas:

| Research area                       | What was investigated                                 |
| ----------------------------------- | ----------------------------------------------------- |
| Event discovery and search          | Difficulties finding available and relevant events    |
| Notifications and event information | Alerts, reminders and event updates                   |
| Ticket access and management        | Difficulties accessing or managing tickets            |
| Personalisation                     | Recommendations and updates based on user preferences |
| Social features                     | Sharing events and connecting with other people       |

## Findings

The reviewed experiences showed both **problems and positive experiences** that are relevant to Crowded.

### Event Discovery and Search

One Eventbrite user reported difficulties seeing all available results because the search stopped loading after a few scrolls. The user explained that this made it difficult to explore what was available without narrowing the search using keywords.

In contrast, a Bandsintown user described being able to see upcoming shows in their area as a positive experience.

The review of Festing also showed an existing approach focused on discovering events based on users' interests and location. This is relevant to Crowded because it demonstrates the importance of making relevant events easier to discover rather than requiring users to search across multiple sources.

These findings show that seeing local events is valued by users. In Crowded, discovery is kept as a **secondary entry point** (a simple "near me / today" list), since existing platforms already address it and it is not the core of the product.

### Review evidence

**Eventbrite – Event discovery**

![Eventbrite review about search/discovery](images/Eventbrite%20-%20Search%20-%20Review.png)

**Bandsintown – Local event discovery**

![Bandsintown review about local discovery](images/Bandsintown%20-%20Review.png)

---

### Notifications and Event Information

One Bandsintown user reported not receiving alerts about a band they liked even though notifications were enabled. The same user also described difficulties signing in and accessing tickets while travelling to a concert.

Another Bandsintown user had a positive experience with reminders and personalised event updates.

These experiences suggest that **event notifications can be useful but reliability and accessibility are important**. This supports Crowded's core focus on reliable event change notifications. The ticket-access problem also provides initial support for Crowded's ticket wallet, which keeps the user's tickets and their event information accessible in one place.

### Review evidence

**Bandsintown – Notifications and ticket access**

![Bandsintown review about notifications and ticket access](images/Bandsintown%20-%20Notification%20-%20Review%202.png)

**Bandsintown – Positive notification experience**

![Eventbrite review about notifications](images/Bandsintown%20-%20Review.png)

---

### Social Features

A Songkick user reported difficulty finding a way to share an event with friends who might be interested in attending.

The review of Festing also showed that social interaction can be incorporated directly into the event-discovery experience, with users able to interact around events and see other participants.

This finding is based on **one individual review**, so it should not be considered sufficient evidence that social sharing is a widespread problem. Social features are out of scope for Crowded's MVP.

### Review evidence

**Songkick – Event sharing**

![Songkick review about event sharing](images/Songkick%20-%20Share%20-%20Review.png)

---

### Personalisation

A positive Eventbrite review described receiving updates about interesting events that were tailored to the user's personal preferences.

The review of Festing also showed a focus on discovering events according to users' interests and location.

This shows that personalised updates can be valued, but personalised recommendations are related to event discovery and are therefore out of scope for Crowded's MVP.

### Review evidence

**Eventbrite – Personalised updates**

![Eventbrite review about personalised updates](images/EventBrite%20-%20Review%202.png)

---

## Summary of Findings

The main findings can be summarised as follows:

| Finding                                                     | Evidence from reviews                                                                | Relevance to Crowded                                                           |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------ |
| Event discovery can be difficult                            | Eventbrite user had problems loading and exploring all search results                | Discovery is kept only as a secondary entry point                              |
| Local discovery can be useful                               | Bandsintown user valued seeing upcoming shows in their area                          | Supports a simple "near me / today" list                                       |
| Existing platforms use location and interests for discovery | Festing provides event discovery based on user interests and location                | Supports the relevance of personalised and location-based discovery            |
| Event notifications can fail                                | Bandsintown user did not receive alerts despite having them enabled                  | Supports reliable event change notifications (core)                            |
| Event reminders can be useful                               | Bandsintown user had a positive experience with reminders                            | Supports notifications about the user's events                                 |
| Ticket access can be problematic                            | Bandsintown user had difficulty accessing tickets while travelling                   | Supports the ticket wallet                                                     |
| Event sharing can be difficult                              | Songkick user had difficulty sharing an event with friends                           | Out of scope for the MVP                                                       |
| Social interaction can be part of event discovery           | Festing incorporates social interaction around events                                | Provides a reference for potential community features                          |
| Personalised updates can be valuable                        | Eventbrite user liked updates based on personal preferences                          | Out of scope for the MVP (related to discovery)                                |

---

## Limitations

The research has several limitations that need to be considered when interpreting the findings.

First, the research was based **only on documented user reviews from existing event platforms**.

Second, there is limited information about the people who wrote the reviews. Their characteristics, event-attendance habits and specific circumstances are not known. Therefore, these individual experiences cannot be assumed to represent Crowded's target users as a whole.

Third, the research does not provide **quantitative evidence** about how common the identified problems are. For example, one user reporting a notification problem shows that this problem occurred for that user, but does not establish how frequently notification failures occur across the platform.

Fourth, the reviewed platforms focus mainly on event discovery and ticketing. The research therefore provides little direct evidence about **on-site disruptions** such as overcrowding, queues and incidents, or about the needs of **event organizers**, who were not part of the research.

Finally, some of Crowded's proposed features are based mainly on the team's assumptions rather than evidence from these reviews. In particular, the research does not directly validate:

* Organizer-published crowd levels and queues
* Organizer alerts and the organizer backoffice
* Attendance-intention polls
* Participant incident reporting
* AI-assisted ticket extraction and alert drafting

Future research should address these gaps, for example through a survey of event attendees about missed event changes and on-site problems, and interviews with local event organizers.

---

## Resulting Product Decisions

Based on the research findings, the team identified which Crowded features have some initial supporting evidence and which remain assumptions requiring further validation.

| Research finding                                                     | Resulting product decision                                          | Current evidence        |
| -------------------------------------------------------------------- | ------------------------------------------------------------------- | ----------------------- |
| Users may not receive important event alerts                         | Make reliable event change notifications a core feature             | Initially supported     |
| Users may experience difficulty accessing tickets                    | Include a ticket wallet with ticket import                          | Initially supported     |
| Users value seeing events in their area                              | Keep a simple "near me / today" list as a secondary entry point     | Initially supported     |
| Users may have difficulty finding available events                   | Do not compete on discovery; existing platforms already address it  | Initially supported     |
| Users value personalised event updates                               | Leave personalised recommendations out of the MVP                   | Initially supported     |
| Event sharing may be difficult                                       | Leave social features out of the MVP                                | Initial indication only |
| No reviewed experience validates organizer-published on-site data    | Keep as an assumption requiring further research                    | Not validated           |
| No reviewed experience validates organizer alerts                    | Keep as an assumption requiring further research                    | Not validated           |
| No reviewed experience validates attendance-intention polls          | Keep as an assumption requiring further research                    | Not validated           |
| No reviewed experience validates participant incident reporting      | Keep as an assumption requiring further research                    | Not validated           |

These decisions are consistent with Crowded's current MVP, which includes:

* Ticket wallet (ticket import)
* Following public events and venue QR codes
* Minimal event discovery ("near me / today")
* Event change notifications
* Organizer backoffice and API (crowd levels, queues, incidents, alerts)
* Attendance-intention polls
* Participant incident reporting
* Provenance labels
* Weather alerts

The research therefore provides **initial evidence for several parts of Crowded's product direction**, particularly event change notifications and ticket management. The review of Festing also provides a useful reference for existing approaches to combining event discovery with social interaction.
