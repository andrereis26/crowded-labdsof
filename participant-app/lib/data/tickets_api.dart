import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import 'auth_api.dart';

/// What `POST /tickets` answered (API Outline, "Connect to an event").
sealed class TicketUploadResult {
  const TicketUploadResult();
}

/// The ticket matched a registered event and the participant is connected to
/// it. The event's data is the organization's; gate, sector and seat are as
/// read from the ticket.
final class TicketConnected extends TicketUploadResult {
  const TicketConnected({
    required this.connectionId,
    required this.eventId,
    required this.eventName,
    required this.start,
    required this.venueName,
    this.ticketId,
    this.doorsOpen,
    this.gate,
    this.sector,
    this.seat,
  });

  final String connectionId;

  /// Null when the participant was connected to the event without a ticket.
  final String? ticketId;
  final String eventId;
  final String eventName;
  final DateTime start;
  final DateTime? doorsOpen;
  final String venueName;
  final String? gate;
  final String? sector;
  final String? seat;
}

/// The import waits for the event code printed on the ticket.
final class TicketNeedsEventCode extends TicketUploadResult {
  const TicketNeedsEventCode({
    required this.importId,
    this.gate,
    this.sector,
    this.seat,
  });

  final String importId;
  final String? gate;
  final String? sector;
  final String? seat;
}

/// The file has no readable QR code, so nothing was stored.
final class TicketWithoutQrCode extends TicketUploadResult {
  const TicketWithoutQrCode();
}

/// A refusal from Core, with its error code: `RATE_LIMITED` after too many
/// imports in an hour, `TICKET_ALREADY_IMPORTED` when another account has the
/// ticket, or `NETWORK` when Core could not be reached.
class TicketsApiException implements Exception {
  const TicketsApiException(this.code);

  final String code;

  bool get tooManyRequests => code == 'RATE_LIMITED';

  bool get alreadyImported => code == 'TICKET_ALREADY_IMPORTED';

  @override
  String toString() => 'TicketsApiException($code)';
}

/// Ticket import in Core (`POST /tickets`).
abstract interface class TicketsApi {
  /// Uploads a ticket PDF for the signed-in participant.
  Future<TicketUploadResult> uploadPdf(Uint8List bytes, String fileName);
}

/// [TicketsApi] over HTTP, with the signed-in participant's token, so Core
/// decides whose ticket it is.
class HttpTicketsApi implements TicketsApi {
  HttpTicketsApi({
    required this._token,
    http.Client? client,
    String baseUrl = apiBaseUrl,
  }) : _client = client ?? http.Client(),
       _base = Uri.base.resolve(baseUrl.endsWith('/') ? baseUrl : '$baseUrl/');

  final String Function() _token;
  final http.Client _client;
  final Uri _base;

  @override
  Future<TicketUploadResult> uploadPdf(Uint8List bytes, String fileName) async {
    final request = http.MultipartRequest('POST', _base.resolve('tickets'))
      ..headers['Authorization'] = 'Bearer ${_token()}'
      ..files.add(
        http.MultipartFile.fromBytes('file', bytes, filename: fileName),
      );
    final http.Response response;
    try {
      response = await http.Response.fromStream(await _client.send(request));
    } on Exception {
      throw const TicketsApiException('NETWORK');
    }
    final body = _json(response);
    final ticket = body['ticket'] is Map<String, dynamic>
        ? body['ticket'] as Map<String, dynamic>
        : const <String, dynamic>{};
    final event = body['event'];
    final start = event is Map<String, dynamic>
        ? _instant(event['startDateTime'])
        : null;
    if (response.statusCode == 200 &&
        body['status'] == 'CONNECTED' &&
        body['connectionId'] is String &&
        event is Map<String, dynamic> &&
        event['id'] is String &&
        event['name'] is String &&
        start != null) {
      return TicketConnected(
        connectionId: body['connectionId'] as String,
        ticketId: body['ticketId'] as String?,
        eventId: event['id'] as String,
        eventName: event['name'] as String,
        start: start,
        doorsOpen: _instant(event['doorsOpenAt']),
        venueName: event['venueName'] as String? ?? '',
        gate: ticket['gate'] as String?,
        sector: ticket['sector'] as String?,
        seat: ticket['seat'] as String?,
      );
    }
    if (response.statusCode == 200 && body['status'] == 'NEEDS_EVENT_CODE') {
      return TicketNeedsEventCode(
        importId: body['importId'] as String,
        gate: ticket['gate'] as String?,
        sector: ticket['sector'] as String?,
        seat: ticket['seat'] as String?,
      );
    }
    final code = body['code'];
    if (code == 'QR_CODE_NOT_FOUND') return const TicketWithoutQrCode();
    throw TicketsApiException(
      code is String ? code : 'HTTP_${response.statusCode}',
    );
  }

  static DateTime? _instant(Object? value) =>
      value is String ? DateTime.tryParse(value)?.toLocal() : null;

  static Map<String, dynamic> _json(http.Response response) {
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      return decoded is Map<String, dynamic> ? decoded : const {};
    } on FormatException {
      return const {};
    }
  }
}
