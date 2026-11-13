import 'dart:async';
import 'dart:convert';

import 'package:crowded/data/models.dart';
import 'package:crowded/data/notifications_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

Map<String, Object?> _json({
  String id = 'n-1',
  String type = 'OFFICIAL_CHANGE',
  String? causeId = 'c-1',
  String? eventName = 'Noite de Fado',
  String? readAt,
}) => {
  'id': id,
  'type': type,
  'title': 'Adiado',
  'body': 'Nova data.\nTraz o bilhete.',
  'eventId': 'e-1',
  'eventName': eventName,
  'causeId': causeId,
  'createdAt': '2026-11-12T10:00:00Z',
  'readAt': readAt,
};

void main() {
  group('HttpNotificationsApi', () {
    test('lists the notifications with the token', () async {
      late http.Request sent;
      final api = HttpNotificationsApi(
        baseUrl: 'https://crowded.test/api',
        client: () => MockClient((request) async {
          sent = request;
          return http.Response(
            jsonEncode({
              'notifications': [
                _json(),
                _json(id: 'n-2', type: 'NEW_TYPE'),
                _json(id: 'n-3', type: 'RECOGNITION', causeId: null),
              ],
              'unreadCount': 2,
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final page = await api.list('jwt');

      expect(sent.method, 'GET');
      expect(sent.url.toString(), 'https://crowded.test/api/notifications');
      expect(sent.headers['Authorization'], 'Bearer jwt');
      expect(page.unread, 2);
      expect(page.notifications.map((n) => n.id), [
        'n-1',
        'n-3',
      ], reason: 'a type the app does not know is left out');
      final change = page.notifications.first;
      expect(change.kind, NotificationKind.officialChange);
      expect(change.eventName, 'Noite de Fado');
      expect(change.body, 'Nova data.\nTraz o bilhete.');
      expect(change.receivedAt, DateTime.utc(2026, 11, 12, 10).toLocal());
      expect(change.target, '/event/e-1/change/c-1');
      expect(change.read, isFalse);
      expect(page.notifications.last.target, '/event/e-1');
    });

    test('marks a notification read', () async {
      late http.Request sent;
      final api = HttpNotificationsApi(
        baseUrl: 'https://crowded.test/api/',
        client: () => MockClient((request) async {
          sent = request;
          return http.Response('', 204);
        }),
      );

      await api.markRead('jwt', 'n-1');

      expect(sent.method, 'POST');
      expect(
        sent.url.toString(),
        'https://crowded.test/api/notifications/n-1/read',
      );
      expect(sent.headers['Authorization'], 'Bearer jwt');
    });

    test('reports refusals with their code', () async {
      Future<String> codeOf(http.Response response) async {
        final api = HttpNotificationsApi(
          client: () => MockClient((request) async => response),
        );
        try {
          await api.markRead('jwt', 'n-1');
        } on NotificationsException catch (e) {
          return e.code;
        }
        return 'none';
      }

      expect(
        await codeOf(
          http.Response(jsonEncode({'code': 'NOTIFICATION_NOT_FOUND'}), 404),
        ),
        'NOTIFICATION_NOT_FOUND',
      );
      expect(await codeOf(http.Response('', 401)), 'UNAUTHORIZED');
      expect(await codeOf(http.Response('<html>', 502)), 'HTTP_502');
    });

    test('reports a network failure and an invalid list', () async {
      final offline = HttpNotificationsApi(
        client: () => MockClient((request) => throw http.ClientException('x')),
      );
      final invalid = HttpNotificationsApi(
        client: () => MockClient((request) async => http.Response('[', 200)),
      );
      final notAnObject = HttpNotificationsApi(
        client: () => MockClient((request) async => http.Response('[]', 200)),
      );

      expect(
        offline.list('jwt'),
        throwsA(
          isA<NotificationsException>().having(
            (e) => e.code,
            'code',
            'NETWORK',
          ),
        ),
      );
      expect(
        invalid.list('jwt'),
        throwsA(
          isA<NotificationsException>().having(
            (e) => e.code,
            'code',
            'INVALID_RESPONSE',
          ),
        ),
      );
      expect(notAnObject.list('jwt'), throwsA(isA<NotificationsException>()));
    });

    test('streams the opening and each new notification', () async {
      final body = StreamController<List<int>>();
      late http.BaseRequest sent;
      final api = HttpNotificationsApi(
        baseUrl: 'https://crowded.test/api',
        client: () => MockClient.streaming((request, bodyStream) async {
          sent = request;
          return http.StreamedResponse(body.stream, 200);
        }),
      );
      final events = <LiveEvent>[];
      final done = Completer<void>();

      api.live('jwt').listen(events.add, onDone: done.complete);
      body.add(utf8.encode('retry:5000\n:open\n\n'));
      body.add(utf8.encode(':keep-alive\n\n'));
      body.add(
        utf8.encode(
          'id:n-1\nevent:notification\ndata:'
          '${jsonEncode({'notification': _json(), 'unreadCount': 3})}\n\n',
        ),
      );
      body.add(utf8.encode('event:notification\ndata:{not json\n\n'));
      body.add(utf8.encode('event:other\ndata:{}\n\n'));
      await body.close();
      await done.future;

      expect(
        sent.url.toString(),
        'https://crowded.test/api/notifications/stream',
      );
      expect(sent.headers['Authorization'], 'Bearer jwt');
      expect(sent.headers['Accept'], 'text/event-stream');
      expect(events, hasLength(2));
      expect(events.first, isA<LiveOpened>());
      expect((events.last as LiveNotificationEvent).notification.id, 'n-1');
    });

    test('fails the stream when it cannot be opened or drops', () async {
      final refused = HttpNotificationsApi(
        client: () => MockClient((request) async => http.Response('', 401)),
      );
      await expectLater(
        refused.live('jwt'),
        emitsError(
          isA<NotificationsException>().having(
            (e) => e.code,
            'code',
            'UNAUTHORIZED',
          ),
        ),
      );

      final body = StreamController<List<int>>();
      final dropped = HttpNotificationsApi(
        client: () => MockClient.streaming(
          (request, bodyStream) async =>
              http.StreamedResponse(body.stream, 200),
        ),
      );
      final result = expectLater(
        dropped.live('jwt'),
        emitsInOrder([
          isA<LiveOpened>(),
          emitsError(isA<NotificationsException>()),
        ]),
      );
      body.add(utf8.encode(':open\n\n'));
      body.addError(http.ClientException('connection reset'));
      await result;
    });

    test('closes the connection when the stream is cancelled', () async {
      var cancelled = false;
      final body = StreamController<List<int>>(
        onCancel: () => cancelled = true,
      );
      final api = HttpNotificationsApi(
        client: () => MockClient.streaming(
          (request, bodyStream) async =>
              http.StreamedResponse(body.stream, 200),
        ),
      );
      final opened = Completer<void>();
      final subscription = api.live('jwt').listen((_) => opened.complete());
      body.add(utf8.encode(':open\n\n'));
      await opened.future;

      await subscription.cancel();

      expect(cancelled, isTrue);
    });
  });

  group('notificationFromJson', () {
    test('maps every type to its kind, label and screen', () {
      AppNotification read(String type, {String? causeId = 'c-1'}) =>
          notificationFromJson(_json(type: type, causeId: causeId))!;

      expect(read('OPERATIONAL_ALERT').kind, NotificationKind.alert);
      expect(read('OPERATIONAL_ALERT').typeLabel, 'Alerta da organização');
      expect(read('OPERATIONAL_ALERT').target, '/event/e-1');
      expect(read('WEATHER_WARNING').kind, NotificationKind.weather);
      expect(read('ORGANIZATION_SURVEY').target, '/event/e-1/requests');
      expect(read('CONFIRMATION_REQUEST').target, '/event/e-1?confirm=c-1');
      expect(read('RECOGNITION').target, '/recognition/c-1');
      expect(read('INCIDENT_STATUS').target, '/account/contributions');
      expect(read('OFFICIAL_CHANGE', causeId: null).target, '/event/e-1');
    });

    test('names a deleted event and keeps the read state', () {
      final n = notificationFromJson(
        _json(eventName: null, readAt: '2026-11-12T10:05:00Z'),
      )!;

      expect(n.eventName, 'Evento');
      expect(n.read, isTrue);
    });

    test('skips a notification without its essentials', () {
      expect(notificationFromJson({..._json(), 'id': null}), isNull);
      expect(notificationFromJson({..._json(), 'createdAt': 'x'}), isNull);
    });
  });

  test('ServerSentEvents joins data lines and names unnamed events', () {
    final parser = ServerSentEvents();

    expect(parser.add('data: first'), isNull);
    expect(parser.add('data:second'), isNull);
    expect(parser.add('ignored'), isNull);
    final event = parser.add('')!;

    expect(event.name, 'message');
    expect(event.data, 'first\nsecond');
    expect(parser.add(''), isNull, reason: 'a blank line alone is no event');
  });
}
