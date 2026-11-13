import 'package:flutter/painting.dart';

import '../ui/ui.dart';
import 'models.dart';

/// Demo content that mirrors the mockups, with dates relative to [now] so
/// "HOJE" is always today. Replaced by the Core API once it exists.
class DemoData {
  DemoData(this.now);

  final DateTime now;

  DateTime _day(int offset, int hour, [int minute = 0]) =>
      DateTime(now.year, now.month, now.day + offset, hour, minute);

  DateTime _ago(Duration d) => now.subtract(d);

  late final festivalMargens = Event(
    id: 'festival-margens',
    name: 'Festival Margens',
    venue: 'Parque da Cidade',
    city: 'Porto',
    start: _day(0, 22),
    doorsOpen: _day(0, 20),
    end: _day(1, 2),
    art: const EventArt(Color(0xFF3C50AA), Color(0xFF14142D)),
    organizer: 'Organização Margens',
    eventCode: 'MARG-2026',
    updatedAt: _ago(const Duration(minutes: 5)),
    alerts: [
      EventAlert(
        message: 'Porta B fechada. Use a Porta C.',
        publisher: 'Organização Margens',
        sentAt: _ago(const Duration(minutes: 3)),
      ),
    ],
    warnings: [
      WeatherWarning(
        level: WarningLevel.yellow,
        title: 'Vento forte no distrito do Porto',
        issuedAt: _day(0, 14),
        until: _day(0, 23, 59),
      ),
    ],
    venueStatus: VenueStatus(
      crowdLevel: CrowdLevel.moderate,
      crowdUpdatedAt: _ago(const Duration(minutes: 2)),
      queuesUpdatedAt: _ago(const Duration(minutes: 1)),
      queues: const [
        QueueTime(place: 'Porta A', minutes: 10),
        QueueTime(place: 'Porta C', minutes: 25, increasing: true),
        QueueTime(place: 'Bar principal', minutes: 5),
      ],
    ),
    weather: WeatherInfo(
      summary: '18° · Céu limpo',
      detail: 'Chuva fraca possível depois das 23h (10%)',
      updatedAt: _ago(const Duration(minutes: 30)),
    ),
    estimates: [
      ParticipantEstimate(
        title: 'Fila nos WC junto ao palco · 15–20 min',
        category: ReportCategory.queue,
        reportedAt: _ago(const Duration(minutes: 4)),
        confirmed: 9,
        denied: 1,
        confidence: ConfidenceLevel.high,
      ),
      ParticipantEstimate(
        title: 'Rampa da entrada Norte bloqueada',
        category: ReportCategory.accessibility,
        reportedAt: _ago(const Duration(minutes: 15)),
        confirmed: 4,
        denied: 1,
        confidence: ConfidenceLevel.high,
      ),
    ],
    places: const [
      VenuePlace('Porta A', 'na'),
      VenuePlace('Porta B', 'na'),
      VenuePlace('Porta C', 'na'),
      VenuePlace('Entrada Norte', 'na'),
      VenuePlace('Bar principal', 'no'),
      VenuePlace('WC junto ao palco', 'nos'),
      VenuePlace('Palco principal', 'no'),
    ],
    directions: Directions(
      entrance: 'Porta A',
      entranceDetail: 'Entrada principal',
      operators: 'Metro do Porto e STCP',
      updatedAt: _ago(const Duration(minutes: 1)),
      options: const [
        TransportOption(
          mode: TransportMode.metro,
          title: 'Metro + 8 min a pé',
          detail: 'Metro do Porto · linha D · 21:12 · 21:24',
          minutes: 26,
        ),
        TransportOption(
          mode: TransportMode.bus,
          title: 'Autocarro 502',
          detail: 'STCP: desvio na Av. da Boavista · próximo às 21:05',
          minutes: 25,
          disrupted: true,
        ),
        TransportOption(
          mode: TransportMode.walk,
          title: 'A pé',
          detail: '2,6 km',
          minutes: 35,
        ),
        TransportOption(
          mode: TransportMode.car,
          title: 'Carro ou TVDE',
          detail: 'Estacionamento limitado junto ao recinto',
          minutes: 12,
        ),
      ],
    ),
    changes: [
      EventChange(
        id: 'margens-published',
        kind: ChangeKind.published,
        title: 'Evento publicado',
        description: 'Data, horário e local publicados pela organização.',
        detectedAt: _ago(const Duration(days: 40)),
        publisher: 'Organização Margens',
      ),
    ],
  );

