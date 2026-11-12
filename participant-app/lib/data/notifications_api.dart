import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'auth_api.dart';
import 'models.dart';

/// The notification center as Notifications sends it: newest first, and how
/// many are unread.
class NotificationPage {
  const NotificationPage({required this.notifications, required this.unread});

  final List<AppNotification> notifications;
  final int unread;
}

/// What the live stream sends.
sealed class LiveEvent {
  const LiveEvent();
}

/// The stream is open: anything created before now is in `GET /notifications`.
class LiveOpened extends LiveEvent {
  const LiveOpened();
}

/// A new notification, for the banner and the badge.
class LiveNotificationEvent extends LiveEvent {
  const LiveNotificationEvent(this.notification);

  final AppNotification notification;
}

/// A refusal from Notifications, with its error code, `UNAUTHORIZED` when the
/// token is no longer accepted, or `NETWORK` when it could not be reached.
class NotificationsException implements Exception {
  const NotificationsException(this.code);

  final String code;

  @override
  String toString() => 'NotificationsException($code)';
}

/// The participant's notifications in Notifications (API Outline,
/// Notification center).
abstract interface class NotificationsApi {
  Future<NotificationPage> list(String token);

  Future<void> markRead(String token, String notificationId);

  /// `GET /notifications/stream`: one connection, which ends when the server
  /// closes it or the subscription is cancelled. Fails with a
  /// [NotificationsException] when it cannot be opened.
  Stream<LiveEvent> live(String token);
}

