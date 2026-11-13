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
