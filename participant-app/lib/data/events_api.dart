import 'dart:async';
import 'dart:convert';

import 'package:flutter/painting.dart';
import 'package:http/http.dart' as http;

import 'auth_api.dart';
import '../ui/ui.dart' show CrowdLevel;
import '../utils/pt_format.dart';
import 'models.dart';
import 'notifications_api.dart' show ServerSentEvents;

/// A refusal from Core, with its error code (`NOT_CONNECTED`,
/// `EVENT_NOT_FOUND`, `UNAUTHORIZED`), `NETWORK` when Core could not be
/// reached, or `HTTP_<status>` without a code.
class EventsException implements Exception {
  const EventsException(this.code);

  final String code;

  /// The participant may no longer see the event: it is gone, or they left.
  bool get noLongerVisible =>
      code == 'NOT_CONNECTED' || code == 'EVENT_NOT_FOUND';

  @override
  String toString() => 'EventsException($code)';
}

/// An event's page and change history as Core sends them (API Outline,
/// journey 3).
abstract interface class EventsApi {
  /// `GET /events/{eventId}`.
  Future<EventPage> page(String eventId);

  /// `GET /events/{eventId}/changes`, newest first.
  Future<List<CoreChange>> changes(String eventId);

  /// `GET /events/{eventId}/live`: the page now, then again after each
  /// change, until the server closes the stream or the subscription is
  /// cancelled. Fails with an [EventsException] when it cannot be opened
  /// or drops.
  Stream<EventPage> live(String eventId);
}

/// What `GET /events/{eventId}` says about an event.
class EventPage {
  const EventPage({
    required this.id,
    required this.name,
    required this.status,
    required this.start,
    required this.venueName,
    required this.district,
    required this.organization,
    this.isPublic = false,
    this.doorsOpen,
    this.end,
    this.latestChange,
    this.venueStatus,
    this.alerts = const [],
  });

  final String id;
  final String name;
  final EventStatus status;
  final DateTime start;
  final DateTime? doorsOpen;
  final DateTime? end;
  final String venueName;
  final String district;

  /// The organization's name: the source of every detail.
  final String organization;

  /// Joined with an access code rather than a ticket.
  final bool isPublic;
  final CoreChange? latestChange;

  /// The crowd level the organization published; null before the first.
  final VenueStatus? venueStatus;

  /// The alerts the organization sent and has not resolved, newest first.
  /// Shown even to a participant who turned their notifications off.
  final List<EventAlert> alerts;
}

/// An official change as Core sends it, before the app words it.
class CoreChange {
  const CoreChange({
    required this.id,
    required this.type,
    required this.previousValue,
    required this.newValue,
    required this.publisher,
    required this.publishedAt,
    this.ticketStillValid,
    this.refundInfo,
    this.message,
    this.severity = Severity.information,
  });

  final String id;

  /// `POSTPONEMENT`, `TIME_CHANGE`, `VENUE_CHANGE` or `CANCELLATION`.
  final String type;
  final Map<String, dynamic> previousValue;
  final Map<String, dynamic> newValue;
  final String publisher;
  final DateTime publishedAt;
  final bool? ticketStillValid;
  final String? refundInfo;
  final String? message;

  /// How much the change matters.
  final Severity severity;
}

