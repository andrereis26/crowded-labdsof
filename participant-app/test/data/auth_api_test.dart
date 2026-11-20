import 'dart:convert';

import 'package:crowded/data/auth_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// [TokenStore] in memory, as the phone's secure storage would keep it.
class _MemoryTokens implements TokenStore {
  ({String accessToken, String refreshToken})? kept;

  @override
  Future<({String accessToken, String refreshToken})?> read() async => kept;

  @override
  Future<void> write({
    required String accessToken,
    required String refreshToken,
  }) async => kept = (accessToken: accessToken, refreshToken: refreshToken);

  @override
  Future<void> clear() async => kept = null;
}

/// Core's answers, by method and path, with every request recorded.
class _Core {
  _Core(this.answers);

  final Map<String, http.Response Function(http.Request)> answers;
  final sent = <http.Request>[];

  http.Client client() => MockClient((request) async {
    sent.add(request);
    final answer = answers['${request.method} ${request.url.path}'];
    return answer == null ? http.Response('', 404) : answer(request);
  });

  List<String> get calls => [for (final r in sent) '${r.method} ${r.url.path}'];
}

http.Response _json(Object body, [int status = 200]) =>
    http.Response(jsonEncode(body), status);

const _user = {'id': 'u-1', 'role': 'PARTICIPANT', 'organizationId': null};
const _me = {
  'id': 'u-1',
  'email': 'ana@email.pt',
  'name': null,
  'role': 'PARTICIPANT',
  'organizationId': null,
};

