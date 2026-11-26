import 'dart:convert';

import 'package:crowded/data/events_api.dart';
import 'package:crowded/data/models.dart';
import 'package:crowded/ui/ui.dart' show CrowdLevel;
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _event = '30000000-0000-4000-8000-000000000001';

Map<String, Object?> _change({
  String id = 'c-1',
  String type = 'POSTPONEMENT',
  Map<String, Object?> previousValue = const {
    'startDateTime': '2026-11-20T20:00:00Z',
  },
  Map<String, Object?> newValue = const {
    'startDateTime': '2026-11-27T20:00:00Z',
  },
  bool? ticketStillValid = true,
  String? refundInfo,
  String? message,
  String publishedAt = '2026-11-10T10:00:00Z',
}) => {
  'id': id,
  'type': type,
  'severity': 'WARNING',
  'previousValue': previousValue,
  'newValue': newValue,
  'ticketStillValid': ticketStillValid,
  'refundInfo': refundInfo,
  'message': message,
  'provenance': {
    'sourceType': 'ORGANIZATION',
    'sourceName': 'Ribeira Live',
    'lastUpdatedAt': publishedAt,
    'confirmed': true,
  },
};

Map<String, Object?> _page({
  String status = 'POSTPONED',
  Map<String, Object?>? latestChange,
}) => {
  'id': _event,
  'name': 'Festival Margens do Douro',
  'description': 'Música à beira-rio.',
  'type': 'TICKETED',
  'status': status,
  'startDateTime': '2026-11-27T20:00:00Z',
  'doorsOpenAt': '2026-11-27T18:30:00Z',
  'endDateTime': null,
  'venue': {
    'name': 'Cais da Ribeira',
    'address': 'Cais da Ribeira, Porto',
    'district': 'Porto',
    'latitude': 41.14,
    'longitude': -8.61,
  },
  'organization': {'id': 'o-1', 'name': 'Ribeira Live'},
  'latestChange': latestChange,
};

HttpEventsApi _api(http.Response Function(http.Request request) answer) =>
    HttpEventsApi(
      baseUrl: 'https://crowded.test/api',
      client: () => MockClient((request) async => answer(request)),
    );

http.Response _json(Object body, [int status = 200]) => http.Response.bytes(
  utf8.encode(jsonEncode(body)),
  status,
  headers: {'content-type': 'application/json'},
);