  late final tempestade = Event(
    id: 'a-tempestade',
    name: 'A Tempestade',
    venue: 'Teatro Municipal',
    city: 'Porto',
    start: _day(22, 21, 30),
    art: const EventArt(Color(0xFF8C4D2E), Color(0xFF26140F)),
    status: EventStatus.postponed,
    organizer: 'Teatro Municipal',
    eventCode: 'TEMP-2026',
    updatedAt: _ago(const Duration(hours: 2)),
    changes: [
      EventChange(
        id: 'tempestade-date',
        kind: ChangeKind.dateChanged,
        title: 'Data alterada',
        description: 'Local sem alterações.',
        detectedAt: _ago(const Duration(hours: 2)),
        publisher: 'Teatro Municipal',
        previousStart: _day(8, 21, 30),
        newStart: _day(22, 21, 30),
        refund: Refund(
          until: _day(14, 23, 59),
          where: 'teatromunicipal.pt/reembolsos',
        ),
      ),
      EventChange(
        id: 'tempestade-published',
        kind: ChangeKind.published,
        title: 'Evento publicado',
        description: 'Data, horário e local publicados pela organização.',
        detectedAt: _ago(const Duration(days: 32)),
        publisher: 'Teatro Municipal',
      ),
    ],
  );

  late final jazzNoParque = Event(
    id: 'jazz-no-parque',
    name: 'Jazz no Parque',
    venue: 'Jardim do Palácio',
    city: 'Porto',
    start: _day(1, 19),
    doorsOpen: _day(1, 18),
    end: _day(1, 23),
    art: const EventArt(Color(0xFF338C80), Color(0xFF0D2E33)),
    organizer: 'Jazz no Parque',
    eventCode: 'JAZZ-2026',
    updatedAt: _ago(const Duration(minutes: 5)),
    warnings: [
      WeatherWarning(
        level: WarningLevel.orange,
        title: 'Chuva forte no Porto',
        detail: 'Das 18:00 às 23:59, no distrito do evento.',
        issuedAt: _day(0, 9, 12),
        until: _day(1, 23, 59),
      ),
    ],
    weather: WeatherInfo(
      summary: '16° · Chuva forte',
      detail: 'Chuva forte prevista a partir das 19h (80%)',
      updatedAt: _ago(const Duration(hours: 1)),
      icon: WeatherIcon.rain,
    ),
  );

  late final orquestra = Event(
    id: 'orquestra-sinfonica',
    name: 'Orquestra Sinfónica',
    venue: 'Auditório Municipal',
    city: 'Porto',
    start: _day(28, 21),
    doorsOpen: _day(28, 20, 30),
    art: const EventArt(Color(0xFF596B9E), Color(0xFF1A1F38)),
    organizer: 'Auditório Municipal',
    eventCode: 'ORQ-2026',
    updatedAt: _ago(const Duration(minutes: 5)),
  );

  /// Registered ticketed event that is not in the wallet yet: the demo
  /// import adds it.
  late final noiteFado = Event(
    id: 'noite-de-fado',
    name: 'Noite de Fado',
    venue: 'Casa do Infante',
    city: 'Porto',
    start: _day(9, 21, 30),
    doorsOpen: _day(9, 21),
    end: _day(9, 23, 30),
    art: const EventArt(Color(0xFF8C4D2E), Color(0xFF26140F)),
    organizer: 'Casa do Infante',
    eventCode: 'FADO-2026',
    updatedAt: _ago(const Duration(hours: 3)),
  );

