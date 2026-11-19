import 'dart:async';
import 'dart:convert';

import 'package:crowded/data/connections_api.dart';
import 'package:crowded/data/models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  const event = '30000000-0000-4000-8000-000000000002';
  const joined =
      '{"connectionId":"c-1","event":{"id":"$event","name":"Arraial",'
      '"startDateTime":"2026-06-23T20:00:00Z","doorsOpenAt":null,'
      '"venueName":"Fontainhas"}}';

  HttpConnectionsApi api(MockClientHandler handler) => HttpConnectionsApi(
    token: () => 'token-123',
    client: MockClient(handler),
    baseUrl: 'https://crowded.test/api',
  );

  Matcher refusedWith(String code) => throwsA(
    isA<ConnectionsApiException>().having((e) => e.code, 'code', code),
  );

  test('sends only the code, as the signed-in participant', () async {
    late http.Request sent;
    await api((request) async {
      sent = request;
      return http.Response(joined, 201);
    }).joinWithAccessCode('SAOJOAO26');

    expect(sent.method, 'POST');
    expect(sent.url.toString(), 'https://crowded.test/api/connections');
    expect(sent.headers['Authorization'], 'Bearer token-123');
    expect(sent.headers['Content-Type'], startsWith('application/json'));
    expect(jsonDecode(sent.body), {'accessCode': 'SAOJOAO26'});
  });

  test('returns the event of a new or an existing connection', () async {
    for (final status in [201, 200]) {
      final result = await api((_) async => http.Response(joined, status))
          .joinWithAccessCode('SAOJOAO26');

      expect(result.connectionId, 'c-1');
      expect(result.eventId, event);
    }
  });

  test('reports a code that leads to no public event', () async {
    final call = api(
      (_) async => http.Response('{"code":"ACCESS_CODE_NOT_FOUND"}', 404),
    ).joinWithAccessCode('NOPE');

    await expectLater(call, refusedWith('ACCESS_CODE_NOT_FOUND'));
    await expectLater(
      call,
      throwsA(
        isA<ConnectionsApiException>().having((e) => e.notFound, '', true),
      ),
    );
  });

  test('reports other refusals and unreadable answers by status', () async {
    await expectLater(
      api((_) async => http.Response('{"code":"PARTICIPANT_ONLY"}', 403))
          .joinWithAccessCode('SAOJOAO26'),
      refusedWith('PARTICIPANT_ONLY'),
    );
    await expectLater(
      api((_) async => http.Response('<html>Bad gateway</html>', 502))
          .joinWithAccessCode('SAOJOAO26'),
      refusedWith('HTTP_502'),
    );
    await expectLater(
      api((_) async => http.Response('[]', 201)).joinWithAccessCode('X'),
      refusedWith('HTTP_201'),
    );
  });

  test('reports an ended session', () async {
    final call = api((_) async => http.Response('', 401))
        .joinWithAccessCode('SAOJOAO26');

    await expectLater(call, refusedWith('UNAUTHORIZED'));
    await expectLater(
      call,
      throwsA(
        isA<ConnectionsApiException>().having(
          (e) => e.sessionEnded,
          'sessionEnded',
          true,
        ),
      ),
    );
  });

  test('stops waiting for a Core that does not answer', () async {
    final silent = HttpConnectionsApi(
      token: () => 'token-123',
      client: MockClient((_) => Completer<http.Response>().future),
      baseUrl: 'https://crowded.test/api',
      timeout: const Duration(milliseconds: 10),
    );

    await expectLater(
      silent.joinWithAccessCode('SAOJOAO26'),
      refusedWith('NETWORK'),
    );
  });

  test('reports that Core cannot be reached', () async {
    await expectLater(
      api((_) async => throw http.ClientException('offline'))
          .joinWithAccessCode('SAOJOAO26'),
      refusedWith('NETWORK'),
    );
    expect(
      const ConnectionsApiException('NETWORK').toString(),
      'ConnectionsApiException(NETWORK)',
    );
  });

  group('my connections', () {
    const list =
        '{"connections":['
        '{"connectionId":"t-1","type":"TICKET","event":{"id":"$event",'
        '"name":"Festival Margens","status":"CANCELLED",'
        '"startDateTime":"2026-11-27T22:00:00Z",'
        '"doorsOpenAt":"2026-11-27T20:00:00Z","venueName":"Cais",'
        '"latestChange":{"id":"ch-1","type":"CANCELLATION",'
        '"severity":"CRITICAL","previousValue":{},"newValue":{},'
        '"ticketStillValid":false,"refundInfo":null,'
        '"message":"Cancelado.","provenance":{"sourceType":"ORGANIZATION",'
        '"sourceName":"Margens","lastUpdatedAt":"2026-11-20T10:00:00Z",'
        '"confirmed":true}},'
        '"activeAlerts":[{"id":"a-1","type":"ENTRANCE_CLOSURE",'
        '"severity":"WARNING","message":"Entrada Sul fechada.",'
        '"sentAt":"2026-11-27T21:00:00Z"}]},'
        '"ticket":{"gate":"A","sector":"2","seat":"14","hasEntryCode":true}},'
        '{"connectionId":"c-2","type":"ACCESS_CODE","event":{"id":"e-2",'
        '"name":"Arraial","status":"SCHEDULED",'
        '"startDateTime":"2026-12-01T20:00:00Z","doorsOpenAt":null,'
        '"venueName":"Fontainhas","latestChange":null,"activeAlerts":[]},'
        '"ticket":null}],"syncedAt":"2026-11-18T10:00:00Z"}';

    test('asks for the events of the signed-in participant', () async {
      late http.Request sent;
      await api((request) async {
        sent = request;
        return http.Response(list, 200);
      }).myConnections();

      expect(sent.method, 'GET');
      expect(sent.url.toString(), 'https://crowded.test/api/me/connections');
      expect(sent.headers['Authorization'], 'Bearer token-123');
    });

    test('reads each event, its change, alerts and ticket, in order', () async {
      final result = await api((_) async => http.Response(list, 200))
          .myConnections();

      expect(result.syncedAt.toUtc(), DateTime.utc(2026, 11, 18, 10));
      expect(result.events.map((e) => e.event.name), [
        'Festival Margens',
        'Arraial',
      ]);
      final ticketed = result.events[0];
      expect(ticketed.event.status, EventStatus.cancelled);
      expect(ticketed.event.doorsOpen?.toUtc(), DateTime.utc(2026, 11, 27, 20));
      expect(ticketed.event.joinedWithAccessCode, isFalse);
      expect(ticketed.event.changes.single.kind, ChangeKind.cancelled);
      expect(ticketed.event.changes.single.severity, Severity.critical);
      expect(ticketed.event.alerts.single.severity, Severity.warning);
      expect(ticketed.event.alerts.single.message, 'Entrada Sul fechada.');
      expect(ticketed.ticket?.id, 't-1');
      expect(ticketed.ticket?.gate, 'A');
      expect(ticketed.ticket?.sector, '2');
      expect(ticketed.ticket?.seat, '14');
      expect(ticketed.ticket?.hasEntryCode, isTrue);
      final byCode = result.events[1];
      expect(byCode.ticket, isNull);
      expect(byCode.event.joinedWithAccessCode, isTrue);
      expect(byCode.event.doorsOpen, isNull);
      expect(byCode.event.changes, isEmpty);
      expect(byCode.event.alerts, isEmpty);
    });

    test('an empty list is a participant without events', () async {
      final result = await api(
        (_) async => http.Response(
          '{"connections":[],"syncedAt":"2026-11-18T10:00:00Z"}',
          200,
        ),
      ).myConnections();

      expect(result.events, isEmpty);
    });

    test('leaves out a connection it cannot read', () async {
      final result = await api(
        (_) async => http.Response(
          '{"connections":[{"connectionId":"x"},'
          '{"connectionId":"c-2","type":"ACCESS_CODE","event":{"id":"e-2",'
          '"name":"Arraial","status":"SCHEDULED",'
          '"startDateTime":"2026-12-01T20:00:00Z","venueName":"Fontainhas"}}],'
          '"syncedAt":"2026-11-18T10:00:00Z"}',
          200,
        ),
      ).myConnections();

      expect(result.events.map((e) => e.event.id), ['e-2']);
    });

    test(
      'reports an ended session, no network and unreadable answers',
      () async {
        await expectLater(
          api((_) async => http.Response('', 401)).myConnections(),
          refusedWith('UNAUTHORIZED'),
        );
        await expectLater(
          api((_) async => throw http.ClientException('offline'))
              .myConnections(),
          refusedWith('NETWORK'),
        );
        await expectLater(
          api((_) async => http.Response('<html>Bad</html>', 502))
              .myConnections(),
          refusedWith('HTTP_502'),
        );
        await expectLater(
          api((_) async => http.Response('{"code":"BOOM"}', 500))
              .myConnections(),
          refusedWith('BOOM'),
        );
        await expectLater(
          api((_) async => http.Response('{}', 200)).myConnections(),
          refusedWith('HTTP_200'),
        );
      },
    );
  });
}