/// [EventsApi] over HTTP, with the session's client ([AuthApi.client]),
/// which sends the token or the cookies.
class HttpEventsApi implements EventsApi {
  HttpEventsApi({http.Client Function()? client, String baseUrl = apiBaseUrl})
    : _client = client ?? http.Client.new,
      _base = Uri.base.resolve(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');

  final http.Client Function() _client;
  final Uri _base;

  @override
  Future<EventPage> page(String eventId) async {
    final body = await _get('events/${Uri.encodeComponent(eventId)}');
    final page = body is Map<String, dynamic> ? eventPageFromJson(body) : null;
    if (page == null) throw const EventsException('INVALID_RESPONSE');
    return page;
  }

  @override
  Future<List<CoreChange>> changes(String eventId) async {
    final body = await _get('events/${Uri.encodeComponent(eventId)}/changes');
    if (body is! List) throw const EventsException('INVALID_RESPONSE');
    return [
      for (final item in body.whereType<Map<String, dynamic>>())
        ?coreChangeFromJson(item),
    ];
  }

  @override
  Stream<EventPage> live(String eventId) {
    final client = _client();
    late final StreamController<EventPage> controller;
    StreamSubscription<String>? lines;
    controller = StreamController<EventPage>(
      onListen: () async {
        try {
          final request = http.Request(
            'GET',
            _base.resolve('events/${Uri.encodeComponent(eventId)}/live'),
          )..headers['Accept'] = 'text/event-stream';
          final response = await _send(client, request);
          if (controller.isClosed) return;
          final parser = ServerSentEvents();
          lines = response.stream
              .transform(utf8.decoder)
              .transform(const LineSplitter())
              .listen(
                (line) {
                  final event = parser.add(line);
                  if (event == null || event.name != 'event') return;
                  final page = _pageFrom(event.data);
                  if (page != null) controller.add(page);
                },
                onError: (Object error) {
                  controller.addError(const EventsException('NETWORK'));
                  controller.close();
                },
                onDone: controller.close,
                cancelOnError: true,
              );
        } on EventsException catch (e) {
          controller.addError(e);
          await controller.close();
        }
      },
      onCancel: () async {
        await lines?.cancel();
        client.close();
      },
    );
    return controller.stream;
  }

  /// A page sent on the stream; a malformed one is skipped, as the next
  /// change sends the page again.
  static EventPage? _pageFrom(String data) {
    try {
      final json = jsonDecode(data);
      return json is Map<String, dynamic> ? eventPageFromJson(json) : null;
    } on FormatException {
      return null;
    }
  }

  Future<Object?> _get(String path) async {
    final client = _client();
    try {
      final response = await _send(
        client,
        http.Request('GET', _base.resolve(path)),
      );
      final String text;
      try {
        text = await response.stream.bytesToString();
      } on Exception {
        throw const EventsException('NETWORK');
      }
      try {
        return text.isEmpty ? null : jsonDecode(text);
      } on FormatException {
        throw const EventsException('INVALID_RESPONSE');
      }
    } finally {
      client.close();
    }
  }

  /// Sends [request] in the session; an answer other than 2xx becomes an
  /// [EventsException] with Core's error code.
  Future<http.StreamedResponse> _send(
    http.Client client,
    http.Request request,
  ) async {
    final http.StreamedResponse response;
    try {
      response = await client.send(request);
    } on Exception {
      throw const EventsException('NETWORK');
    }
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return response;
    }
    if (response.statusCode == 401) {
      await response.stream.drain<void>().catchError((Object _) {});
      throw const EventsException('UNAUTHORIZED');
    }
    String? code;
    try {
      final body = jsonDecode(await response.stream.bytesToString());
      if (body is Map<String, dynamic> && body['code'] is String) {
        code = body['code'] as String;
      }
    } on Exception {
      // No problem details: the status says enough.
    }
    throw EventsException(code ?? 'HTTP_${response.statusCode}');
  }
}

/// The page in [json], or null when a required field is missing.
EventPage? eventPageFromJson(Map<String, dynamic> json) {
  final id = json['id'];
  final name = json['name'];
  final start = _instant(json['startDateTime']);
  final venue = json['venue'];
  final organization = json['organization'];
  if (id is! String ||
      name is! String ||
      start == null ||
      venue is! Map<String, dynamic> ||
      organization is! Map<String, dynamic>) {
    return null;
  }
  final latest = json['latestChange'];
  final organizationName = organization['name'] as String? ?? 'Organização';
  final alerts = json['activeAlerts'];
  return EventPage(
    id: id,
    name: name,
    status: eventStatusFromCore(json['status']),
    start: start,
    doorsOpen: _instant(json['doorsOpenAt']),
    end: _instant(json['endDateTime']),
    venueName: venue['name'] as String? ?? '',
    district: venue['district'] as String? ?? '',
    organization: organizationName,
    isPublic: json['type'] == 'PUBLIC',
    latestChange: latest is Map<String, dynamic>
        ? coreChangeFromJson(latest)
        : null,
    venueStatus: venueStatusFromJson(json),
    alerts: [
      if (alerts is List)
        for (final item in alerts.whereType<Map<String, dynamic>>())
          ?eventAlertFromJson(item, publisher: organizationName),
    ],
  );
}

