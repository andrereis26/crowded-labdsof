import 'dart:convert';

import 'package:crowded/data/auth_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('signs in and reads the session from Core', () async {
    late http.Request sent;
    final api = HttpAuthApi(
      baseUrl: 'https://crowded.test/api',
      client: MockClient((request) async {
        sent = request;
        return http.Response(
          jsonEncode({
            'accessToken': 'jwt',
            'expiresAt': '2026-11-11T11:00:00Z',
            'mustChangePassword': false,
            'user': {
              'id': 'u-1',
              'role': 'PARTICIPANT',
              'organizationId': null,
            },
          }),
          200,
        );
      }),
    );

    final session = await api.signIn(email: ' Ana@Email.pt ', password: 'p');

    expect(sent.method, 'POST');
    expect(sent.url.toString(), 'https://crowded.test/api/auth/login');
    expect(sent.headers['Content-Type'], startsWith('application/json'));
    expect(jsonDecode(sent.body), {'email': ' Ana@Email.pt ', 'password': 'p'});
    expect(session.accessToken, 'jwt');
    expect(session.expiresAt, DateTime.utc(2026, 11, 11, 11));
    expect(session.userId, 'u-1');
    expect(session.role, 'PARTICIPANT');
    expect(session.email, 'ana@email.pt');
  });

  test('registers at /auth/register', () async {
    late Uri url;
    final api = HttpAuthApi(
      baseUrl: 'https://crowded.test/api/',
      client: MockClient((request) async {
        url = request.url;
        return http.Response('{"userId": "u-2", "role": "PARTICIPANT"}', 201);
      }),
    );

    await api.register(email: 'ana@email.pt', password: 'palavra-secreta');

    expect(url.toString(), 'https://crowded.test/api/auth/register');
  });

  test('turns a refusal into its code', () async {
    final api = HttpAuthApi(
      baseUrl: 'https://crowded.test/api',
      client: MockClient(
        (_) async => http.Response(
          '{"status": 401, "code": "INVALID_CREDENTIALS"}',
          401,
        ),
      ),
    );

    expect(
      () => api.signIn(email: 'a@b.pt', password: 'x'),
      throwsA(
        isA<AuthException>().having(
          (e) => e.code,
          'code',
          'INVALID_CREDENTIALS',
        ),
      ),
    );
  });

  test('names an answer without a code by its status', () async {
    final api = HttpAuthApi(
      baseUrl: 'https://crowded.test/api',
      client: MockClient(
        (_) async => http.Response('<html>bad gateway</html>', 502),
      ),
    );

    expect(
      () => api.register(email: 'a@b.pt', password: 'palavra-secreta'),
      throwsA(isA<AuthException>().having((e) => e.code, 'code', 'HTTP_502')),
    );
  });

  test('says NETWORK when Core cannot be reached', () async {
    final api = HttpAuthApi(
      baseUrl: 'https://crowded.test/api',
      client: MockClient((_) async => throw http.ClientException('offline')),
    );

    expect(
      () => api.signIn(email: 'a@b.pt', password: 'x'),
      throwsA(isA<AuthException>().having((e) => e.code, 'code', 'NETWORK')),
    );
    expect(const AuthException('NETWORK').toString(), 'AuthException(NETWORK)');
  });
}
