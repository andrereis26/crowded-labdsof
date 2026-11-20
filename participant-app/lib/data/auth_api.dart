import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;

/// Base address of the Crowded API. The web app is served by the same router
/// as the API, so the default is relative; a mobile build passes the full
/// address with `--dart-define=API_BASE_URL=https://…/api`.
const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: '/api');

/// A signed-in session: who it is for and, on a phone, its tokens. On the
/// web Core keeps the tokens in cookies the app cannot read (ADR 0021).
class AuthSession {
  const AuthSession({
    required this.userId,
    required this.role,
    required this.email,
    this.accessToken,
    this.refreshToken,
  });

  final String userId;
  final String role;
  final String email;
  final String? accessToken;
  final String? refreshToken;

  AuthSession _withTokens(String accessToken, String refreshToken) =>
      AuthSession(
        userId: userId,
        role: role,
        email: email,
        accessToken: accessToken,
        refreshToken: refreshToken,
      );
}

/// A refusal from Core, with its error code (API Outline), or `NETWORK` when
/// Core could not be reached.
class AuthException implements Exception {
  const AuthException(this.code);

  final String code;

  @override
  String toString() => 'AuthException($code)';
}

/// Registration, sign-in and the session that follows, in Core (ADR 0021).
abstract interface class AuthApi {
  Future<void> register({required String email, required String password});

  Future<AuthSession> signIn({required String email, required String password});

  /// The session kept from an earlier visit, or null when there is none.
  Future<AuthSession?> restore();

  /// Ends the session with Core and forgets its tokens.
  Future<void> signOut();

  /// A client for Core's and Notifications' requests in this session: it
  /// sends the token, and refreshes the session when the token expires.
  http.Client client();

  /// Called when the session ended, such as when it was not used for 30 days
  /// or the participant signed out on another device.
  set onEnded(void Function()? callback);
}

/// Where a phone keeps the session's tokens between visits.
abstract interface class TokenStore {
  Future<({String accessToken, String refreshToken})?> read();

  Future<void> write({
    required String accessToken,
    required String refreshToken,
  });

  Future<void> clear();
}

/// [TokenStore] in the phone's secure storage: the Keychain on iOS, and
/// Keystore-encrypted storage on Android.
class SecureTokenStore implements TokenStore {
  SecureTokenStore([FlutterSecureStorage? storage])
    : _storage = storage ?? const FlutterSecureStorage();

  static const _access = 'crowded.accessToken';
  static const _refresh = 'crowded.refreshToken';

  final FlutterSecureStorage _storage;

  @override
  Future<({String accessToken, String refreshToken})?> read() async {
    final access = await _storage.read(key: _access);
    final refresh = await _storage.read(key: _refresh);
    if (access == null || refresh == null) return null;
    return (accessToken: access, refreshToken: refresh);
  }

  @override
  Future<void> write({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _storage.write(key: _access, value: accessToken);
    await _storage.write(key: _refresh, value: refreshToken);
  }

  @override
  Future<void> clear() async {
    await _storage.delete(key: _access);
    await _storage.delete(key: _refresh);
  }
}

/// [TokenStore] that keeps nothing: on the web the tokens are in cookies.
class NoTokenStore implements TokenStore {
  const NoTokenStore();

  @override
  Future<({String accessToken, String refreshToken})?> read() async => null;

  @override
  Future<void> write({
    required String accessToken,
    required String refreshToken,
  }) async {}