void main() {
  group('HttpEventsApi', () {
    test('reads the page', () async {
      late http.Request sent;
      final page = await _api((request) {
        sent = request;
        return _json(_page(latestChange: _change()));
      }).page(_event);

      expect(sent.method, 'GET');
      expect(sent.url.toString(), 'https://crowded.test/api/events/$_event');
      expect(page.name, 'Festival Margens do Douro');
      expect(page.status, EventStatus.postponed);
      expect(page.start, DateTime.utc(2026, 11, 27, 20).toLocal());
      expect(page.doorsOpen, DateTime.utc(2026, 11, 27, 18, 30).toLocal());
      expect(page.end, isNull);
      expect(page.venueName, 'Cais da Ribeira');
      expect(page.organization, 'Ribeira Live');
      expect(page.latestChange?.id, 'c-1');
    });

    test('reads the history in the order Core sends it', () async {
      late Uri asked;
      final changes = await _api((request) {
        asked = request.url;
        return _json([
          _change(id: 'c-2', type: 'CANCELLATION'),
          _change(id: 'c-bad', type: 'POSTPONEMENT', publishedAt: 'never'),
          _change(id: 'c-1'),
        ]);
      }).changes(_event);

      expect(asked.path, '/api/events/$_event/changes');
      expect(changes.map((c) => c.id), [
        'c-2',
        'c-1',
      ], reason: 'a change without its time is left out');
      expect(changes.first.publisher, 'Ribeira Live');
      expect(
        changes.first.publishedAt,
        DateTime.utc(2026, 11, 10, 10).toLocal(),
      );
    });

    test('says why Core refused, or that it could not be reached', () async {
      Future<String> codeOf(http.Response Function(http.Request) answer) =>
          _api(answer)
              .page(_event)
              .then<String>((_) => 'none')
              .catchError((Object e) => (e as EventsException).code);

      expect(
        await codeOf(
          (_) => _json({'code': 'NOT_CONNECTED', 'status': 403}, 403),
        ),
        'NOT_CONNECTED',
      );
      expect(await codeOf((_) => http.Response('', 401)), 'UNAUTHORIZED');
      expect(await codeOf((_) => http.Response('<html>', 502)), 'HTTP_502');
      expect(await codeOf((_) => _json({'name': 'x'})), 'INVALID_RESPONSE');
      expect(await codeOf((_) => http.Response('{', 200)), 'INVALID_RESPONSE');
      expect(
        await codeOf((_) => throw http.ClientException('offline')),
        'NETWORK',
      );
    });

    test('knows which refusals mean the event is no longer visible', () {
      expect(const EventsException('NOT_CONNECTED').noLongerVisible, isTrue);
      expect(const EventsException('EVENT_NOT_FOUND').noLongerVisible, isTrue);
      expect(const EventsException('NETWORK').noLongerVisible, isFalse);
      expect(const EventsException('UNAUTHORIZED').noLongerVisible, isFalse);
    });
  });

  group('HttpEventsApi.live', () {
    HttpEventsApi streaming(
      http.StreamedResponse Function(http.BaseRequest request) answer,
    ) => HttpEventsApi(
      baseUrl: 'https://crowded.test/api',
      client: () => MockClient.streaming((request, _) async => answer(request)),
    );

    test('sends each page of the stream', () async {
      late http.BaseRequest sent;
      final scheduled = jsonEncode(_page(status: 'SCHEDULED'));
      final cancelled = jsonEncode(_page(status: 'CANCELLED'));
      final pages = await streaming((request) {
        sent = request;
        return http.StreamedResponse(
          Stream.fromIterable([
            utf8.encode(':open\n\nevent: event\ndata: $scheduled\n\n'),
            utf8.encode(':\n\nevent: event\ndata: {\n\n'),
            utf8.encode('event: event\ndata: $cancelled\n\n'),
          ]),
          200,
          headers: {'content-type': 'text/event-stream'},
        );
      }).live(_event).toList();

      expect(sent.url.path, '/api/events/$_event/live');
      expect(sent.headers['Accept'], 'text/event-stream');
      expect(pages.map((p) => p.status), [
        EventStatus.scheduled,
        EventStatus.cancelled,
      ], reason: 'a malformed event is skipped');
    });

    test('says why the stream could not be opened or dropped', () async {
      Future<String> codeOf(
        http.StreamedResponse Function(http.BaseRequest) answer,
      ) =>
          streaming(answer)
              .live(_event)
              .drain<void>()
              .then<String>((_) => 'none')
              .catchError((Object e) => (e as EventsException).code);

      http.StreamedResponse answer(int status, String body) =>
          http.StreamedResponse(Stream.value(utf8.encode(body)), status);

      expect(
        await codeOf((_) => answer(403, '{"code":"NOT_CONNECTED"}')),
        'NOT_CONNECTED',
      );
      expect(await codeOf((_) => answer(401, '')), 'UNAUTHORIZED');
      expect(await codeOf((_) => throw http.ClientException('x')), 'NETWORK');
      expect(
        await codeOf(
          (_) => http.StreamedResponse(
            Stream.error(http.ClientException('reset')),
            200,
          ),
        ),
        'NETWORK',
      );
      expect(await codeOf((_) => answer(200, ':open\n\n')), 'none');
    });
  });

  group('eventFromCore', () {
    test('carries the crowd level the organization published', () {
      Map<String, Object?> crowd(String level) => {
        'level': level,
        'provenance': {
          'sourceType': 'ORGANIZATION',
          'sourceName': 'Ribeira Live',
          'lastUpdatedAt': '2026-11-27T21:05:00Z',
          'confirmed': true,
        },
      };
      final page = eventPageFromJson({..._page(), 'crowd': crowd('HIGH')})!;

      final status = eventFromCore(page, const []).venueStatus!;

      expect(status.crowdLevel, CrowdLevel.high);
      expect(
        status.crowd?.updatedAt,
        DateTime.utc(2026, 11, 27, 21, 5).toLocal(),
      );
      expect(status.queues, isEmpty);
      expect(eventPageFromJson(_page())!.venueStatus, isNull);
      expect(
        eventPageFromJson({..._page(), 'crowd': crowd('PACKED')})!.venueStatus,
        isNull,
        reason: 'a level this version does not know',
      );
    });

    test('carries the queue times, with the oldest as their time', () {
      Map<String, Object?> queue(Object? point, Object? minutes, String at) => {
        'pointName': point,
        'waitMinutes': minutes,
        'provenance': {
          'sourceType': 'ORGANIZATION',
          'sourceName': 'Ribeira Live',
          'lastUpdatedAt': at,
        },
      };
      final page = eventPageFromJson({
        ..._page(),
        'queues': [
          queue('Porta A', 10, '2026-11-27T21:05:00Z'),
          queue('Bar', 25, '2026-11-27T20:50:00Z'),
          queue('WC', 'dez', '2026-11-27T21:05:00Z'),
          queue(null, 5, '2026-11-27T21:05:00Z'),
        ],
      })!;

      final status = eventFromCore(page, const []).venueStatus!;

      expect(status.crowdLevel, isNull);
      expect(status.crowd, isNull);
      expect(status.queues.map((q) => (q.place, q.minutes)), [
        ('Porta A', 10),
        ('Bar', 25),
      ]);
      expect(
        status.queuesSource?.updatedAt,
        DateTime.utc(2026, 11, 27, 20, 50).toLocal(),
      );
      expect(
        eventPageFromJson({..._page(), 'queues': []})!.venueStatus,
        isNull,
      );
    });

    test('carries the active alerts, critical ones as urgent', () {
      final page = eventPageFromJson({
        ..._page(),
        'activeAlerts': [
          {
            'id': 'a-2',
            'type': 'ENTRANCE_CLOSURE',
            'severity': 'CRITICAL',
            'message': 'Porta A fechada. Usem a B.',
            'sentAt': '2026-11-27T21:05:00Z',
          },
          {
            'id': 'a-1',
            'type': 'SCHEDULE_DELAY',
            'severity': 'WARNING',
            'message': 'Começa às 22h.',
            'sentAt': '2026-11-27T20:50:00Z',
          },
          {'id': 'a-0', 'type': 'OTHER', 'severity': 'INFO'},
        ],
      })!;

      final alerts = eventFromCore(page, const []).alerts;

      expect(alerts.map((a) => (a.message, a.urgent, a.severity)), [
        ('Porta A fechada. Usem a B.', true, Severity.critical),
        ('Começa às 22h.', false, Severity.warning),
      ]);
      expect(alerts.first.sentAt, DateTime.utc(2026, 11, 27, 21, 5).toLocal());
      expect(alerts.first.publisher, page.organization);
      expect(eventPageFromJson(_page())!.alerts, isEmpty);
    });

    test('reads each kind of source and when Core says it goes stale', () {
      Provenance? read(String type, {Object? confirmed, Object? staleAt}) =>
          provenanceFromJson({
            'sourceType': type,
            'sourceName': 'Nome',
            'lastUpdatedAt': '2026-11-27T21:05:00Z',
            'confirmed': ?confirmed,
            'stale': true,
            'staleAt': ?staleAt,
          });

      expect(read('OFFICIAL_SOURCE')!.type, SourceType.official);
      expect(read('ORGANIZATION')!.type, SourceType.organization);
      expect(read('PARTICIPANT_ESTIMATE')!.type, SourceType.estimate);
      expect(read('EXTERNAL_SERVICE')!.type, SourceType.external);
      final ai = read('AI_GENERATED', confirmed: false)!;
      expect(ai.type, SourceType.ai);
      expect(ai.isUnconfirmedAi, isTrue);
      expect(read('AI_GENERATED', confirmed: true)!.isUnconfirmedAi, isFalse);
      expect(read('ORGANIZATION')!.stale, isTrue);
      expect(read('SOMETHING_NEW'), isNull);
      expect(provenanceFromJson(null), isNull);
      expect(
        provenanceFromJson({'sourceType': 'ORGANIZATION', 'sourceName': 'A'}),
        isNull,
        reason: 'no time',
      );
    });

    test('flags information from the deadline Core sent, not its own rule', () {
      final updated = DateTime.utc(2026, 11, 27, 21, 5);
      final provenance = Provenance.organization(
        updated,
        staleAt: updated.add(const Duration(minutes: 7)),
      );

      expect(
        provenance.isStaleAt(updated.add(const Duration(minutes: 6))),
        isFalse,
      );
      expect(
        provenance.isStaleAt(updated.add(const Duration(minutes: 7))),
        isTrue,
      );
      expect(
        Provenance.organization(updated)
            .isStaleAt(updated.add(const Duration(days: 9))),
        isFalse,
        reason: 'no deadline: an announcement never goes out of date',
      );
    });

    test('keeps the origin of the crowd level, the queues and an alert', () {
      Map<String, Object?> origin(String at, {bool stale = false}) => {
        'sourceType': 'ORGANIZATION',
        'sourceName': 'Ribeira Live',
        'lastUpdatedAt': at,
        'confirmed': true,
        'stale': stale,
        'staleAt': '2026-11-27T21:25:00Z',
      };
      final page = eventPageFromJson({
        ..._page(),
        'crowd': {
          'level': 'HIGH',
          'provenance': origin('2026-11-27T21:05:00Z'),
        },
        'queues': [
          {
            'pointName': 'Bar',
            'waitMinutes': 25,
            'provenance': origin('2026-11-27T20:50:00Z', stale: true),
          },
        ],
        'activeAlerts': [
          {
            'id': 'a-1',
            'type': 'ENTRANCE_CLOSURE',
            'severity': 'CRITICAL',
            'message': 'Usem a Porta B.',
            'sentAt': '2026-11-27T21:00:00Z',
            'provenance': origin('2026-11-27T21:00:00Z'),
          },
        ],
      })!;

      final event = eventFromCore(page, const []);
      final status = event.venueStatus!;

      expect(status.crowd!.name, 'Ribeira Live');
      expect(status.crowd!.stale, isFalse);
      expect(status.queuesSource!.stale, isTrue);
      expect(
        status.queuesSource!.staleAt,
        DateTime.utc(2026, 11, 27, 21, 25).toLocal(),
      );
      expect(event.alerts.single.source.type, SourceType.organization);
      expect(event.alerts.single.source.name, 'Ribeira Live');
    });

    test(
      'carries IPMA\'s forecast and warnings, leaving out unreadable ones',
      () {
        Map<String, Object?> ipma(String at) => {
          'sourceType': 'EXTERNAL_SERVICE',
          'sourceName': 'IPMA',
          'lastUpdatedAt': at,
          'confirmed': true,
        };
        final page = eventPageFromJson({
          ..._page(),
          'weather': {
            'forecast': [
              {'date': '2026-11-27', 'conditions': 'Chuvisco'},
              {
                'date': '2026-11-27',
                'conditions': 'Aguaceiros/chuva',
                'minTemperature': 10.7,
                'maxTemperature': 14.4,
                'precipitationProbability': 100.0,
                'provenance': ipma('2026-11-27T09:31:02Z'),
              },
            ],
            'warnings': [
              {
                'awarenessType': 'Vento',
                'level': 'RED',
                'endTime': '2026-11-28T03:00:00Z',
                'description': 'Rajadas até 130 km/h.',
                'provenance': ipma('2026-11-27T10:05:00Z'),
              },
              {
                'awarenessType': 'Nevoeiro',
                'level': 'YELLOW',
                'endTime': '2026-11-27T23:00:00Z',
                'description': null,
                'provenance': ipma('2026-11-27T10:05:00Z'),
              },
              {'awarenessType': 'Neve', 'level': 'PURPLE'},
            ],
          },
        })!;

        final event = eventFromCore(page, const []);

        expect(event.weather!.summary, '14° · Aguaceiros/chuva');
        expect(
          event.weather!.detail,
          'Mínima de 11° · 100% de probabilidade de chuva',
        );
        expect(event.weather!.icon, WeatherIcon.rain);
        expect(
          event.weather!.updatedAt,
          DateTime.utc(2026, 11, 27, 9, 31, 2).toLocal(),
        );
        expect(event.warnings.map((w) => (w.level, w.title, w.detail)), [
          (WarningLevel.red, 'Vento', 'Rajadas até 130 km/h.'),
          (WarningLevel.yellow, 'Nevoeiro', null),
        ]);
        expect(
          event.warnings.first.issuedAt,
          DateTime.utc(2026, 11, 27, 10, 5).toLocal(),
        );
        expect(
          event.warnings.first.until,
          DateTime.utc(2026, 11, 28, 3).toLocal(),
        );
        expect(eventPageFromJson(_page())!.weather, isNull);
        expect(eventPageFromJson(_page())!.warnings, isEmpty);
      },
    );

    test('picks an icon from the forecast', () {
      expect(weatherIconFor('Céu limpo', 0), WeatherIcon.sun);
      expect(
        weatherIconFor('Céu muito nublado ou encoberto', 20),
        WeatherIcon.cloud,
      );
      expect(weatherIconFor('Trovoada', 30), WeatherIcon.rain);
      expect(weatherIconFor('Céu limpo', 60), WeatherIcon.rain);
    });

    test('builds the event with its changes, newest first', () {
      final history = [
        coreChangeFromJson(
          _change(
            id: 'c-2',
            type: 'TIME_CHANGE',
            previousValue: {'doorsOpenAt': '2026-11-27T19:00:00Z'},
            newValue: {'doorsOpenAt': '2026-11-27T18:30:00Z'},
            ticketStillValid: null,
            publishedAt: '2026-11-11T10:00:00Z',
          ),
        )!,
        coreChangeFromJson(_change())!,
      ];
      final page = eventPageFromJson(_page(latestChange: _change(id: 'c-2')))!;

      final event = eventFromCore(page, history);

      expect(event.id, _event);
      expect(event.place, 'Cais da Ribeira, Porto');
      expect(event.organizer, 'Ribeira Live');
      expect(event.status, EventStatus.postponed);
      expect(event.updatedAt, DateTime.utc(2026, 11, 10, 10).toLocal());
      expect(event.changes.map((c) => c.id), ['c-2', 'c-1']);
      expect(event.changes.first.kind, ChangeKind.timeChanged);
      expect(
        event.changes.first.title,
        'Portas às ${_hhmm(DateTime.utc(2026, 11, 27, 18, 30))}',
      );
      expect(event.latestDateChange?.id, 'c-1');
      expect(
        eventFromCore(page, history).art.from,
        event.art.from,
        reason: 'the same event always gets the same picture',
      );
    });

    test('words each type of change for the participant', () {
      final postponed = changeFromCore(coreChangeFromJson(_change())!)!;
      expect(postponed.kind, ChangeKind.dateChanged);
      expect(postponed.title, 'Data alterada');
      expect(postponed.previousStart, DateTime.utc(2026, 11, 20, 20).toLocal());
      expect(postponed.newStart, DateTime.utc(2026, 11, 27, 20).toLocal());

      final moved = changeFromCore(
        coreChangeFromJson(
          _change(
            type: 'VENUE_CHANGE',
            previousValue: {'venueName': 'Coliseu'},
            newValue: {'venueName': 'Hard Club'},
          ),
        )!,
      )!;
      expect(moved.title, 'Local alterado');
      expect(moved.description, 'Agora em Hard Club.');

      final cancelled = changeFromCore(
        coreChangeFromJson(
          _change(
            type: 'CANCELLATION',
            previousValue: {'status': 'SCHEDULED'},
            newValue: {'status': 'CANCELLED'},
            ticketStillValid: false,
            refundInfo: 'ribeiralive.pt/reembolsos',
            message: '  Lamentamos o cancelamento.  ',
          ),
        )!,
      )!;
      expect(cancelled.kind, ChangeKind.cancelled);
      expect(cancelled.title, 'Evento cancelado');
      expect(cancelled.description, 'Lamentamos o cancelamento.');
      expect(cancelled.ticketStillValid, isFalse);
      expect(cancelled.refund?.where, 'ribeiralive.pt/reembolsos');
      expect(cancelled.refund?.until, isNull);

      final unknown = coreChangeFromJson(_change(type: 'DETAILS_UPDATE'))!;
      expect(changeFromCore(unknown), isNull);
    });

    test('reads every status Core sends', () {
      expect(eventStatusFromCore('SCHEDULED'), EventStatus.scheduled);
      expect(eventStatusFromCore('RELOCATED'), EventStatus.relocated);
      expect(eventStatusFromCore('CANCELLED'), EventStatus.cancelled);
      expect(eventStatusFromCore('ONGOING'), EventStatus.scheduled);
      expect(eventStatusFromCore(null), EventStatus.scheduled);
    });
  });
}

String _hhmm(DateTime utc) {
  final local = utc.toLocal();
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(local.hour)}:${two(local.minute)}';
}
