import 'dart:async';
import 'dart:convert';

import 'package:crowded/data/connections_api.dart';
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
}