  @override
  Future<void> clear() async {}
}

enum _Refresh { refreshed, rotated, ended }

/// [AuthApi] over HTTP. On the web Core keeps the session in cookies, which
/// the browser sends; on a phone the tokens come in the body and are kept in
/// [TokenStore], so the participant stays signed in between visits.
class HttpAuthApi implements AuthApi {
  HttpAuthApi({
    http.Client Function()? client,
    String baseUrl = apiBaseUrl,
    bool? cookies,
    TokenStore? tokens,
  }) : _client = client ?? http.Client.new,
       _base = Uri.base.resolve(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/'),
       _cookies = cookies ?? kIsWeb,
       _tokens =
           tokens ??
           ((cookies ?? kIsWeb) ? const NoTokenStore() : SecureTokenStore());

  final http.Client Function() _client;
  final Uri _base;
  final bool _cookies;
  final TokenStore _tokens;
  late final http.Client _http = _client();
  AuthSession? _session;
  Future<_Refresh>? _refreshing;
  void Function()? _onEnded;

  @override
  set onEnded(void Function()? callback) => _onEnded = callback;

  @override
  Future<void> register({
    required String email,
    required String password,
  }) async {
    await _post('auth/register', {'email': email, 'password': password});
  }

  @override
  Future<AuthSession> signIn({
    required String email,
    required String password,
  }) async {
    final body = await _post('auth/login', {
      'email': email,
      'password': password,
      'delivery': _cookies ? 'COOKIE' : 'BODY',
    });
    final user = body['user'] as Map<String, dynamic>;
    final session = AuthSession(
      userId: user['id'] as String,
      role: user['role'] as String,
      email: email.trim().toLowerCase(),
      accessToken: body['accessToken'] as String?,
      refreshToken: body['refreshToken'] as String?,
    );
    await _keep(session);
    return session;
  }

  @override
  Future<AuthSession?> restore() async {
    if (!_cookies) {
      final stored = await _tokens.read();
      if (stored == null) return null;
      _session = AuthSession(
        userId: '',
        role: '',
        email: '',
        accessToken: stored.accessToken,
        refreshToken: stored.refreshToken,
      );
    }
    final http.Response response;
    try {
      response = await client().get(_base.resolve('me'));
    } on Exception {
      // Offline: the stored tokens stay for the next visit.
      _session = null;
      return null;
    }
    if (response.statusCode != 200) {
      // Core may be restarting (502) or busy (429): the stored tokens stay for
      // the next visit. A session that ended was forgotten when its refresh
      // was refused.
      _session = null;
      return null;
    }
    final me = _decode(response.body);
    if (me is! Map<String, dynamic>) return null;
    final session = AuthSession(
      userId: me['id'] as String,
      role: me['role'] as String,
      email: me['email'] as String,
      accessToken: _session?.accessToken,
      refreshToken: _session?.refreshToken,
    );
    _session = session;
    return session;
  }

  @override
  Future<void> signOut() async {
    final session = _session;
    await _forget();
    try {
      await _http.post(
        _base.resolve('auth/logout'),
        headers: {
          'Content-Type': 'application/json',
          if (session?.accessToken != null)
            'Authorization': 'Bearer ${session!.accessToken}',
        },
        body: jsonEncode({'refreshToken': ?session?.refreshToken}),
      );
    } on Exception {
      // Signed out here; the session ends on Core when it expires.
    }
  }

  @override
  http.Client client() => _SessionClient(_client(), this);

  Future<void> _keep(AuthSession session) async {
    _session = session;
    final access = session.accessToken;
    final refresh = session.refreshToken;
    if (!_cookies && access != null && refresh != null) {
      await _tokens.write(accessToken: access, refreshToken: refresh);
    }
  }

  Future<void> _forget() async {
    _session = null;
    await _tokens.clear();
  }

  /// Refreshes the session once for every request that found its token
  /// expired at the same time.
  Future<_Refresh> _refresh() =>
      _refreshing ??= _sendRefresh().whenComplete(() => _refreshing = null);

  Future<_Refresh> _sendRefresh() async {
    final session = _session;
    if (!_cookies && session?.refreshToken == null) return _Refresh.ended;
    final http.Response response;
    try {
      response = await _http.post(
        _base.resolve('auth/refresh'),
        headers: {'Content-Type': 'application/json'},
        body: _cookies
            ? null
            : jsonEncode({'refreshToken': session!.refreshToken}),
      );
    } on Exception {
      // Offline: the request fails as it would have, and the session stays.
      return _Refresh.rotated;
    }
    final body = _decode(response.body);
    if (response.statusCode == 200 && body is Map<String, dynamic>) {
      final access = body['accessToken'];
      final refresh = body['refreshToken'];
      if (session != null && access is String && refresh is String) {
        await _keep(session._withTokens(access, refresh));
      }
      return _Refresh.refreshed;
    }
    final code = body is Map<String, dynamic> ? body['code'] : null;
    return code == 'REFRESH_TOKEN_ROTATED' ? _Refresh.rotated : _Refresh.ended;
  }

  void _ended() {
    if (_session == null && !_cookies) return;
    unawaited(_forget());
    _onEnded?.call();
  }

  Future<Map<String, dynamic>> _post(
    String path,
    Map<String, String> json,
  ) async {
    final http.Response response;
    try {
      response = await _http.post(
        _base.resolve(path),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(json),
      );
    } on Exception {
      throw const AuthException('NETWORK');
    }
    final Object? body = _decode(response.body);
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return body is Map<String, dynamic> ? body : const {};
    }
    final code = body is Map<String, dynamic> ? body['code'] : null;
    throw AuthException(code is String ? code : 'HTTP_${response.statusCode}');
  }

  static Object? _decode(String text) {
    if (text.isEmpty) return null;
    try {
      return jsonDecode(text);
    } on FormatException {
      return null;
    }
  }
}

/// Sends a session's requests: with the bearer token on a phone, and with
/// the cookies on the web. When the token has expired (401), it refreshes the
/// session and sends the request again; a file upload cannot be sent twice,
/// so it fails and the participant tries again.
class _SessionClient extends http.BaseClient {
  _SessionClient(this._inner, this._auth);

  final http.Client _inner;
  final HttpAuthApi _auth;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final again = request is http.Request ? _copy(request) : null;
    final response = await _inner.send(_authorized(request));
    if (response.statusCode != 401) return response;
    final outcome = await _auth._refresh();
    if (outcome == _Refresh.ended) {
      _auth._ended();
      return response;
    }
    if (again == null) return response;
    await response.stream.drain<void>();
    final repeated = await _inner.send(_authorized(again));
    if (repeated.statusCode == 401) _auth._ended();
    return repeated;
  }

  http.BaseRequest _authorized(http.BaseRequest request) {
    final token = _auth._session?.accessToken;
    if (token != null) request.headers['Authorization'] = 'Bearer $token';
    return request;
  }

  static http.Request _copy(http.Request request) =>
      http.Request(request.method, request.url)
        ..headers.addAll(request.headers)
        ..bodyBytes = request.bodyBytes
        ..followRedirects = request.followRedirects
        ..maxRedirects = request.maxRedirects
        ..persistentConnection = request.persistentConnection;

  @override
  void close() => _inner.close();
}
