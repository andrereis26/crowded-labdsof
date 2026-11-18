import 'dart:convert';
import 'dart:typed_data';

import 'package:crowded/data/tickets_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  final pdf = Uint8List.fromList(utf8.encode('%PDF-1.7 ticket'));

  HttpTicketsApi api(MockClientHandler handler) => HttpTicketsApi(
    token: () => 'token-123',
    client: MockClient(handler),
    baseUrl: 'https://crowded.test/api',
  );

  Matcher refusedWith(String code) =>
      throwsA(isA<TicketsApiException>().having((e) => e.code, 'code', code));

  test('uploads the PDF as the signed-in participant', () async {
    late http.Request sent;
    await api((request) async {
      sent = request;
      return http.Response(
        '{"status":"NEEDS_EVENT_CODE","importId":"i-1","ticket":{}}',
        200,
      );
    }).uploadPdf(pdf, 'bilhete.pdf');

    expect(sent.method, 'POST');
    expect(sent.url.toString(), 'https://crowded.test/api/tickets');
    expect(sent.headers['Authorization'], 'Bearer token-123');
    expect(sent.headers['content-type'], startsWith('multipart/form-data'));
    expect(sent.body, contains('name="file"; filename="bilhete.pdf"'));
    expect(sent.body, contains('%PDF-1.7 ticket'));
  });

  test('returns the pending import with the ticket details', () async {
    final result = await api(
      (_) async => http.Response(
        '{"status":"NEEDS_EVENT_CODE","importId":"i-1",'
        '"ticket":{"gate":"A","sector":null,"seat":"14"}}',
        200,
      ),
    ).uploadPdf(pdf, 'bilhete.pdf');

    expect(result, isA<TicketNeedsEventCode>());
    final pending = result as TicketNeedsEventCode;
    expect(pending.importId, 'i-1');
    expect(pending.gate, 'A');
    expect(pending.sector, isNull);
    expect(pending.seat, '14');
  });

  test('returns the connection and the registered event', () async {
    final result = await api(
      (_) async => http.Response(
        '{"status":"CONNECTED","ticketId":"t-1","connectionId":"t-1",'
        '"event":{"id":"e-1","name":"Festival Margens",'
        '"startDateTime":"2026-11-27T22:00:00Z",'
        '"doorsOpenAt":"2026-11-27T20:00:00Z","venueName":"Cais da Ribeira"},'
        '"ticket":{"gate":"A","sector":null,"seat":"14"}}',
        200,
      ),
    ).uploadPdf(pdf, 'bilhete.pdf');

    expect(result, isA<TicketConnected>());
    final connected = result as TicketConnected;
    expect(connected.connectionId, 't-1');
    expect(connected.ticketId, 't-1');
    expect(connected.eventId, 'e-1');
    expect(connected.eventName, 'Festival Margens');
    expect(connected.start.toUtc(), DateTime.utc(2026, 11, 27, 22));
    expect(connected.doorsOpen?.toUtc(), DateTime.utc(2026, 11, 27, 20));
    expect(connected.venueName, 'Cais da Ribeira');
    expect(connected.gate, 'A');
    expect(connected.sector, isNull);
    expect(connected.seat, '14');
  });

  test('connects without a ticket when the event has none of theirs', () async {
    final result = await api(
      (_) async => http.Response(
        '{"status":"CONNECTED","ticketId":null,"connectionId":"c-1",'
        '"event":{"id":"e-1","name":"Festival","venueName":"Cais",'
        '"startDateTime":"2026-11-27T22:00:00Z","doorsOpenAt":null},'
        '"ticket":{}}',
        200,
      ),
    ).uploadPdf(pdf, 'bilhete.pdf');

    final connected = result as TicketConnected;
    expect(connected.ticketId, isNull);
    expect(connected.doorsOpen, isNull);
  });

  test('refuses an answer without the event as an unexpected one', () async {
    await expectLater(
      api(
        (_) async => http.Response(
          '{"status":"CONNECTED","connectionId":"c-1","ticket":{}}',
          200,
        ),
      ).uploadPdf(pdf, 'bilhete.pdf'),
      refusedWith('HTTP_200'),
    );
  });

  test('reports a ticket that another account has', () async {
    final upload = api(
      (_) async => http.Response('{"code":"TICKET_ALREADY_IMPORTED"}', 409),
    ).uploadPdf(pdf, 'bilhete.pdf');

    await expectLater(upload, refusedWith('TICKET_ALREADY_IMPORTED'));
    expect(
      const TicketsApiException('TICKET_ALREADY_IMPORTED').alreadyImported,
      isTrue,
    );
    expect(const TicketsApiException('RATE_LIMITED').alreadyImported, isFalse);
  });

  test('reports a ticket without a QR code', () async {
    final result = await api(
      (_) async => http.Response('{"code":"QR_CODE_NOT_FOUND"}', 400),
    ).uploadPdf(pdf, 'bilhete.pdf');

    expect(result, isA<TicketWithoutQrCode>());
  });

  test('reports too many imports this hour', () async {
    final upload = api(
      (_) async => http.Response('{"code":"RATE_LIMITED"}', 429),
    ).uploadPdf(pdf, 'bilhete.pdf');

    await expectLater(upload, refusedWith('RATE_LIMITED'));
    expect(const TicketsApiException('RATE_LIMITED').tooManyRequests, isTrue);
    expect(const TicketsApiException('NETWORK').tooManyRequests, isFalse);
  });

  const connected =
      '{"status":"CONNECTED","ticketId":"t-1","connectionId":"t-1",'
      '"event":{"id":"e-1","name":"Festival Margens",'
      '"startDateTime":"2026-11-27T22:00:00Z","venueName":"Cais"},'
      '"ticket":{"gate":"A","seat":"14"}}';

  test('completes the pending import with the typed code', () async {
    late http.Request sent;
    final result = await api((request) async {
      sent = request;
      return http.Response(connected, 200);
    }).submitEventCode('i-1', 'MARG-2026');

    expect(sent.method, 'POST');
    expect(
      sent.url.toString(),
      'https://crowded.test/api/ticket-imports/i-1/event-code',
    );
    expect(sent.headers['Authorization'], 'Bearer token-123');
    expect(sent.headers['content-type'], startsWith('application/json'));
    expect(jsonDecode(sent.body), {'eventCode': 'MARG-2026'});
    expect(result.eventId, 'e-1');
    expect(result.eventName, 'Festival Margens');
    expect(result.gate, 'A');
  });

  test('reports the refusals of the typed code', () async {
    Future<TicketConnected> submit(int status, String code) =>
        api((_) async => http.Response('{"code":"$code"}', status))
            .submitEventCode('i-1', 'MARG-2026');

    await expectLater(
      submit(404, 'EVENT_NOT_ON_CROWDED'),
      refusedWith('EVENT_NOT_ON_CROWDED'),
    );
    await expectLater(
      submit(404, 'IMPORT_NOT_FOUND'),
      refusedWith('IMPORT_NOT_FOUND'),
    );
    await expectLater(
      submit(409, 'TICKET_ALREADY_IMPORTED'),
      refusedWith('TICKET_ALREADY_IMPORTED'),
    );
    expect(
      const TicketsApiException('EVENT_NOT_ON_CROWDED').eventNotOnCrowded,
      isTrue,
    );
    expect(
      const TicketsApiException('IMPORT_NOT_FOUND').importNotFound,
      isTrue,
    );
    expect(const TicketsApiException('NETWORK').importNotFound, isFalse);
  });

  test('fails on an unexpected answer or no connection to the code', () async {
    await expectLater(
      api((_) async => http.Response('<html>', 500))
          .submitEventCode('i-1', 'MARG-2026'),
      refusedWith('HTTP_500'),
    );
    await expectLater(
      api((_) async => http.Response('{}', 200))
          .submitEventCode('i-1', 'MARG-2026'),
      refusedWith('HTTP_200'),
    );
    await expectLater(
      api((_) async => throw http.ClientException('offline'))
          .submitEventCode('i-1', 'MARG-2026'),
      refusedWith('NETWORK'),
    );
  });

  test('fails on an unexpected answer or no connection', () async {
    await expectLater(
      api((_) async => http.Response('<html>', 500)).uploadPdf(pdf, 'b.pdf'),
      refusedWith('HTTP_500'),
    );
    await expectLater(
      api((_) async => throw http.ClientException('offline'))
          .uploadPdf(pdf, 'b.pdf'),
      refusedWith('NETWORK'),
    );
  });
}