/// An active alert as Core sends it, or null when a required field is
/// missing. A critical one is urgent.
EventAlert? eventAlertFromJson(
  Map<String, dynamic> json, {
  String publisher = 'Organização',
}) {
  final message = json['message'];
  final sentAt = _instant(json['sentAt']);
  if (message is! String || sentAt == null) return null;
  final severity = severityFromCore(json['severity']);
  return EventAlert(
    message: message,
    publisher: publisher,
    sentAt: sentAt,
    urgent: severity == Severity.critical,
    severity: severity,
  );
}

/// The crowd level and queue times on Core's event [page], or null when it
/// has neither. One whose value or time is missing or unknown to this
/// version is left out.
VenueStatus? venueStatusFromJson(Map<String, dynamic> page) {
  final crowd = page['crowd'];
  final level = crowd is Map<String, dynamic>
      ? switch (crowd['level']) {
          'LOW' => CrowdLevel.low,
          'MODERATE' => CrowdLevel.moderate,
          'HIGH' => CrowdLevel.high,
          'FULL' => CrowdLevel.full,
          _ => null,
        }
      : null;
  final crowdAt = crowd is Map<String, dynamic> ? _updatedAt(crowd) : null;
  final hasCrowd = level != null && crowdAt != null;

  final queues = <QueueTime>[];
  DateTime? queuesAt;
  for (final queue in page['queues'] is List ? page['queues'] as List : []) {
    if (queue is! Map<String, dynamic>) continue;
    final place = queue['pointName'];
    final minutes = queue['waitMinutes'];
    final at = _updatedAt(queue);
    if (place is! String || minutes is! int || at == null) continue;
    queues.add(QueueTime(place: place, minutes: minutes));
    // The oldest, so the panel never shows the times as fresher than they are.
    if (queuesAt == null || at.isBefore(queuesAt)) queuesAt = at;
  }

  if (!hasCrowd && queues.isEmpty) return null;
  return VenueStatus(
    crowdLevel: hasCrowd ? level : null,
    crowdUpdatedAt: hasCrowd ? crowdAt : null,
    queues: queues,
    queuesUpdatedAt: queuesAt,
  );
}

/// The `lastUpdatedAt` of [json]'s provenance.
DateTime? _updatedAt(Map<String, dynamic> json) {
  final provenance = json['provenance'];
  return provenance is Map<String, dynamic>
      ? _instant(provenance['lastUpdatedAt'])
      : null;
}

/// The change in [json], or null when a required field is missing.
CoreChange? coreChangeFromJson(Map<String, dynamic> json) {
  final id = json['id'];
  final type = json['type'];
  final provenance = json['provenance'];
  final publishedAt = provenance is Map<String, dynamic>
      ? _instant(provenance['lastUpdatedAt'])
      : null;
  if (id is! String || type is! String || publishedAt == null) return null;
  final previous = json['previousValue'];
  final next = json['newValue'];
  return CoreChange(
    id: id,
    type: type,
    previousValue: previous is Map<String, dynamic> ? previous : const {},
    newValue: next is Map<String, dynamic> ? next : const {},
    publisher:
        stringFromJson((provenance as Map<String, dynamic>)['sourceName']) ??
        'Organização',
    publishedAt: publishedAt,
    ticketStillValid: switch (json['ticketStillValid']) {
      final bool valid => valid,
      _ => null,
    },
    refundInfo: stringFromJson(json['refundInfo']),
    message: stringFromJson(json['message']),
    severity: severityFromCore(json['severity']),
  );
}

/// Core's classification of a change or an alert.
Severity severityFromCore(Object? severity) => switch (severity) {
  'CRITICAL' => Severity.critical,
  'WARNING' => Severity.warning,
  _ => Severity.information,
};