  late final feiraMedieval = Event(
    id: 'feira-medieval',
    name: 'Feira Medieval de Gaia',
    venue: 'Cais de Gaia',
    city: 'Vila Nova de Gaia',
    start: _day(16, 10),
    art: const EventArt(Color(0xFFE5B24D), Color(0xFF73331A)),
    organizer: 'Câmara de Gaia',
    accessCode: 'FEIRAGAIA',
    updatedAt: _ago(const Duration(days: 1)),
  );

  late final corridaNatal = Event(
    id: 'corrida-natal',
    name: 'Corrida de Natal',
    venue: 'Avenida dos Aliados',
    city: 'Porto',
    start: _day(64, 10),
    art: const EventArt(Color(0xFFE66E50), Color(0xFF5A286E)),
    organizer: 'Porto Desporto',
    accessCode: 'NATAL2026',
    updatedAt: _ago(const Duration(days: 2)),
  );

  /// Public event the participant can join with the code on its posters.
  late final saoJoao = Event(
    id: 'sao-joao',
    name: 'São João na Ribeira',
    venue: 'Cais da Ribeira',
    city: 'Porto',
    start: _day(5, 20),
    end: _day(6, 4),
    art: const EventArt(Color(0xFFE66E50), Color(0xFF5A286E)),
    organizer: 'Associação da Ribeira',
    accessCode: 'SAOJOAO24',
    updatedAt: _ago(const Duration(days: 1)),
  );

  /// Public event the participant left: old notifications open P8's
  /// "Já não estás ligado a este evento".
  late final mercadoOutono = Event(
    id: 'mercado-outono',
    name: 'Mercado de Outono',
    venue: 'Praça da Batalha',
    city: 'Porto',
    start: _day(3, 10),
    end: _day(3, 19),
    art: const EventArt(Color(0xFFE5B24D), Color(0xFF73331A)),
    organizer: 'Mercado de Outono',
    accessCode: 'OUTONO26',
  );

  /// Every event registered by an organization.
  List<Event> get registered => [
    festivalMargens,
    tempestade,
    jazzNoParque,
    orquestra,
    noiteFado,
    feiraMedieval,
    corridaNatal,
    saoJoao,
    mercadoOutono,
  ];

  List<Ticket> get tickets => [
    Ticket(
      id: 't-margens',
      event: festivalMargens,
      origin: TicketOrigin.pdf,
      importedAt: _ago(const Duration(days: 12)),
      gate: 'A',
      seat: 'Geral',
      codeReference: '4821',
    ),
    Ticket(
      id: 't-tempestade',
      event: tempestade,
      origin: TicketOrigin.pdf,
      importedAt: _ago(const Duration(days: 20)),
      gate: '2',
      seat: 'C-07',
      codeReference: '1170',
    ),
    Ticket(
      id: 't-jazz',
      event: jazzNoParque,
      origin: TicketOrigin.image,
      importedAt: _ago(const Duration(days: 5)),
      gate: 'Norte',
      seat: 'Livre',
      codeReference: '5512',
    ),
    Ticket(
      id: 't-orquestra',
      event: orquestra,
      origin: TicketOrigin.pdf,
      importedAt: _ago(const Duration(days: 3)),
      gate: 'B',
      seat: 'F-12',
      codeReference: '0935',
    ),
  ];

  /// Public events joined with their access code.
  Set<String> get publicIds => {feiraMedieval.id, corridaNatal.id};

  /// Events the participant left, with when.
  Map<String, DateTime> get leftEvents => {
    mercadoOutono.id: _ago(const Duration(days: 5)),
  };

