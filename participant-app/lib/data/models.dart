import 'package:flutter/material.dart';

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
  final ReportCategory category;
  final DateTime reportedAt;
  final int confirmed;
  final int denied;
  final ConfidenceLevel confidence;
}

enum ChangeKind { published, dateChanged, timeChanged, venueChanged, cancelled }

/// An entry in an event's change history (P6, P7).
class EventChange {
  const EventChange({
    required this.id,
    required this.kind,
    required this.title,
    required this.description,
    required this.detectedAt,
    required this.publisher,
    this.previousStart,
    this.newStart,
    this.ticketStillValid = true,
    this.refund,
  });

  final String id;
  final ChangeKind kind;
  final String title;
  final String description;
  final DateTime detectedAt;

  /// The organization that published it ("Teatro Municipal"): the only
  /// source of event information (ADR 0015).
  final String publisher;
  final DateTime? previousStart;
  final DateTime? newStart;

  /// "O bilhete continua válido para a nova data" (O4).
  final bool ticketStillValid;

  /// Refund information written by the organization, if any (P7).
  final Refund? refund;
}

/// Refund information that the organization adds to a change (O4, P7).
class Refund {
  const Refund({required this.until, required this.where});

  final DateTime until;

  /// "teatromunicipal.pt/reembolsos".
  final String where;
}

/// IPMA weather warning level.
enum WarningLevel {
  yellow('Aviso amarelo'),
  orange('Aviso laranja'),
  red('Aviso vermelho');

  const WarningLevel(this.label);

  final String label;

  /// Red warnings are critical; orange and red are notified, yellow only
  /// shows on the event page.
  bool get critical => this == red;
}

/// An IPMA warning for the event's district during the event.
class WeatherWarning {
  const WeatherWarning({
    required this.level,
    required this.title,
    required this.issuedAt,
    required this.until,
    this.detail,
  });

  final WarningLevel level;

  /// "Vento forte no distrito do Porto".
  final String title;

  /// "Rajadas até 90 km/h".
  final String? detail;
  final DateTime issuedAt;
  final DateTime until;
}

/// An event registered by an organization (ADR 0015). Ticketed events are
/// matched by the event code printed on their tickets; public events are
/// joined with an access code.
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
    this.updatedAt,
    this.places = const [],
    this.directions,
    this.district = 'Porto',
    this.warnings = const [],
    this.eventCode,
    this.accessCode,
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

  /// When the organization last updated the event.
  final DateTime? updatedAt;

  /// Gates and services a report can point to ("Onde?" in P11).
  final List<VenuePlace> places;
  final Directions? directions;

  /// District used for IPMA warnings and forecasts.
  final String district;

  /// IPMA warnings for the district during the event, most severe first.
  final List<WeatherWarning> warnings;

  /// Code printed on the tickets of a ticketed event ("MARG-2026").
  final String? eventCode;

  /// Code of a public event, shown on its posters ("SAOJOAO24").
  final String? accessCode;

  bool get isPublic => accessCode != null;

  String get place => '$venue, $city';

  /// The change that set the current date, if the event was postponed.
  EventChange? get latestDateChange =>
      changes.where((c) => c.kind == ChangeKind.dateChanged).firstOrNull;
}

enum TicketOrigin {
  pdf('Importado de PDF'),
  image('Importado de uma imagem');

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
    this.codeReference,
  });

  final String id;

  /// The registered event whose code matched the ticket.
  final Event event;
  final TicketOrigin origin;
  final DateTime importedAt;
  final String? gate;
  final String? seat;
  final bool hasEntryCode;

  /// Last digits of the entry code, shown under it (P19).
  final String? codeReference;
}

enum NotificationKind {
  alert('Alerta da organização'),
  request('Pedido da organização'),

  /// Asks to confirm another participant's report (P13).
  confirmation('Pedido de confirmação'),
  recognition('Reconhecimento'),
  officialChange('Alteração oficial'),
  weather('Aviso meteorológico'),

  /// What happened to a report the participant sent.
  reportStatus('Estado do reporte');

  const NotificationKind(this.typeLabel);

  /// Read by screen readers on the notification icon.
  final String typeLabel;
}