/// The app has no status of its own for an event in progress or over: it
/// reads the schedule for that.
EventStatus eventStatusFromCore(Object? status) => switch (status) {
  'POSTPONED' => EventStatus.postponed,
  'CANCELLED' => EventStatus.cancelled,
  'RELOCATED' => EventStatus.relocated,
  _ => EventStatus.scheduled,
};

DateTime? _instant(Object? value) =>
    value is String ? DateTime.tryParse(value)?.toLocal() : null;

/// A text as Core sends it, or null when it is missing or not a text.
String? stringFromJson(Object? value) => value is String ? value : null;

/// A time as Core sends it, or null when it is missing or not a time.
DateTime? instantFromJson(Object? value) => _instant(value);

/// Pictures for events from Core until the catalog has photos: the same
/// event always gets the same one.
const _arts = [
  EventArt(Color(0xFF2E4A8C), Color(0xFF0F1626)),
  EventArt(Color(0xFF8C4D2E), Color(0xFF26140F)),
  EventArt(Color(0xFF2E8C6B), Color(0xFF0F2620)),
  EventArt(Color(0xFF6B2E8C), Color(0xFF1C0F26)),
];

/// The picture of an event from Core.
EventArt eventArtFor(String id) =>
    _arts[id.codeUnits.fold(0, (a, b) => a + b) % _arts.length];

/// The event as the app shows it, from its page and its history (newest
/// first). Changes of a type this version does not know are left out.
Event eventFromCore(EventPage page, List<CoreChange> history) {
  final changes = [for (final c in history) ?changeFromCore(c)];
  return Event(
    id: page.id,
    name: page.name,
    venue: page.venueName,
    city: page.district,
    district: page.district,
    start: page.start,
    doorsOpen: page.doorsOpen,
    end: page.end,
    art: eventArtFor(page.id),
    status: page.status,
    organizer: page.organization,
    joinedWithAccessCode: page.isPublic,
    changes: changes,
    updatedAt: (page.latestChange ?? history.firstOrNull)?.publishedAt,
    venueStatus: page.venueStatus,
    alerts: page.alerts,
  );
}

/// An official change worded for the history (P6) and its page (P7).
EventChange? changeFromCore(CoreChange change) {
  final message = change.message?.trim();
  final said = message == null || message.isEmpty ? null : message;
  final previousStart = _instant(change.previousValue['startDateTime']);
  final newStart = _instant(change.newValue['startDateTime']);
  final doors = _instant(change.newValue['doorsOpenAt']);
  final (kind, title, description) = switch (change.type) {
    'POSTPONEMENT' => (
      ChangeKind.dateChanged,
      'Data alterada',
      said ?? 'Nova data publicada pela organização.',
    ),
    'TIME_CHANGE' when newStart != null => (
      ChangeKind.timeChanged,
      'Hora alterada',
      said ?? 'Nova hora publicada pela organização.',
    ),
    'TIME_CHANGE' => (
      ChangeKind.timeChanged,
      doors == null ? 'Hora alterada' : 'Portas às ${formatTime(doors)}',
      said ??
          (doors == null
              ? 'Novo horário publicado pela organização.'
              : 'Abertura de portas às ${formatTime(doors)}.'),
    ),
    'VENUE_CHANGE' => (
      ChangeKind.venueChanged,
      'Local alterado',
      said ??
          switch (change.newValue['venueName']) {
            final String venue => 'Agora em $venue.',
            _ => 'Novo local publicado pela organização.',
          },
    ),
    'CANCELLATION' => (
      ChangeKind.cancelled,
      'Evento cancelado',
      said ??
          (change.ticketStillValid == false
              ? 'O bilhete deixa de ser válido.'
              : 'Cancelado pela organização.'),
    ),
    _ => (null, '', ''),
  };
  if (kind == null) return null;
  final refund = change.refundInfo?.trim();
  return EventChange(
    id: change.id,
    kind: kind,
    title: title,
    description: description,
    detectedAt: change.publishedAt,
    publisher: change.publisher,
    previousStart: previousStart,
    newStart: newStart,
    ticketStillValid: change.ticketStillValid ?? true,
    refund: refund == null || refund.isEmpty ? null : Refund(where: refund),
    severity: change.severity,
  );
}
