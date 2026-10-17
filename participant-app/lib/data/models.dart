import 'package:flutter/painting.dart';

import '../ui/ui.dart';

/// Placeholder picture of an event: a gradient until the catalog has photos.
class EventArt {
  const EventArt(this.from, this.to);

  final Color from;
  final Color to;

  LinearGradient get gradient => LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [from, to],
    stops: const [0.14, 0.86],
  );
}

enum EventStatus { scheduled, postponed, cancelled }

/// Queue at a gate or service on the event day.
class QueueTime {
  const QueueTime({
    required this.place,
    required this.minutes,
    this.increasing = false,
  });

  final String place;
  final int minutes;
  final bool increasing;
}

/// Live on-site information for the event day (P5).
class VenueStatus {
  const VenueStatus({
    required this.crowdLevel,
    required this.crowdUpdatedAt,
    required this.queues,
    required this.queuesUpdatedAt,
  });

  final CrowdLevel crowdLevel;
  final DateTime crowdUpdatedAt;
  final List<QueueTime> queues;
  final DateTime queuesUpdatedAt;
}

class WeatherInfo {
  const WeatherInfo({
    required this.summary,
    required this.detail,
    required this.updatedAt,
    this.icon = WeatherIcon.sun,
  });

  /// "18° · Céu limpo".
  final String summary;

  /// "Chuva fraca possível depois das 23h (10%)".
  final String detail;
  final DateTime updatedAt;
  final WeatherIcon icon;
}

enum WeatherIcon { sun, cloud, rain }

/// Organizer alert shown on the event page.
class EventAlert {
  const EventAlert({
    required this.message,
    required this.publisher,
    required this.sentAt,
    this.urgent = false,
  });

  final String message;
  final String publisher;
  final DateTime sentAt;
  final bool urgent;
}

/// Participant estimate confirmed by others (P5 desktop, P14).
class ParticipantEstimate {
  const ParticipantEstimate({
    required this.title,
    required this.category,
    required this.reportedAt,
    required this.confirmed,
    required this.denied,
    required this.confidence,
  });

  final String title;
  final String category;
  final DateTime reportedAt;
  final int confirmed;
  final int denied;
  final ConfidenceLevel confidence;
}

enum ChangeSource {
  official('Oficial', 'Bilheteira oficial'),
  organization('Organização', 'Organização');

  const ChangeSource(this.label, this.defaultPublisher);

  final String label;
  final String defaultPublisher;
}

enum ChangeKind {
  published,
  venueConfirmed,
  dateChanged,
  timeChanged,
  cancelled,
}

/// An entry in an event's change history (P6, P7).
class EventChange {
  const EventChange({
    required this.id,
    required this.kind,
    required this.title,
    required this.description,
    required this.detectedAt,
    required this.source,
    required this.publisher,
    this.previousStart,
    this.newStart,
  });

  final String id;
  final ChangeKind kind;
  final String title;
  final String description;
  final DateTime detectedAt;
  final ChangeSource source;
  final String publisher;
  final DateTime? previousStart;
  final DateTime? newStart;

  /// Name used after "Fonte:" ("bilheteira oficial", "Teatro Municipal").
  String get sourceName =>
      source == ChangeSource.official ? 'bilheteira oficial' : publisher;
}

/// An event in the catalog.
class Event {
  const Event({
    required this.id,
    required this.name,
    required this.venue,
    required this.city,
    required this.start,
    required this.art,
    this.doorsOpen,
    this.end,
    this.status = EventStatus.scheduled,
    this.organizer = 'Organização',
    this.changes = const [],
    this.alerts = const [],
    this.venueStatus,
    this.weather,
    this.estimates = const [],
    this.distanceKm,
    this.sourceUpdatedAt,
  });

  final String id;
  final String name;
  final String venue;
  final String city;
  final DateTime start;
  final DateTime? doorsOpen;
  final DateTime? end;
  final EventArt art;
  final EventStatus status;
  final String organizer;

  /// Newest first.
  final List<EventChange> changes;
  final List<EventAlert> alerts;
  final VenueStatus? venueStatus;
  final WeatherInfo? weather;
  final List<ParticipantEstimate> estimates;
  final double? distanceKm;

  /// Last sync with the ticketing source.
  final DateTime? sourceUpdatedAt;

  String get place => '$venue, $city';

