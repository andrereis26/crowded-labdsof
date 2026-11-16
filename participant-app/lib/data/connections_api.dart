import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'auth_api.dart';

/// The public event a participant connected to with its access code
/// (`POST /connections`, API Outline "Connect to an event").
class JoinedEvent {
  const JoinedEvent({required this.connectionId, required this.eventId});

  final String connectionId;
  final String eventId;
}

/// A refusal from Core, with its error code: `ACCESS_CODE_NOT_FOUND` when
/// the code leads to no public event, `UNAUTHORIZED` when the session ended,
/// or `NETWORK` when Core could not be reached or did not answer in time.
class ConnectionsApiException implements Exception {
  const ConnectionsApiException(this.code);

  final String code;

  bool get notFound => code == 'ACCESS_CODE_NOT_FOUND';

  bool get sessionEnded => code == 'UNAUTHORIZED';

  @override
  String toString() => 'ConnectionsApiException($code)';
}

/// The participant's connections to events in Core.
abstract interface class ConnectionsApi {
  /// Connects the signed-in participant to the public event of [accessCode],
  /// typed or read from its QR code. Core decides the event from the code.
  Future<JoinedEvent> joinWithAccessCode(String accessCode);
}

/// [ConnectionsApi] over HTTP, with the signed-in participant's token, so
/// Core decides who connects.
class HttpConnectionsApi implements ConnectionsApi {
  HttpConnectionsApi({
    required this._token,
    http.Client? client,
    String baseUrl = apiBaseUrl,
    this._timeout = const Duration(seconds: 15),
  }) : _client = client ?? http.Client(),
       _base = Uri.base.resolve(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');

  final String Function() _token;
  final http.Client _client;
  final Uri _base;

  /// How long to wait for Core, so a request that never answers does not
  /// leave the screen waiting for good.
  final Duration _timeout;

  @override
  Future<JoinedEvent> joinWithAccessCode(String accessCode) async {
    final http.Response response;
    try {
      response = await _client
          .post(
            _base.resolve('connections'),
            headers: {
              'Authorization': 'Bearer ${_token()}',
              'Content-Type': 'application/json',
            },
            body: jsonEncode({'accessCode': accessCode}),
          )
          .timeout(_timeout);
    } on Exception {
      throw const ConnectionsApiException('NETWORK');
    }
    if (response.statusCode == 401) {
      throw const ConnectionsApiException('UNAUTHORIZED');
    }
    final body = _json(response);
    final event = body['event'];
    if ((response.statusCode == 200 || response.statusCode == 201) &&
        body['connectionId'] is String &&
        event is Map<String, dynamic> &&
        event['id'] is String) {
      return JoinedEvent(
        connectionId: body['connectionId'] as String,
        eventId: event['id'] as String,
      );
    }
    final code = body['code'];
    throw ConnectionsApiException(
      code is String ? code : 'HTTP_${response.statusCode}',
    );
  }

  static Map<String, dynamic> _json(http.Response response) {
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      return decoded is Map<String, dynamic> ? decoded : const {};
    } on FormatException {
      return const {};
    }
  }
}