enum NotificationFilter {
  all('Todas'),
  changes('Alterações'),
  alerts('Alertas'),
  weather('Tempo'),
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
    this.route,
    this.urgent = false,
    this.responses,
    this.read = false,
  });

  final String id;
  final NotificationKind kind;
  final String eventId;
  final String eventName;

  /// "Alerta da organização", "Pedido urgente": read by screen readers on
  /// the notification icon.
  final String typeLabel;
  final String title;
  final String? body;

  /// "Organização Margens", "Fonte: IPMA". Shown on request cards (P17), not
  /// in the notification centre.
  final String? source;
  final DateTime receivedAt;
  final String? changeId;

  /// Screen opened on tap, when it is not the event page or a change.
  final String? route;

  /// Urgent requests are highlighted in P17.
  final bool urgent;

  /// How many people answered a request so far.
  final int? responses;
  bool read;

  /// Answer to a request ("Sim" / "Não"), once given.
  bool? answer;

  /// Where opening it goes: its screen, the official change, or the event.
  String get target =>
      route ??
      (changeId != null
          ? '/evento/$eventId/alteracao/$changeId'
          : '/evento/$eventId');

  bool matches(NotificationFilter filter) => switch (filter) {
    NotificationFilter.all => true,
    NotificationFilter.changes => kind == NotificationKind.officialChange,
    NotificationFilter.alerts => kind == NotificationKind.alert,
    NotificationFilter.weather => kind == NotificationKind.weather,
    NotificationFilter.requests =>
      kind == NotificationKind.request || kind == NotificationKind.confirmation,
  };
}

/// What happened to a ticket file after it was read (P2 → P2a / P2b).
enum ImportOutcome {
  /// The event code matched a registered event: the ticket was added (P2a).
  added,

  /// No event code was read, or the code that was read matches no event:
  /// the participant types it (P2b).
  needsEventCode,

  /// No QR code in the file: the ticket is not stored.
  noQrCode,

  /// The typed code matches no event: nothing is stored.
  eventNotOnCrowded,

  /// The ticket is already linked to another account.
  linkedToAnotherAccount,
}

/// A ticket file being imported, with what was read from it (P2b).
class TicketImport {
  const TicketImport({
    required this.fileName,
    required this.origin,
    this.readCode,
  });

  final String fileName;
  final TicketOrigin origin;

  /// Event code read from the ticket, when there was one ("MARG-2O26").
  final String? readCode;
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

/// Notification preferences (C2).
class NotificationPreferences {
  bool officialChanges = true;
  bool organizerAlerts = true;
  bool weatherAlerts = true;
  bool recognitions = true;
  RequestNotificationLevel requests = RequestNotificationLevel.urgentOnly;
  bool confirmationPrompts = true;
  final Map<String, EventNotificationLevel> perEvent = {};
}

/// The signed-in participant. Crowded keeps only the email, no name (data
/// minimisation).
class Account {
  const Account({required this.email});

  final String email;

  String get initials => email.isEmpty ? '' : email[0].toUpperCase();
}

/// A gate or service at the venue, with the preposition used before it
/// ("na Porta C", "no Bar principal", "nos WC junto ao palco").
class VenuePlace {
  const VenuePlace(this.name, this.preposition);

  final String name;
  final String preposition;

  String get withPreposition => '$preposition $name';
}

/// What a participant can report at the venue (P11).
enum ReportCategory {
  crowd('Lotação', 'a lotação', Icons.people_outline, AppTagTone.warning),
  queue('Fila', 'a fila', Icons.schedule, AppTagTone.warning),
  accessibility(
    'Acessibilidade',
    'acessibilidade',
    Icons.accessibility_new,
    AppTagTone.info,
  ),
  venueProblem(
    'Problema no recinto',
    'um problema',
    Icons.build_outlined,
    AppTagTone.warning,
  ),
  lostAndFound(
    'Perdidos e achados',
    'um objeto perdido',
    Icons.shopping_bag_outlined,
    AppTagTone.neutral,
  );

  const ReportCategory(this.label, this.subject, this.icon, this.tone);

  final String label;

  /// Used in sentences: "o teu reporte sobre a fila na Porta C".
  final String subject;
  final IconData icon;
  final AppTagTone tone;

  /// Lost items can be reported away from the venue (P12).
  bool get needsPresence => this != lostAndFound;
}

/// Waiting time options for a queue report.
enum WaitTime {
  under10('< 10 min', 'Espera de menos de 10 minutos'),
  from10To20('10–20', 'Espera de 10 a 20 minutos'),
  from20To40('20–40', 'Espera de 20 a 40 minutos'),
  over40('> 40', 'Espera de mais de 40 minutos');

  const WaitTime(this.label, this.sentence);

  final String label;
  final String sentence;
}

/// A report sent from P11.
class ParticipantReport {
  const ParticipantReport({
    required this.id,
    required this.eventId,
    required this.category,
    required this.place,
    required this.sentAt,
    this.waitTime,
    this.crowdLevel,
    this.description = '',
  });

  final String id;
  final String eventId;
  final ReportCategory category;
  final VenuePlace place;
  final DateTime sentAt;
  final WaitTime? waitTime;
  final CrowdLevel? crowdLevel;
  final String description;

