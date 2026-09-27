# User Research

## Research Method

To better understand the problems that Crownd is trying to solve, the team conducted user research through a **review of documented user experiences** from existing event platforms.

The platforms reviewed were:

* **Songkick**
* **Bandsintown**
* **Eventbrite**

The team analysed user reviews related to **discovering, planning and attending events**. The research focused mainly on the following areas:

| Research area                       | What was investigated                                 |
| ----------------------------------- | ----------------------------------------------------- |
| Event discovery and search          | Difficulties finding available and relevant events    |
| Notifications and event information | Alerts, reminders and event updates                   |
| Ticket access and management        | Difficulties accessing or managing tickets            |
| Personalisation                     | Recommendations and updates based on user preferences |
| Social features                     | Sharing events and connecting with other people       |

## Findings

The reviewed experiences showed both **problems and positive experiences** that are relevant to Crownd.

### Event Discovery and Search

One Eventbrite user reported difficulties seeing all available results because the search stopped loading after a few scrolls. The user explained that this made it difficult to explore what was available without narrowing the search using keywords.

In contrast, a Bandsintown user described being able to see upcoming shows in their area as a positive experience.

These findings provide initial support for Crownd's focus on **location-based and centralised event discovery**. Crownd's Event Explorer persona identifies difficulty finding local experiences and missing events of interest as important pain points.

### Review evidence

**Eventbrite – Event discovery**

![Eventbrite review about search/discovery](images/Eventbrite%20-%20Search%20-%20Review.png)

**Bandsintown – Local event discovery**

![Bandsintown review about local discovery](images/Bandsintown%20-%20Review.png)

---

### Notifications and Event Information

One Bandsintown user reported not receiving alerts about a band they liked even though notifications were enabled. The same user also described difficulties signing in and accessing tickets while travelling to a concert.

Another Bandsintown user had a positive experience with reminders and personalised event updates.

These experiences suggest that **event notifications can be useful but reliability and accessibility are important**. This supports Crownd's planned event notifications and reminders. The ticket-access problem also provides initial support for Crownd's focus on keeping ticket information accessible within the product.

### Review evidence

**Bandsintown – Notifications and ticket access**

![Bandsintown review about notifications and ticket access](images/Bandsintown%20-%20Notification%20-%20Review%202.png)

**Bandsintown – Positive notification experience**

![Eventbrite review about notifications](images/Bandsintown%20-%20Review.png)

---

### Social Features

A Songkick user reported difficulty finding a way to share an event with friends who might be interested in attending.

This is relevant to Crownd's idea of connecting people interested in the same events. However, this finding is based on **one individual review**, so it should not be considered sufficient evidence that social sharing is a widespread problem.

### Review evidence

**Songkick – Event sharing**

![Songkick review about event sharing](images/Songkick%20-%20Share%20-%20Review.png)

---

### Personalisation

A positive Eventbrite review described receiving updates about interesting events that were tailored to the user's personal preferences.

This provides initial support for Crownd's focus on **personalised event discovery and recommendations**.

### Review evidence

**Eventbrite – Personalised updates**

![Eventbrite review about personalised updates](images/EventBrite%20-%20Review%202.png)

---

## Summary of Findings

The main findings can be summarised as follows:

| Finding                              | Evidence from reviews                                                       | Relevance to Crownd                                       |
| ------------------------------------ | --------------------------------------------------------------------------- | --------------------------------------------------------- |
| Event discovery can be difficult     | Eventbrite user had problems loading and exploring all search results       | Supports effective event discovery and search             |
| Local discovery can be useful        | Bandsintown user valued seeing upcoming shows in their area                 | Supports radius-based discovery                           |
| Event reminders can be useful        | Bandsintown user had a positive experience with reminders                   | Supports event reminders                                  |
| Ticket access can be problematic     | Bandsintown user had difficulty accessing tickets while travelling          | Supports centralised ticket management                    |
| Event sharing can be difficult       | Songkick user had difficulty sharing an event with friends                  | Provides an initial reason to investigate social features |
| Personalised updates can be valuable | Eventbrite user liked updates based on personal preferences                 | Supports personalised recommendations                     |

---

## Limitations

The research has several limitations that need to be considered when interpreting the findings.

First, the research was based **only on documented user reviews from existing event platforms**.

Second, there is limited information about the people who wrote the reviews. Their characteristics, event-attendance habits and specific circumstances are not known. Therefore, these individual experiences cannot be assumed to represent Crownd's target users as a whole.

Third, the research does not provide **quantitative evidence** about how common the identified problems are. For example, one user reporting a notification problem shows that this problem occurred for that user, but does not establish how frequently notification failures occur across the platform.

Finally, some of Crownd's proposed features are based mainly on the team's assumptions rather than evidence from these reviews. In particular, the research does not directly validate:

* Crowd reports
* Organiser interest polls
* AI-powered recommendations

---

## Resulting Product Decisions

Based on the research findings, the team identified which Crownd features have some initial supporting evidence and which remain assumptions requiring further validation.

| Research finding                                                   | Resulting product decision                                         | Current evidence        |
| ------------------------------------------------------------------ | ------------------------------------------------------------------ | ----------------------- |
| Users may have difficulty finding available events                 | Keep effective event discovery and search as a core part of Crownd | Initially supported     |
| Users value seeing events in their area                            | Include radius-based event discovery                               | Initially supported     |
| Users may not receive important event alerts                       | Include reliable event notifications                               | Initially supported     |
| Users value event reminders                                        | Include event reminders                                            | Initially supported     |
| Users may experience difficulty accessing tickets                  | Include centralised ticket management/ticket upload                | Initially supported     |
| Users value personalised event updates                             | Include personalised recommendations                               | Initially supported     |
| Event sharing may be difficult                                     | Investigate social/event-sharing features further                  | Initial indication only |
| No reviewed experience directly validates crowd reports            | Keep as an assumption requiring further research                   | Not validated           |
| No reviewed experience directly validates organiser interest polls | Keep as an assumption requiring further research                   | Not validated           |
| No reviewed experience directly validates AI recommendations       | Keep as an assumption requiring further research                   | Not validated           |

These decisions are consistent with Crownd's current MVP, which includes:

* Event feed
* Radius-based search
* Category filtering
* Personalised recommendations
* User profiles
* Favourites
* Ticket upload
* Event reminders
* Event creation
* Event updates
* Interest polling
* AI recommendation engine

The research therefore provides **initial evidence for several parts of Crownd's product direction**, particularly event discovery, location-based discovery, personalised updates, notifications and ticket management. At the same time, the research makes clear that other proposed capabilities remain assumptions.
