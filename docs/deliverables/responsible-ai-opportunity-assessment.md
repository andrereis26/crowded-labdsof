# Responsible AI Opportunity Assessment

## Proposed AI-Assisted Workflow

1. **Personalised recommendations:** When a user opens Crowded, retrieve their saved interests and approximate location, then find events inside their chosen radius. Rank the results and show recommendations. Run this at most once per user per day. A language model such as ChatGPT could optionally help rank or explain the shortlist; ordinary filters and ranking can provide the same workflow without a model.
2. **Weather-related event notifications:** A daily job checks events scheduled for that day and the forecast for their locations. When a configured severe-weather threshold is met, notify users who are interested in the affected events and have enabled these notifications. Use a trusted weather source and clear wording that describes the forecast; a language model is optional and should not decide whether conditions are severe.

## Expected Value

Recommendations could make local event discovery more relevant and reduce the effort of searching across disconnected sources. Weather notifications could help interested users make informed travel plans and give organisers an additional way to reach their audience.

## Risks

### R1 - Privacy and Data Security
Location and preference data are personal. 

**Mitigation**
> Ask for consent, use approximate location and only the data needed, and avoid sending identifiable user data to an external AI provider.


### R2 - Bias and Fairness
Recommendations may over-favour popular events, miss new events or reflect incomplete preferences. 

**Mitigation**
> Keep the full event list searchable and let users change interests and location settings.

#### R3 - Reliability and Accuracy
Incorrect forecasts or thresholds could cause unnecessary or missed alerts. 

**Mitigation**
> Label forecasts with their source or update time, avoid implying that an event is cancelled, and let organisers publish confirmed changes separately.

#### R4 - User Trust
Unwanted or frequent notifications can reduce trust. 

**Mitigation**
> Respect notification preferences and provide controls to mute or disable them.

## Evaluation Approach

Pilot with users who opt in and compare AI-ranked recommendations with the existing location, radius and category-filtered event list. Measure recommendation opens, saves and participation; notification opens and opt-outs; and forecast-alert accuracy, including false alerts. Review results across locations and for users with little preference history. Stop or adjust the pilot if relevance does not improve or unwanted notifications increase.

## Baseline or Fallback Strategy

Use location, radius, category and interest filters with a transparent non-AI ranking as the recommendation baseline. If the AI service is unavailable, show those results. For weather notifications, apply published thresholds to the weather feed and notify only when the forecast is available and meets the threshold. If the feed is missing or stale, skip the automated alert and leave confirmed event updates to organisers.