  /// "Fila na Porta C".
  String get title {
    final subject = category.subject;
    final noun = subject.startsWith('a ')
        ? subject.substring(2)
        : subject.startsWith('um ')
        ? subject.substring(3)
        : subject;
    return '${noun[0].toUpperCase()}${noun.substring(1)} '
        '${place.withPreposition}';
  }
}

enum ConfirmationAnswer { yes, no, cantSee }

/// Another participant's report that nearby people are asked to confirm
/// (P13).
class ConfirmationRequest {
  ConfirmationRequest({
    required this.id,
    required this.eventId,
    required this.eventName,
    required this.category,
    required this.title,
    required this.detail,
    required this.reportedAt,
  });

  final String id;
  final String eventId;
  final String eventName;
  final ReportCategory category;

  /// "Fila longa na Porta C".
  final String title;

  /// "Espera de 20 a 40 minutos".
  final String detail;
  final DateTime reportedAt;
  ConfirmationAnswer? answer;
}

/// The organizer marked a report as helpful (P15).
class Recognition {
  const Recognition({
    required this.id,
    required this.eventId,
    required this.organizer,
    required this.reportTitle,
    required this.category,
    required this.reportedAt,
    required this.resolvedAfter,
    required this.resolution,
    required this.alertReach,
    required this.alertMessage,
  });

  final String id;
  final String eventId;
  final String organizer;
  final String reportTitle;
  final ReportCategory category;
  final DateTime reportedAt;
  final Duration resolvedAfter;

  /// "Água reposta no bar principal".
  final String resolution;

  /// People who received the organizer's alert.
  final int alertReach;
  final String alertMessage;
}

/// Open question from the organizer (P17).
class IdeaPrompt {
  IdeaPrompt({
    required this.id,
    required this.eventId,
    required this.question,
    required this.sentAt,
    required this.ideasShared,
  });

  final String id;
  final String eventId;
  final String question;
  final DateTime sentAt;
  int ideasShared;
  bool shared = false;
}

enum TransportMode {
  metro(Icons.directions_subway_outlined, AppTagTone.info),
  bus(Icons.directions_bus_outlined, AppTagTone.info),
  walk(Icons.directions_walk, AppTagTone.neutral),
  car(Icons.directions_car_outlined, AppTagTone.neutral);

  const TransportMode(this.icon, this.tone);

  final IconData icon;
  final AppTagTone tone;
}

class TransportOption {
  const TransportOption({
    required this.mode,
    required this.title,
    required this.detail,
    required this.minutes,
    this.disrupted = false,
  });

  final TransportMode mode;
  final String title;
  final String detail;
  final int minutes;

  /// Shows "Perturbação" and the detail in warning colours.
  final bool disrupted;
}

/// How to get to the venue (P18).
class Directions {
  const Directions({
    required this.entrance,
    required this.entranceDetail,
    required this.options,
    required this.operators,
    required this.updatedAt,
  });

  /// Transport operators whose GTFS timetables feed the options
  /// ("Metro do Porto e STCP").
  final String operators;

  /// "Porta A".
  final String entrance;

  /// "Entrada principal".
  final String entranceDetail;
  final List<TransportOption> options;
  final DateTime updatedAt;
}

enum ContributionKind {
  report('Reporte', Icons.flag_outlined),
  confirmation('Confirmação', Icons.check),
  helpful('Reporte útil', Icons.workspace_premium_outlined);

  const ContributionKind(this.label, this.icon);

  final String label;
  final IconData icon;
}

enum ContributionFilter {
  all('Todas', Icons.inbox_outlined),
  reports('Reportes', Icons.flag_outlined),
  confirmations('Confirmações', Icons.check),
  helpful('Marcados como úteis', Icons.workspace_premium_outlined);

  const ContributionFilter(this.label, this.icon);

  final String label;
  final IconData icon;
}

/// A report or confirmation the participant made, with what happened to it
/// (P20). Private to the participant.
class Contribution {
  const Contribution({
    required this.id,
    required this.kind,
    required this.eventId,
    required this.eventName,
    required this.title,
    required this.status,
    required this.at,
    required this.tone,
    this.route,
  });

  final String id;
  final ContributionKind kind;
  final String eventId;
  final String eventName;
  final String title;

  /// "Confirmado por 6 pessoas · agrupado num incidente".
  final String status;
  final DateTime at;

  /// Colour of the icon: in review, confirmed, resolved…
  final AppTagTone tone;
  final String? route;

  bool matches(ContributionFilter filter) => switch (filter) {
    ContributionFilter.all => true,
    ContributionFilter.reports => kind != ContributionKind.confirmation,
    ContributionFilter.confirmations => kind == ContributionKind.confirmation,
    ContributionFilter.helpful => kind == ContributionKind.helpful,
  };
}
