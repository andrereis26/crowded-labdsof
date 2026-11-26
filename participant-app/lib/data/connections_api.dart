import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'auth_api.dart';
import 'events_api.dart';
import 'models.dart';

/// The public event a participant connected to with its access code
/// (`POST /connections`, API Outline "Connect to an event").
class JoinedEvent {
  const JoinedEvent({required this.connectionId, required this.eventId});

  final String connectionId;
  final String eventId;
}

/// The participant's events as Core lists them, and when it read them.
class MyConnections {
  const MyConnections({required this.events, required this.syncedAt});

  /// Those that have not ended first, then the ones that ended: Core's order.
  final List<ConnectedEvent> events;
  final DateTime syncedAt;
}

/// A refusal from Core, with its error code: `ACCESS_CODE_NOT_FOUND` when
/// the code leads to no public event, `UNAUTHORIZED` when the session ended,
/// or `NETWORK` when Core could not be reached or did not answer in time.
class ConnectionsApiException implements Exception {
  const ConnectionsApiException(this.code);

  final String code;

  bool get notFound => code == 'ACCESS_CODE_NOT_FOUND';

  bool get sessionEnded => code == 'UNAUTHORIZED';

  @override
  String toString() => 'ConnectionsApiException($code)';
}

/// The participant's connections to events in Core.
abstract interface class ConnectionsApi {
  /// Connects the signed-in participant to the public event of [accessCode],
  /// typed or read from its QR code. Core decides the event from the code.
  Future<JoinedEvent> joinWithAccessCode(String accessCode);

  /// The signed-in participant's events (`GET /me/connections`), with each
  /// one's latest change and active alerts.
  Future<MyConnections> myConnections();
}

/// [ConnectionsApi] over HTTP, with the session's client ([AuthApi.client]), so
/// Core decides who connects.
class HttpConnectionsApi implements ConnectionsApi {
  HttpConnectionsApi({
    http.Client? client,
    String baseUrl = apiBaseUrl,
    this._timeout = const Duration(seconds: 15),
  }) : _client = client ?? http.Client(),
       _base = Uri.base.resolve(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');

  final http.Client _client;
  final Uri _base;

  /// How long to wait for Core, so a request that never answers does not
  /// leave the screen waiting for good.
  final Duration _timeout;

  @override
  Future<JoinedEvent> joinWithAccessCode(String accessCode) async {
    final http.Response response;
    try {
      response = await _client
          .post(
            _base.resolve('connections'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'accessCode': accessCode}),
          )
          .timeout(_timeout);
    } on Exception {
      throw const ConnectionsApiException('NETWORK');
    }
    if (response.statusCode == 401) {
      throw const ConnectionsApiException('UNAUTHORIZED');
    }
    final body = _json(response);
    final event = body['event'];
    if ((response.statusCode == 200 || response.statusCode == 201) &&
        body['connectionId'] is String &&
        event is Map<String, dynamic> &&
        event['id'] is String) {
      return JoinedEvent(
        connectionId: body['connectionId'] as String,
        eventId: event['id'] as String,
      );
    }
    final code = body['code'];
    throw ConnectionsApiException(
      code is String ? code : 'HTTP_${response.statusCode}',
    );
  }

  @override
  Future<MyConnections> myConnections() async {
    final http.Response response;
    try {
      response = await _client
          .get(_base.resolve('me/connections'))
          .timeout(_timeout);
    } on Exception {
      throw const ConnectionsApiException('NETWORK');
    }
    if (response.statusCode == 401) {
      throw const ConnectionsApiException('UNAUTHORIZED');
    }
    final body = _json(response);
    final list = myConnectionsFromJson(body);
    if (response.statusCode == 200 && list != null) return list;
    final code = body['code'];
    throw ConnectionsApiException(
      code is String ? code : 'HTTP_${response.statusCode}',
    );
  }

  static Map<String, dynamic> _json(http.Response response) {
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      return decoded is Map<String, dynamic> ? decoded : const {};
    } on FormatException {
      return const {};
    }
  }
}

/// The list Core sent, or null when it is not one. A connection it cannot
/// read is left out rather than failing the whole list.
MyConnections? myConnectionsFromJson(Map<String, dynamic> json) {
  final connections = json['connections'];
  final syncedAt = instantFromJson(json['syncedAt']);
  if (connections is! List || syncedAt == null) return null;
  return MyConnections(
    events: [
      for (final item in connections.whereType<Map<String, dynamic>>())
        ?_connectedEventFromJson(item),
    ],
    syncedAt: syncedAt,
  );
}

ConnectedEvent? _connectedEventFromJson(Map<String, dynamic> json) {
  final connectionId = json['connectionId'];
  final shown = json['event'];
  if (connectionId is! String || shown is! Map<String, dynamic>) return null;
  final id = shown['id'];
  final name = shown['name'];
  final start = instantFromJson(shown['startDateTime']);
  if (id is! String || name is! String || start == null) return null;
  final latest = shown['latestChange'];
  final change = latest is Map<String, dynamic>
      ? coreChangeFromJson(latest)
      : null;
  final converted = change == null ? null : changeFromCore(change);
  final alerts = shown['activeAlerts'];
  final event = Event(
    id: id,
    name: name,
    venue: stringFromJson(shown['venueName']) ?? '',
    city: '',
    start: start,
    doorsOpen: instantFromJson(shown['doorsOpenAt']),
    art: eventArtFor(id),
    status: eventStatusFromCore(shown['status']),
    joinedWithAccessCode: json['type'] == 'ACCESS_CODE',
    changes: [?converted],
    alerts: [
      if (alerts is List)
        for (final item in alerts.whereType<Map<String, dynamic>>())
          ?eventAlertFromJson(item),
    ],
    updatedAt: change?.publishedAt,
    provenance: change?.source,
    imageUpdatedAt: instantFromJson(shown['imageUpdatedAt']),
  );
  final ticket = json['ticket'];
  return ConnectedEvent(
    event: event,
    ticket: ticket is Map<String, dynamic>
        ? Ticket(
            id: connectionId,
            event: event,
            gate: stringFromJson(ticket['gate']),
            sector: stringFromJson(ticket['sector']),
            seat: stringFromJson(ticket['seat']),
            // The code itself is not read from Core yet, so the entry code
            // screen has nothing to show.
            hasEntryCode: false,
          )
        : null,
  );
}
