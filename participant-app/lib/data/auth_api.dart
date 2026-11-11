import 'dart:convert';

import 'package:http/http.dart' as http;

/// Base address of the Crowded API. The web app is served by the same router
/// as the API, so the default is relative; a mobile build passes the full
/// address with `--dart-define=API_BASE_URL=https://…/api`.
const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: '/api');

/// A signed-in session: the short-lived token Core issued and who it is for.
class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.expiresAt,
    required this.userId,
    required this.role,
    required this.email,
  });

  final String accessToken;
  final DateTime expiresAt;
  final String userId;
  final String role;
  final String email;
}

/// A refusal from Core, with its error code (API Outline), or `NETWORK` when
/// Core could not be reached.
class AuthException implements Exception {
  const AuthException(this.code);

  final String code;

  @override
  String toString() => 'AuthException($code)';
}

/// Registration and sign-in in Core (`POST /auth/register`, `POST /auth/login`).
abstract interface class AuthApi {
  Future<void> register({required String email, required String password});

  Future<AuthSession> signIn({required String email, required String password});
}

/// [AuthApi] over HTTP. The token is kept in memory only.
class HttpAuthApi implements AuthApi {
  HttpAuthApi({http.Client? client, String baseUrl = apiBaseUrl})
    : _client = client ?? http.Client(),
      _base = Uri.base.resolve(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');

  final http.Client _client;
  final Uri _base;

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
    });
    final user = body['user'] as Map<String, dynamic>;
    return AuthSession(
      accessToken: body['accessToken'] as String,
      expiresAt: DateTime.parse(body['expiresAt'] as String),
      userId: user['id'] as String,
      role: user['role'] as String,
      email: email.trim().toLowerCase(),
    );
  }

  Future<Map<String, dynamic>> _post(
    String path,
    Map<String, String> json,
  ) async {
    final http.Response response;
    try {
      response = await _client.post(
        _base.resolve(path),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(json),
      );
    } on Exception {
      throw const AuthException('NETWORK');
    }
    final Object? body = response.body.isEmpty ? null : _decode(response.body);
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return body is Map<String, dynamic> ? body : const {};
    }
    final code = body is Map<String, dynamic> ? body['code'] : null;
    throw AuthException(code is String ? code : 'HTTP_${response.statusCode}');
  }

  static Object? _decode(String text) {
    try {
      return jsonDecode(text);
    } on FormatException {
      return null;
    }
  }
}