  /// Yes/no requests of the organization (P17). The notification center
  /// lists the participant's real notifications from Notifications.
  List<AppNotification> get yesNoRequests => [
    AppNotification(
      id: 'n-request',
      kind: NotificationKind.request,
      eventId: festivalMargens.id,
      eventName: festivalMargens.name,
      typeLabel: 'Pedido urgente',
      title: 'Vais usar o autocarro especial das 23:30?',
      body: 'Responde até às 21h.',
      source: 'Organização Margens',
      receivedAt: _ago(const Duration(hours: 1)),
      route: '/event/${festivalMargens.id}/requests',
      urgent: true,
      responses: 412,
    ),
  ];

  List<ConfirmationRequest> get confirmationRequests => [
    ConfirmationRequest(
      id: 'c-porta-c',
      eventId: festivalMargens.id,
      eventName: festivalMargens.name,
      category: ReportCategory.queue,
      title: 'Fila longa na Porta C',
      detail: WaitTime.from20To40.sentence,
      reportedAt: _ago(const Duration(minutes: 4)),
    ),
  ];

  List<Recognition> get recognitions => [
    Recognition(
      id: 'r-agua',
      eventId: festivalMargens.id,
      organizer: festivalMargens.organizer,
      reportTitle: 'Falta de água no bar principal',
      category: ReportCategory.venueProblem,
      reportedAt: _ago(const Duration(minutes: 43)),
      resolvedAfter: const Duration(minutes: 18),
      resolution: 'Água reposta no bar principal',
      alertReach: 1240,
      alertMessage: 'Água disponível de novo no bar principal',
    ),
  ];

  List<IdeaPrompt> get ideaPrompts => [
    IdeaPrompt(
      id: 'i-proxima-edicao',
      eventId: festivalMargens.id,
      question: 'O que melhorarias na próxima edição?',
      sentAt: _ago(const Duration(days: 2)),
      ideasShared: 23,
    ),
  ];

  /// What the participant reported and confirmed so far (P20).
  List<Contribution> get contributions => [
    Contribution(
      id: 'k-porta-c',
      kind: ContributionKind.report,
      eventId: festivalMargens.id,
      eventName: festivalMargens.name,
      title: 'Fila longa na Porta C',
      status: 'Confirmado por 6 pessoas · agrupado num incidente',
      at: _ago(const Duration(minutes: 40)),
      tone: AppTagTone.warning,
    ),
    Contribution(
      id: 'k-rampa',
      kind: ContributionKind.confirmation,
      eventId: festivalMargens.id,
      eventName: festivalMargens.name,
      title: 'Rampa da entrada Norte bloqueada',
      status: 'Respondeste Sim · confirmado',
      at: _ago(const Duration(hours: 1)),
      tone: AppTagTone.info,
    ),
    Contribution(
      id: 'k-agua',
      kind: ContributionKind.helpful,
      eventId: festivalMargens.id,
      eventName: festivalMargens.name,
      title: 'Falta de água no bar principal',
      status: 'A organização resolveu-o e enviou um alerta a 1 240 pessoas.',
      at: _ago(const Duration(minutes: 25)),
      tone: AppTagTone.success,
      route: '/recognition/r-agua',
    ),
    Contribution(
      id: 'k-palco',
      kind: ContributionKind.report,
      eventId: festivalMargens.id,
      eventName: festivalMargens.name,
      title: 'Lotação alta junto ao palco',
      status: 'Em análise · ainda sem confirmações suficientes',
      at: _ago(const Duration(hours: 2)),
      tone: AppTagTone.danger,
    ),
    Contribution(
      id: 'k-noite-branca',
      kind: ContributionKind.report,
      eventId: 'noite-branca',
      eventName: 'Noite Branca',
      title: 'Entrada lateral fechada',
      status: 'Confirmado por 4 pessoas · resolvido',
      at: _ago(const Duration(days: 9)),
      tone: AppTagTone.info,
    ),
  ];
}
