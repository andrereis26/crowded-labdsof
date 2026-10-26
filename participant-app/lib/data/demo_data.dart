import 'package:flutter/painting.dart';

import '../ui/ui.dart';
import '../utils/pt_format.dart';
import 'models.dart';

/// Demo content that mirrors the mockups, with dates relative to [now] so
/// "HOJE" is always today. Replaced by the Core API once it exists.
class DemoData {
  DemoData(this.now);

  final DateTime now;

  DateTime _day(int offset, int hour, [int minute = 0]) =>
      DateTime(now.year, now.month, now.day + offset, hour, minute);

  DateTime _ago(Duration d) => now.subtract(d);

  /// Next Saturday (today when today is Saturday).
  int get _daysToSaturday => (DateTime.saturday - now.weekday) % 7;

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
    distanceKm: 5.4,
    sourceUpdatedAt: _ago(const Duration(minutes: 5)),
    alerts: [
      EventAlert(
        message: 'Porta B fechada. Use a Porta C.',
        publisher: 'Organização Margens',
        sentAt: _ago(const Duration(minutes: 3)),
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
      updatedAt: _ago(const Duration(minutes: 1)),
      options: const [
        TransportOption(
          mode: TransportMode.metro,
          title: 'Metro + 8 min a pé',
          detail: 'Próximo às 21:12 · depois às 21:24',
          minutes: 26,
        ),
        TransportOption(
          mode: TransportMode.bus,
          title: 'Autocarro 502',
          detail: 'Desvio na Av. da Boavista · próximo às 21:05',
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
        description: 'Bilhetes à venda na bilheteira oficial.',
        detectedAt: _ago(const Duration(days: 40)),
        source: ChangeSource.official,
        publisher: 'Bilheteira oficial',
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
    sourceUpdatedAt: _ago(const Duration(minutes: 4)),
    changes: [
      EventChange(
        id: 'tempestade-date',
        kind: ChangeKind.dateChanged,
        title: 'Data alterada',
        description: 'Local sem alterações.',
        detectedAt: _ago(const Duration(hours: 2)),
        source: ChangeSource.official,
        publisher: 'Bilheteira oficial',
        previousStart: _day(8, 21, 30),
        newStart: _day(22, 21, 30),
      ),
      EventChange(
        id: 'tempestade-venue',
        kind: ChangeKind.venueConfirmed,
        title: 'Sala confirmada',
        description: 'Sala principal do Teatro Municipal.',
        detectedAt: _ago(const Duration(days: 15)),
        source: ChangeSource.organization,
        publisher: 'Teatro Municipal',
      ),
      EventChange(
        id: 'tempestade-published',
        kind: ChangeKind.published,
        title: 'Evento publicado',
        description: 'Bilhetes à venda na bilheteira oficial.',
        detectedAt: _ago(const Duration(days: 32)),
        source: ChangeSource.official,
        publisher: 'Bilheteira oficial',
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
    sourceUpdatedAt: _ago(const Duration(minutes: 5)),
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
    sourceUpdatedAt: _ago(const Duration(minutes: 5)),
  );

  late final margensAfterparty = Event(
    id: 'margens-afterparty',
    name: 'Margens Afterparty',
    venue: 'Parque da Cidade',
    city: 'Porto',
    start: _day(1, 1),
    art: const EventArt(Color(0xFF403380), Color(0xFF0D0D26)),
  );

  late final feiraMedieval = Event(
    id: 'feira-medieval',
    name: 'Feira Medieval de Gaia',
    venue: 'Cais de Gaia',
    city: 'Vila Nova de Gaia',
    start: _day(16, 10),
    art: const EventArt(Color(0xFFE5B24D), Color(0xFF73331A)),
    distanceKm: 3.2,
  );

  late final corridaNatal = Event(
    id: 'corrida-natal',
    name: 'Corrida de Natal',
    venue: 'Avenida dos Aliados',
    city: 'Porto',
    start: _day(64, 10),
    art: const EventArt(Color(0xFFE66E50), Color(0xFF5A286E)),
    distanceKm: 0.6,
  );

  late final mercadoOutono = Event(
    id: 'mercado-outono',
    name: 'Mercado de Outono',
    venue: 'Praça da Batalha',
    city: 'Porto',
    start: _day(0, 10),
    end: _day(0, 19),
    art: const EventArt(Color(0xFFE5B24D), Color(0xFF73331A)),
    distanceKm: 0.8,
  );

  late final concertoOrgao = Event(
    id: 'concerto-orgao',
    name: 'Concerto de Órgão',
    venue: 'Sé do Porto',
    city: 'Porto',
    start: _day(0, 18, 30),
    art: const EventArt(Color(0xFF596B9E), Color(0xFF1A1F38)),
    distanceKm: 1.1,
  );

  late final cinemaArLivre = Event(
    id: 'cinema-ar-livre',
    name: 'Cinema ao ar livre',
    venue: "Jardim de Arca d'Água",
    city: 'Porto',
    start: _day(0, 21),
    art: const EventArt(Color(0xFF403380), Color(0xFF0D0D26)),
    distanceKm: 2.3,
  );

  late final feiraLivro = Event(
    id: 'feira-livro',
    name: 'Feira do Livro',
    venue: 'Jardins do Palácio de Cristal',
    city: 'Porto',
    start: _day(_daysToSaturday, 11),
    end: _day(_daysToSaturday, 20),
    art: const EventArt(Color(0xFF338C80), Color(0xFF0D2E33)),
    distanceKm: 1.9,
  );

  late final casaMusica = Event(
    id: 'casa-da-musica',
    name: 'Orquestra Jovem',
    venue: 'Casa da Música',
    city: 'Porto',
    start: _day(_daysToSaturday + 1, 18),
    art: const EventArt(Color(0xFF8C4D2E), Color(0xFF26140F)),
    distanceKm: 2.7,
  );

  List<Event> get catalog => [
    festivalMargens,
    tempestade,
    jazzNoParque,
    orquestra,
    margensAfterparty,
    feiraMedieval,
    corridaNatal,
    mercadoOutono,
    concertoOrgao,
    cinemaArLivre,
    feiraLivro,
    casaMusica,
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
      origin: TicketOrigin.email,
      importedAt: _ago(const Duration(days: 5)),
      gate: 'Norte',
      seat: 'Livre',
      hasEntryCode: false,
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

  Set<String> get followedIds => {
    feiraMedieval.id,
    corridaNatal.id,
    cinemaArLivre.id,
  };

  List<AppNotification> get notifications => [
    AppNotification(
      id: 'n-alert',
      kind: NotificationKind.alert,
      eventId: festivalMargens.id,
      eventName: festivalMargens.name,
      typeLabel: 'Alerta da organização',
      title: 'Porta B fechada. Use a Porta C.',
      source: 'Organização Margens',
      receivedAt: _ago(const Duration(minutes: 3)),
    ),
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
      route: '/evento/${festivalMargens.id}/pedidos',
      urgent: true,
      responses: 412,
    ),
    AppNotification(
      id: 'n-confirm',
      kind: NotificationKind.confirmation,
      eventId: festivalMargens.id,
      eventName: festivalMargens.name,
      typeLabel: 'Pedido de confirmação',
      title: 'Fila longa na Porta C. Confirmas?',
      body: 'Um participante perto de ti reportou-a há 4 min.',
      receivedAt: _ago(const Duration(minutes: 4)),
      route: '/evento/${festivalMargens.id}?confirmar=c-porta-c',
      read: true,
    ),
    AppNotification(
      id: 'n-recognition',
      kind: NotificationKind.recognition,
      eventId: festivalMargens.id,
      eventName: festivalMargens.name,
      typeLabel: 'Reconhecimento',
      title: 'O teu reporte ajudou',
      body: 'A organização resolveu a falta de água no bar principal.',
      receivedAt: _ago(const Duration(minutes: 25)),
      route: '/reconhecimento/r-agua',
      read: true,
    ),
    AppNotification(
      id: 'n-poll',
      kind: NotificationKind.poll,
      eventId: festivalMargens.id,
      eventName: 'Margens Inverno',
      typeLabel: 'Sondagem',
      title: 'Ajuda-nos a preparar o Margens Inverno',
      body: '2 perguntas · menos de 1 minuto',
      source: 'Organização Margens',
      receivedAt: _ago(const Duration(days: 1)),
      route: '/sondagem/p-inverno',
      read: true,
    ),
    AppNotification(
      id: 'n-change',
      kind: NotificationKind.officialChange,
      eventId: tempestade.id,
      eventName: tempestade.name,
      typeLabel: 'Alteração oficial',
      title:
          'Adiado para ${formatWeekdayDate(tempestade.start)} · '
          '${formatTime(tempestade.start)}',
      body: 'Era ${formatWeekdayDate(_day(8, 21, 30))}.',
      source: 'Fonte: bilheteira oficial',
      receivedAt: _ago(const Duration(hours: 2)),
      changeId: 'tempestade-date',
      read: true,
    ),
    AppNotification(
      id: 'n-weather',
      kind: NotificationKind.weather,
      eventId: jazzNoParque.id,
      eventName: jazzNoParque.name,
      typeLabel: 'Meteorologia',
      title: 'Chuva forte prevista para amanhã às 19h',
      body: 'Leva impermeável.',
      source: 'Fonte: IPMA',
      receivedAt: _ago(const Duration(days: 2)),
      read: true,
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

  List<Poll> get polls {
    final date = _day(56, 20);
    return [
      Poll(
        id: 'p-inverno',
        eventId: festivalMargens.id,
        organizer: festivalMargens.organizer,
        organizerInitials: 'OM',
        proposedEvent: 'Margens Inverno',
        date: date,
        venue: 'Pavilhão Municipal',
        closesAt: date.subtract(const Duration(days: 2)),
        title: 'Ajuda-nos a preparar o Margens Inverno',
        questions: [
          PollQuestion(
            'Vais ao Margens Inverno a ${formatLongDate(date)}?',
            const ['Sim, vou', 'Talvez', 'Não vou'],
          ),
          const PollQuestion('A que horas pensas chegar?', [
            'Antes das 20h',
            'Entre as 20h e as 22h',
            'Depois das 22h',
          ]),
        ],
        sentAt: _ago(const Duration(days: 1)),
        responses: 486,
      ),
    ];
  }

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
      route: '/reconhecimento/r-agua',
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

  /// What the demo "extracts" from a ticket file.
  TicketDraft extractedDraft(TicketOrigin origin) => TicketDraft(
    origin: origin,
    eventName: 'Festival Margens 2026',
    date: _day(0, 0),
    gate: 'A',
    doorsOpen: '20:00',
    start: '22:00',
    place: 'Parque da Cidade, Porto',
    seat: 'Geral',
    hasEntryCode: true,
  );
}