void main() {
  group('on a phone', () {
    test('signs in with the tokens in the body and keeps them', () async {
      final tokens = _MemoryTokens();
      final core = _Core({
        'POST /api/auth/login': (_) => _json({
          'accessToken': 'jwt-1',
          'refreshToken': 'refresh-1',
          'expiresAt': '2026-11-11T11:00:00Z',
          'user': _user,
        }),
      });
      final api = HttpAuthApi(
        baseUrl: 'https://crowded.test/api',
        client: core.client,
        cookies: false,
        tokens: tokens,
      );

      final session = await api.signIn(email: ' Ana@Email.pt ', password: 'p');

      final sent = core.sent.single;
      expect(sent.headers['Content-Type'], startsWith('application/json'));
      expect(jsonDecode(sent.body), {
        'email': ' Ana@Email.pt ',
        'password': 'p',
        'delivery': 'BODY',
      });
      expect(session.accessToken, 'jwt-1');
      expect(session.userId, 'u-1');
      expect(session.role, 'PARTICIPANT');
      expect(session.email, 'ana@email.pt');
      expect(tokens.kept, (accessToken: 'jwt-1', refreshToken: 'refresh-1'));
    });

    test('sends the token, and refreshes it once when it expired', () async {
      final tokens = _MemoryTokens()
        ..kept = (accessToken: 'old', refreshToken: 'refresh-1');
      final core = _Core({
        'GET /api/me': (r) => r.headers['Authorization'] == 'Bearer new'
            ? _json(_me)
            : http.Response('', 401),
        'POST /api/auth/refresh': (r) =>
            jsonDecode(r.body)['refreshToken'] == 'refresh-1'
            ? _json({
                'accessToken': 'new',
                'refreshToken': 'refresh-2',
                'user': _user,
              })
            : _json({'code': 'INVALID_REFRESH_TOKEN'}, 401),
      });
      final api = HttpAuthApi(
        baseUrl: 'https://crowded.test/api',
        client: core.client,
        cookies: false,
        tokens: tokens,
      );

      final session = await api.restore();

      expect(session?.email, 'ana@email.pt');
      expect(session?.accessToken, 'new');
      expect(core.calls, [
        'GET /api/me',
        'POST /api/auth/refresh',
        'GET /api/me',
      ]);
      expect(tokens.kept, (accessToken: 'new', refreshToken: 'refresh-2'));
    });

    test('forgets the tokens when the session has ended', () async {
      final tokens = _MemoryTokens()
        ..kept = (accessToken: 'old', refreshToken: 'stolen');
      final core = _Core({
        'GET /api/me': (_) => http.Response('', 401),
        'POST /api/auth/refresh': (_) =>
            _json({'code': 'INVALID_REFRESH_TOKEN'}, 401),
      });
      var ended = 0;
      final api = HttpAuthApi(
        baseUrl: 'https://crowded.test/api',
        client: core.client,
        cookies: false,
        tokens: tokens,
      )..onEnded = () => ended++;

      expect(await api.restore(), isNull);
      expect(tokens.kept, isNull);
      expect(ended, 1);
    });

    test('starts signed out without stored tokens', () async {
      final core = _Core({});
      final api = HttpAuthApi(
        client: core.client,
        cookies: false,
        tokens: _MemoryTokens(),
      );

      expect(await api.restore(), isNull);
      expect(core.sent, isEmpty);
    });

    test('signs out with the tokens, and forgets them', () async {
      final tokens = _MemoryTokens();
      final core = _Core({
        'POST /api/auth/login': (_) => _json({
          'accessToken': 'jwt-1',
          'refreshToken': 'refresh-1',
          'user': _user,
        }),
        'POST /api/auth/logout': (_) => http.Response('', 204),
      });
      final api = HttpAuthApi(
        baseUrl: 'https://crowded.test/api',
        client: core.client,
        cookies: false,
        tokens: tokens,
      );
      await api.signIn(email: 'ana@email.pt', password: 'p');

      await api.signOut();

      final logout = core.sent.last;
      expect(logout.url.path, '/api/auth/logout');
      expect(logout.headers['Authorization'], 'Bearer jwt-1');
      expect(jsonDecode(logout.body), {'refreshToken': 'refresh-1'});
      expect(tokens.kept, isNull);
    });
  });

  group('on the web', () {
    test('asks for cookies and keeps no token', () async {
      final core = _Core({
        'POST /api/auth/login': (_) =>
            _json({'expiresAt': '2026-11-11T11:00:00Z', 'user': _user}),
        'GET /api/me': (r) => r.headers.containsKey('Authorization')
            ? http.Response('', 400)
            : _json(_me),
      });
      final api = HttpAuthApi(
        baseUrl: 'https://crowded.test/api',
        client: core.client,
        cookies: true,
      );

      final session = await api.signIn(email: 'ana@email.pt', password: 'p');
      expect(jsonDecode(core.sent.single.body)['delivery'], 'COOKIE');
      expect(session.accessToken, isNull);

      // A reload: the browser sends the cookies, which only Core reads.
      expect((await api.restore())?.email, 'ana@email.pt');
    });

    test('repeats a request after another tab refreshed the session', () async {
      var refreshed = false;
      final core = _Core({
        'GET /api/notifications': (_) =>
            refreshed ? _json({'notifications': []}) : http.Response('', 401),
        'POST /api/auth/refresh': (_) {
          refreshed = true;
          return _json({'code': 'REFRESH_TOKEN_ROTATED'}, 401);
        },
      });
      final api = HttpAuthApi(
        baseUrl: 'https://crowded.test/api',
        client: core.client,
        cookies: true,
      );

      final response = await api.client().get(
        Uri.parse('https://crowded.test/api/notifications'),
      );

      expect(response.statusCode, 200);
      expect(core.calls, [
        'GET /api/notifications',
        'POST /api/auth/refresh',
        'GET /api/notifications',
      ]);
    });

    test('cannot send a file twice, so the upload fails once', () async {
      final core = _Core({
        'POST /api/tickets': (_) => http.Response('', 401),
        'POST /api/auth/refresh': (_) => _json({'user': _user}),
      });
      final api = HttpAuthApi(
        baseUrl: 'https://crowded.test/api',
        client: core.client,
        cookies: true,
      );
      final upload = http.MultipartRequest(
        'POST',
        Uri.parse('https://crowded.test/api/tickets'),
      )..fields['x'] = 'y';

      final response = await api.client().send(upload);

      expect(response.statusCode, 401);
      // Refreshed for the next attempt, but not sent again.
      expect(core.calls, ['POST /api/tickets', 'POST /api/auth/refresh']);
    });
  });

  test('registers at /auth/register', () async {
    late Uri url;
    final api = HttpAuthApi(
      baseUrl: 'https://crowded.test/api/',
      client: () => MockClient((request) async {
        url = request.url;
        return http.Response('{"userId": "u-2", "role": "PARTICIPANT"}', 201);
      }),
      cookies: true,
    );

    await api.register(email: 'ana@email.pt', password: 'palavra-secreta');

    expect(url.toString(), 'https://crowded.test/api/auth/register');
  });

  test('turns a refusal into its code', () async {
    final api = HttpAuthApi(
      baseUrl: 'https://crowded.test/api',
      client: () => MockClient(
        (_) async => http.Response(
          '{"status": 401, "code": "INVALID_CREDENTIALS"}',
          401,
        ),
      ),
      cookies: true,
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
      client: () => MockClient(
        (_) async => http.Response('<html>bad gateway</html>', 502),
      ),
      cookies: true,
    );

    expect(
      () => api.register(email: 'a@b.pt', password: 'palavra-secreta'),
      throwsA(isA<AuthException>().having((e) => e.code, 'code', 'HTTP_502')),
    );
  });

  test('says NETWORK when Core cannot be reached', () async {
    final api = HttpAuthApi(
      baseUrl: 'https://crowded.test/api',
      client: () =>
          MockClient((_) async => throw http.ClientException('offline')),
      cookies: true,
    );

    expect(
      () => api.signIn(email: 'a@b.pt', password: 'x'),
      throwsA(isA<AuthException>().having((e) => e.code, 'code', 'NETWORK')),
    );
    expect(await api.restore(), isNull);
    await api.signOut();
    expect(const AuthException('NETWORK').toString(), 'AuthException(NETWORK)');
  });
}