  /// The change that set the current date, if the event was postponed.
  EventChange? get latestDateChange =>
      changes.where((c) => c.kind == ChangeKind.dateChanged).firstOrNull;
}

enum TicketOrigin {
  pdf('Importado de PDF'),
  email('Importado de email'),
  manual('Introduzido à mão');

  const TicketOrigin(this.label);

  final String label;
}

/// A ticket in the participant's wallet.
class Ticket {
  const Ticket({
    required this.id,
    required this.event,
    required this.origin,
    required this.importedAt,
    this.gate,
    this.seat,
    this.hasEntryCode = true,
  });

  final String id;

  /// The linked catalog event. Tickets saved without a link (P4, "Nenhum
  /// destes") get an event built from the ticket data and no changes.
  final Event event;
  final TicketOrigin origin;
  final DateTime importedAt;
  final String? gate;
  final String? seat;
  final bool hasEntryCode;
}

/// Event the participant follows without a ticket.
class FollowedEvent {
  const FollowedEvent(this.event);

  final Event event;
}

enum NotificationKind { alert, request, recognition, officialChange, weather }

enum NotificationFilter {
  all('Todas'),
  changes('Alterações'),
  alerts('Alertas'),
  requests('Pedidos');

  const NotificationFilter(this.label);

  final String label;
}

class AppNotification {
  AppNotification({
    required this.id,
    required this.kind,
    required this.eventId,
    required this.eventName,
    required this.typeLabel,
    required this.title,
    required this.receivedAt,
    this.body,
    this.source,
    this.changeId,
    this.read = false,
  });

  final String id;
  final NotificationKind kind;
  final String eventId;
  final String eventName;

  /// "Alerta da organização", "Pedido urgente".
  final String typeLabel;
  final String title;
  final String? body;

  /// "Organização Margens", "Fonte: IPMA".
  final String? source;
  final DateTime receivedAt;
  final String? changeId;
  bool read;

  /// Answer to a request ("Sim" / "Não"), once given.
  bool? answer;

  bool matches(NotificationFilter filter) => switch (filter) {
    NotificationFilter.all => true,
    NotificationFilter.changes => kind == NotificationKind.officialChange,
    NotificationFilter.alerts =>
      kind == NotificationKind.alert || kind == NotificationKind.weather,
    NotificationFilter.requests => kind == NotificationKind.request,
  };
}

/// Data extracted from a ticket file, or typed in, before it is saved (P3).
class TicketDraft {
  TicketDraft({
    required this.origin,
    this.eventName = '',
    this.date,
    this.gate = '',
    this.doorsOpen = '',
    this.start = '',
    this.place = '',
    this.seat = '',
    this.hasEntryCode = false,
  });

  final TicketOrigin origin;
  String eventName;
  DateTime? date;
  String gate;
  String doorsOpen;
  String start;
  String place;
  String seat;
  bool hasEntryCode;
}

/// A catalog event suggested for a draft ticket (P4).
class EventMatch {
  const EventMatch({required this.event, required this.strong});

  final Event event;

  /// "Correspondência alta" when true, "Correspondência baixa" otherwise.
  final bool strong;
}

/// What a participant receives for one event (C2, "Por evento").
enum EventNotificationLevel {
  all('Tudo'),
  officialOnly('Só alterações oficiais'),
  none('Nada');

  const EventNotificationLevel(this.label);

  final String label;
}

/// Which organization requests reach the participant (C2).
enum RequestNotificationLevel {
  all('Todos'),
  urgentOnly('Só urgentes'),
  none('Nenhum');

  const RequestNotificationLevel(this.label);

  final String label;
}

/// Notification preferences (C2, FR-069).
class NotificationPreferences {
  bool officialChanges = true;
  bool organizerAlerts = true;
  bool weatherAlerts = true;
  bool recognitions = true;
  bool officialChangesByEmail = true;
  RequestNotificationLevel requests = RequestNotificationLevel.urgentOnly;
  bool confirmationPrompts = true;
  final Map<String, EventNotificationLevel> perEvent = {};
}

/// The signed-in participant.
class Account {
  const Account({required this.name, required this.email});

  final String name;
  final String email;

  String get initials => name
      .split(RegExp(r'\s+'))
      .where((p) => p.isNotEmpty)
      .take(2)
      .map((p) => p[0].toUpperCase())
      .join();
}