/// [NotificationsApi] over HTTP. On the web, `package:http` reads the stream
/// with `fetch`, so the token goes in the `Authorization` header as for every
/// other request.
class HttpNotificationsApi implements NotificationsApi {
  HttpNotificationsApi({
    http.Client Function()? client,
    String baseUrl = apiBaseUrl,
  }) : _client = client ?? http.Client.new,
       _base = Uri.base.resolve(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');

  final http.Client Function() _client;
  final Uri _base;

  @override
  Future<NotificationPage> list(String token) async {
    final client = _client();
    try {
      final response = await _send(
        client,
        http.Request('GET', _base.resolve('notifications')),
        token,
      );
      final body = jsonDecode(await response.stream.bytesToString());
      if (body is! Map<String, dynamic>) {
        throw const NotificationsException('INVALID_RESPONSE');
      }
      final items = body['notifications'];
      return NotificationPage(
        notifications: [
          if (items is List)
            for (final item in items.whereType<Map<String, dynamic>>())
              ?notificationFromJson(item),
        ],
        unread: (body['unreadCount'] as num?)?.toInt() ?? 0,
      );
    } on FormatException {
      throw const NotificationsException('INVALID_RESPONSE');
    } finally {
      client.close();
    }
  }

  @override
  Future<void> markRead(String token, String notificationId) async {
    final client = _client();
    try {
      final response = await _send(
        client,
        http.Request(
          'POST',
          _base.resolve('notifications/$notificationId/read'),
        ),
        token,
      );
      await response.stream.drain<void>();
    } finally {
      client.close();
    }
  }

  @override
  Stream<LiveEvent> live(String token) {
    final client = _client();
    late final StreamController<LiveEvent> controller;
    StreamSubscription<String>? lines;
    controller = StreamController<LiveEvent>(
      onListen: () async {
        try {
          final request = http.Request(
            'GET',
            _base.resolve('notifications/stream'),
          )..headers['Accept'] = 'text/event-stream';
          final response = await _send(client, request, token);
          if (controller.isClosed) return;
          final parser = ServerSentEvents();
          lines = response.stream
              .transform(utf8.decoder)
              .transform(const LineSplitter())
              .listen(
                (line) {
                  final event = parser.add(line);
                  if (event != null) _emit(controller, event);
                },
                onError: (Object error) {
                  controller.addError(const NotificationsException('NETWORK'));
                  controller.close();
                },
                onDone: controller.close,
                cancelOnError: true,
              );
        } on NotificationsException catch (e) {
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

  /// Sends one event of the stream: `open` once the connection is up, and
  /// each `notification`.
  static void _emit(
    StreamController<LiveEvent> controller,
    ServerSentEvent event,
  ) {
    if (event.name == 'open') {
      controller.add(const LiveOpened());
      return;
    }
    if (event.name != 'notification') return;
    try {
      final data = jsonDecode(event.data);
      final item = data is Map<String, dynamic> ? data['notification'] : null;
      final notification = item is Map<String, dynamic>
          ? notificationFromJson(item)
          : null;
      if (notification != null) {
        controller.add(LiveNotificationEvent(notification));
      }
    } on FormatException {
      // A malformed event is skipped; the center still lists it.
    }
  }

  Future<http.StreamedResponse> _send(
    http.Client client,
    http.Request request,
    String token,
  ) async {
    request.headers['Authorization'] = 'Bearer $token';
    final http.StreamedResponse response;
    try {
      response = await client.send(request);
    } on Exception {
      throw const NotificationsException('NETWORK');
    }
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return response;
    }
    final text = await response.stream.bytesToString();
    if (response.statusCode == 401) {
      throw const NotificationsException('UNAUTHORIZED');
    }
    String? code;
    try {
      final body = jsonDecode(text);
      if (body is Map<String, dynamic> && body['code'] is String) {
        code = body['code'] as String;
      }
    } on FormatException {
      // No problem details: the status says enough.
    }
    throw NotificationsException(code ?? 'HTTP_${response.statusCode}');
  }
}

/// One server-sent event: its name (`message` when unnamed) and data.
class ServerSentEvent {
  const ServerSentEvent(this.name, this.data);

  final String name;
  final String data;
}

/// Reads server-sent events line by line (HTML Living Standard, "Server-sent
/// events"). Notifications starts every stream with the comment `:open`,
/// read as an `open` event; other comments keep the connection alive.
class ServerSentEvents {
  String? _name;
  final _data = <String>[];

  /// Adds one line; returns the event it completes, if any.
  ServerSentEvent? add(String line) {
    if (line.isEmpty) {
      final name = _name ?? 'message';
      final data = _data.join('\n');
      final hadEvent = _name != null || _data.isNotEmpty;
      _name = null;
      _data.clear();
      return hadEvent ? ServerSentEvent(name, data) : null;
    }
    if (line.startsWith(':')) {
      return line == ':open' ? const ServerSentEvent('open', '') : null;
    }
    final colon = line.indexOf(':');
    final field = colon < 0 ? line : line.substring(0, colon);
    var value = colon < 0 ? '' : line.substring(colon + 1);
    if (value.startsWith(' ')) value = value.substring(1);
    switch (field) {
      case 'event':
        _name = value;
      case 'data':
        _data.add(value);
    }
    return null;
  }
}

/// A notification from Notifications, or null for a type this version of the
/// app does not know.
AppNotification? notificationFromJson(Map<String, dynamic> json) {
  final kind = switch (json['type']) {
    'OFFICIAL_CHANGE' => NotificationKind.officialChange,
    'OPERATIONAL_ALERT' => NotificationKind.alert,
    'WEATHER_WARNING' => NotificationKind.weather,
    'ORGANIZATION_SURVEY' => NotificationKind.request,
    'CONFIRMATION_REQUEST' => NotificationKind.confirmation,
    'RECOGNITION' => NotificationKind.recognition,
    'INCIDENT_STATUS' => NotificationKind.reportStatus,
    _ => null,
  };
  final id = json['id'];
  final eventId = json['eventId'];
  final createdAt = DateTime.tryParse(json['createdAt'] as String? ?? '');
  if (kind == null ||
      id is! String ||
      eventId is! String ||
      createdAt == null) {
    return null;
  }
  final causeId = json['causeId'] as String?;
  return AppNotification(
    id: id,
    kind: kind,
    eventId: eventId,
    eventName: json['eventName'] as String? ?? 'Evento',
    typeLabel: kind.typeLabel,
    title: json['title'] as String? ?? '',
    body: json['body'] as String?,
    receivedAt: createdAt.toLocal(),
    changeId: kind == NotificationKind.officialChange ? causeId : null,
    route: switch (kind) {
      NotificationKind.confirmation when causeId != null =>
        '/evento/$eventId?confirmar=$causeId',
      NotificationKind.request => '/evento/$eventId/pedidos',
      NotificationKind.recognition when causeId != null =>
        '/reconhecimento/$causeId',
      NotificationKind.reportStatus => '/conta/contribuicoes',
      _ => null,
    },
    read: json['readAt'] != null,
  );
}
