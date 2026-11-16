import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../ui/ui.dart';

import '../utils/pt_format.dart';
import 'auth_api.dart';
import 'connections_api.dart';
import 'demo_data.dart';
import 'events_api.dart';
import 'password_strength.dart';
import 'models.dart';
import 'notifications_api.dart';
import 'tickets_api.dart';

/// App state for the participant screens. Registration, sign-in and the
/// pages of events Core knows go to Core, and the notifications to
/// Notifications; the wallet and the rest are [DemoData] until their APIs
/// are wired in. Screens read it with `AppStore.of(context)` and rebuild
/// when it changes.
class AppStore extends ChangeNotifier {
  AppStore({
    DateTime Function()? clock,
    AuthApi? auth,
    NotificationsApi? notifications,
    EventsApi? events,
    this._session,
    this.ticketsApi,
    this.connectionsApi,
  }) : _clock = clock ?? DateTime.now,
       _auth = auth ?? HttpAuthApi(),
       _notificationsApi = notifications ?? HttpNotificationsApi(),
       _eventsApi = events ?? HttpEventsApi() {
    final demo = DemoData(now);
    _registered = {for (final e in demo.registered) e.id: e};
    _tickets = demo.tickets;
    _public = demo.publicIds;
    _left = demo.leftEvents;
    _requests = demo.yesNoRequests;
    _preferences = NotificationPreferences()
      ..perEvent[demo.jazzNoParque.id] = EventNotificationLevel.officialOnly;
    _confirmations = demo.confirmationRequests;
    _recognitions = demo.recognitions;
    _ideas = demo.ideaPrompts;
    _contributions = demo.contributions;
    if (_session != null) _startNotifications();
  }

  final DateTime Function() _clock;
  final AuthApi _auth;

  /// Core's ticket import. Without it, ticket import uses demo data; it is
  /// set once the screens use the real sign-in.
  final TicketsApi? ticketsApi;

  /// Core's connections. Without it, access codes use demo data; it is set
  /// once the screens use the real sign-in.
  final ConnectionsApi? connectionsApi;

  late final Map<String, Event> _registered;
  late List<Ticket> _tickets;
  late Set<String> _public;
  late Map<String, DateTime> _left;
  late List<AppNotification> _requests;
  late NotificationPreferences _preferences;
  late List<ConfirmationRequest> _confirmations;
  late List<Recognition> _recognitions;
  late List<IdeaPrompt> _ideas;
  late List<Contribution> _contributions;

  /// The signed-in participant, as the entry code's holder (P19).
  String get userName => _session?.email ?? '';

  DateTime get now => _clock();

  static AppStore of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppStoreScope>()!.notifier!;

  // Account (C0, C1, C3): registration and sign-in in Core. The token
  // stays in memory, so reloading the app signs the participant out.

  AuthSession? _session;
  AuthSession? get session => _session;
  Account? get account =>
      _session == null ? null : Account(email: _session!.email);
  bool get signedIn => _session != null;

  static const minPasswordLength = 8;

  /// Signs in. Returns an error message, or null when signed in.
  Future<String?> signIn({
    required String email,
    required String password,
  }) async {
    if (!_looksLikeEmail(email)) return 'Introduz um email válido.';
    if (password.isEmpty) return 'Introduz a palavra-passe.';
    try {
      final session = await _auth.signIn(
        email: email.trim(),
        password: password,
      );
      if (session.role != 'PARTICIPANT') {
        return 'Esta conta é de uma organização. Entra no backoffice.';
      }
      _session = session;
      _startNotifications();
      notifyListeners();
      return null;
    } on AuthException catch (e) {
      return _authError(e.code);
    }
  }

  /// Creates a participant account and signs in with it. [confirmation] is
  /// the password typed again, so a typo does not lock the participant out.
  /// Returns an error message, or null when signed in.
  Future<String?> register({
    required String email,
    required String password,
    required String confirmation,
  }) async {
    if (!_looksLikeEmail(email)) return 'Introduz um email válido.';
    if (password.length < minPasswordLength) {
      return 'Usa pelo menos $minPasswordLength caracteres na palavra-passe.';
    }
    if (!PasswordStrength.of(password).meetsTheRule) {
      return _weakPassword;
    }
    if (confirmation != password) return 'As palavras-passe não coincidem.';
    try {
      await _auth.register(email: email.trim(), password: password);
    } on AuthException catch (e) {
      return _authError(e.code);
    }
    return signIn(email: email, password: password);
  }

  static const _weakPassword =
      'A palavra-passe não cumpre os requisitos de segurança: deve incluir '
      'uma letra maiúscula, uma letra minúscula, um algarismo e um símbolo.';

  /// The participant's message for a refusal from Core.
  static String _authError(String code) => switch (code) {
    'INVALID_CREDENTIALS' => 'O email ou a palavra-passe estão errados.',
    'EMAIL_ALREADY_REGISTERED' =>
      'Já existe uma conta com este email. Entra com ela.',
    'PASSWORD_TOO_SHORT' =>
      'Usa pelo menos $minPasswordLength caracteres na palavra-passe.',
    'PASSWORD_TOO_LONG' => 'Usa uma palavra-passe mais curta.',
    'PASSWORD_TOO_WEAK' => _weakPassword,
    'RATE_LIMITED' =>
      'Demasiadas tentativas. Tenta novamente daqui a alguns minutos.',
    'PASSWORD_CHANGE_REQUIRED' =>
      'Esta conta é de uma organização. Entra no backoffice.',
    'NETWORK' => 'Não foi possível ligar ao Crowded. Verifica a ligação e tenta novamente.',
    _ => 'Não foi possível entrar agora. Tenta novamente.',
  };

  void signOut() {
    _stopNotifications();
    _forgetCoreEvents();
    _session = null;
    notifyListeners();
  }

  /// Deletes the account and its personal data (C3). Reports stay,
  /// without a link to the account.
  void deleteAccount() {
    _tickets = [];
    _public = {};
    _left = {};
    _stopNotifications();
    _forgetCoreEvents();
    _requests = [];
    _confirmations = [];
    _recognitions = [];
    _contributions = [];
    _preferences = NotificationPreferences();
    pendingImport = null;
    _session = null;
    notifyListeners();
  }

  static bool _looksLikeEmail(String value) =>
      RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(value.trim());

  // Notification preferences (C2).

  NotificationPreferences get preferences => _preferences;

  void updatePreferences(void Function(NotificationPreferences p) change) {
    change(_preferences);
    notifyListeners();
  }

  EventNotificationLevel levelFor(String eventId) =>
      _preferences.perEvent[eventId] ?? EventNotificationLevel.all;

  /// Events with a ticket or joined with an access code, soonest first.
  List<Event> get connectedEvents =>
      [for (final t in _tickets) t.event, ...publicEvents]
        ..sort((a, b) => a.start.compareTo(b.start));

  /// Core answers an event's page only to a connected participant.
  bool isConnected(String eventId) =>
      ticketFor(eventId) != null ||
      _public.contains(eventId) ||
      _fromCore.containsKey(eventId);

  /// Connected events taking place today, the one in progress (or starting
  /// next) first: the events a participant can report about (P11).
  List<Event> get eventsToday {
    final today = connectedEvents.where(
      (e) =>
          isSameDay(e.start, now) ||
          (e.end != null && e.start.isBefore(now) && e.end!.isAfter(now)),
    );
    bool inProgress(Event e) =>
        !e.start.isAfter(now) && (e.end == null || e.end!.isAfter(now));
    return [...today.where(inProgress), ...today.where((e) => !inProgress(e))];
  }

  /// For callbacks: reads the store without rebuilding on changes.
  static AppStore read(BuildContext context) =>
      context.getInheritedWidgetOfExactType<AppStoreScope>()!.notifier!;

  // Connectivity (demo toggle until real connectivity checks exist).

  DateTime? _offlineSince;
  bool get offline => _offlineSince != null;

  /// When the data on screen was last saved, while offline.
  DateTime? get savedAt => _offlineSince;

  void setOffline(bool value) {
    _offlineSince = value ? now : null;
    notifyListeners();
  }

  // Location (P10). Asked once; the distance check is a demo toggle until
  // the device location is used.

  bool locationAllowed = false;

  void allowLocation() {
    locationAllowed = true;
    notifyListeners();
  }

  bool _awayFromVenue = false;
  bool get awayFromVenue => _awayFromVenue;

  void setAwayFromVenue(bool value) {
    _awayFromVenue = value;
    notifyListeners();
  }

  /// Within 500 m of the venue, as required to report (P12).
  bool isNearVenue(Event event) => !_awayFromVenue;

  // Events and tickets.

  /// A registered event, as Core last sent it when it knows it. Pages of
  /// events the participant is not connected to are not shown: use
  /// [isConnected].
  Event? eventById(String id) => _fromCore[id] ?? _registered[id];

  // Events from Core (P5, P6, P7): each page loads the event and its history
  // when it opens, and follows its live stream while it is open (ADR 0007).
  // What loaded last stays on screen when a later load fails or the stream
  // drops, with the time it was loaded. Demo events, which Core does not
  // know, are not asked for.

  final EventsApi _eventsApi;
  final Map<String, Event> _fromCore = {};
  final Map<String, EventSync> _syncs = {};
  final Map<String, List<CoreChange>> _histories = {};
  final Map<String, _EventFollow> _follows = {};

  static final _coreId = RegExp(
    r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
    caseSensitive: false,
  );

  /// How the last load of [eventId] from Core went: loading until its first
  /// answer, and null for a demo event.
  EventSync? syncOf(String eventId) =>
      _syncs[eventId] ??
      (signedIn && _coreId.hasMatch(eventId)
          ? const EventSync(loading: true)
          : null);

  /// Whether [eventId]'s page shows saved data: the device is offline, or
  /// its last load from Core failed after an earlier one worked.
  bool showsSavedData(String eventId) =>
      offline || (_syncs[eventId]?.showsSavedData ?? false);

  /// When the data shown for [eventId] was saved, while [showsSavedData].
  DateTime? savedAtFor(String eventId) =>
      offline ? savedAt : _syncs[eventId]?.syncedAt;

  /// Loads [eventId]'s page and history from Core, unless it is a demo
  /// event or a load is already on its way.
  Future<void> refreshEvent(String eventId) async {
    final token = _session?.accessToken;
    final previous = _syncs[eventId];
    if (token == null ||
        !_coreId.hasMatch(eventId) ||
        (previous?.loading ?? false)) {
      return;
    }
    // A retry keeps the last failure, so saved data stays labelled as saved.
    _syncs[eventId] = EventSync(
      syncedAt: previous?.syncedAt,
      loading: true,
      failure: previous?.failure,
    );
    notifyListeners();
    try {
      final page = await _eventsApi.page(token, eventId);
      final history = await _eventsApi.changes(token, eventId);
      if (_session?.accessToken != token) return;
      _keep(eventId, page, history);
    } on EventsException catch (e) {
      if (_session?.accessToken != token) return;
      _failed(eventId, e.code, noLongerVisible: e.noLongerVisible);
    }
    notifyListeners();
  }

  void _keep(String eventId, EventPage page, List<CoreChange> history) {
    _fromCore[eventId] = eventFromCore(page, history);
    _histories[eventId] = history;
    _syncs[eventId] = EventSync(syncedAt: now);
  }

  /// Records a failed load or a dropped stream; [loading] keeps a load on
  /// its way.
  void _failed(
    String eventId,
    String code, {
    required bool noLongerVisible,
    bool loading = false,
  }) {
    final previous = _syncs[eventId];
    if (noLongerVisible) {
      _fromCore.remove(eventId);
      _histories.remove(eventId);
    }
    _syncs[eventId] = EventSync(
      syncedAt: noLongerVisible ? null : previous?.syncedAt,
      loading: loading,
      failure: code,
    );
  }

  /// Follows [eventId]'s live stream while a page shows it; each call needs
  /// an [unfollowEvent] when that page closes.
  void followEvent(String eventId) {
    final follow = _follows[eventId];
    if (follow != null) {
      follow.pages++;
      return;
    }
    if (!signedIn || !_coreId.hasMatch(eventId)) return;
    _follows[eventId] = _EventFollow();
    _connectEvent(eventId);
  }

  /// Stops following [eventId] once no page shows it.
  void unfollowEvent(String eventId) {
    final follow = _follows[eventId];
    if (follow == null || --follow.pages > 0) return;
    follow.close();
    _follows.remove(eventId);
  }

  void _connectEvent(String eventId) {
    final follow = _follows[eventId];
    final token = _session?.accessToken;
    if (follow == null || token == null) return;
    follow.retry = null;
    follow.stream = _eventsApi
        .live(token, eventId)
        .listen(
          (page) => _onEventPage(eventId, token, page),
          onError: (Object error) => _onEventStreamEnded(eventId, error),
          onDone: () => _onEventStreamEnded(eventId, null),
          cancelOnError: true,
        );
  }

  /// A page from the stream. It names only the latest change, so the
  /// history is loaded again when that is a new one.
  Future<void> _onEventPage(
    String eventId,
    String token,
    EventPage page,
  ) async {
    final follow = _follows[eventId];
    if (follow == null) return;
    follow.failures = 0;
    follow.latest = page;
    var history = _histories[eventId];
    if (history == null || history.firstOrNull?.id != page.latestChange?.id) {
      try {
        history = await _eventsApi.changes(token, eventId);
      } on EventsException catch (e) {
        if (_session?.accessToken != token) return;
        _failed(eventId, e.code, noLongerVisible: e.noLongerVisible);
        notifyListeners();
        return;
      }
      if (_session?.accessToken != token) return;
    }
    // A later page may have come while the history was loading.
    _keep(eventId, _follows[eventId]?.latest ?? page, history);
    notifyListeners();
  }

  void _onEventStreamEnded(String eventId, Object? error) {
    final follow = _follows[eventId];
    if (follow == null) return;
    follow.stream = null;
    if (!signedIn) return;
    final code = error is EventsException ? error.code : 'STREAM_ENDED';
    // A refused token ends the stream: signing in again opens the page anew.
    if (code == 'UNAUTHORIZED') return;
    final gone = error is EventsException && error.noLongerVisible;
    _failed(
      eventId,
      code,
      noLongerVisible: gone,
      loading: _syncs[eventId]?.loading ?? false,
    );
    notifyListeners();
    if (gone) return;
    follow.failures++;
    follow.retry = Timer(
      reconnectDelay(follow.failures),
      () => _connectEvent(eventId),
    );
  }

  void _forgetCoreEvents() {
    for (final follow in _follows.values) {
      follow.close();
    }
    _follows.clear();
    _fromCore.clear();
    _syncs.clear();
    _histories.clear();
  }

  /// Wallet order: soonest first.
  List<Ticket> get tickets =>
      [..._tickets]..sort((a, b) => a.event.start.compareTo(b.event.start));

  Ticket? ticketFor(String eventId) =>
      _tickets.where((t) => t.event.id == eventId).firstOrNull;

  int get changedTicketCount =>
      _tickets.where((t) => t.event.status != EventStatus.scheduled).length;

  /// Public events joined with an access code, soonest first ("Eventos
  /// públicos" in P1).
  List<Event> get publicEvents =>
      _public.map(eventById).whereType<Event>().toList()
        ..sort((a, b) => a.start.compareTo(b.start));

  /// When the participant left [eventId], if they did (P8).
  DateTime? leftAt(String eventId) => _left[eventId];

  /// Connects to a public event with its access code, typed or read from
  /// its QR code (P21). With [connectionsApi], Core decides the event from
  /// the code. Returns the event's id, or null when no public event has that
  /// code.
  ///
  /// Throws [ConnectionsApiException] when Core refuses it for another
  /// reason or cannot be reached.
  Future<String?> joinWithAccessCode(String code) async {
    final String eventId;
    final api = connectionsApi;
    if (api == null) {
      final normalized = code.trim().toUpperCase();
      final event = _registered.values
          .where((e) => e.accessCode == normalized)
          .firstOrNull;
      if (event == null) return null;
      eventId = event.id;
    } else {
      try {
        eventId = (await api.joinWithAccessCode(code.trim())).eventId;
      } on ConnectionsApiException catch (e) {
        if (e.notFound) return null;
        rethrow;
      }
    }
    _public.add(eventId);
    _left.remove(eventId);
    notifyListeners();
    return eventId;
  }

  /// Removes a ticket or leaves a public event: its page is no
  /// longer shown.
  void leaveEvent(String eventId) {
    _tickets.removeWhere((t) => t.event.id == eventId);
    _public.remove(eventId);
    _left[eventId] = now;
    notifyListeners();
  }

  // Notifications (P8): the participant's notifications in Notifications,
  // kept current by its live stream while signed in (ADR 0007). After the
  // stream drops, the app reconnects and fetches the list again, so what
  // was created meanwhile appears once.

  final NotificationsApi _notificationsApi;
  List<AppNotification> _notifications = [];
  final Set<String> _readPending = {};
  final Set<String> _receivedLive = {};
  StreamSubscription<LiveEvent>? _live;
  Timer? _reconnect;
  int _failures = 0;
  bool _notificationsFailed = false;
  AppNotification? _banner;

  /// Newest first.
  List<AppNotification> get notifications =>
      [..._notifications]..sort((a, b) => b.receivedAt.compareTo(a.receivedAt));

  int get unreadCount => _notifications.where((n) => !n.read).length;

  /// Whether the last attempt to load the notifications failed.
  bool get notificationsFailed => _notificationsFailed;

  /// The newest notification received live, shown as a banner until it is
  /// opened or dismissed.
  AppNotification? get banner => _banner;

  void dismissBanner() {
    if (_banner == null) return;
    _banner = null;
    notifyListeners();
  }

  /// How long to wait before reconnecting after [failures] drops in a row.
  static Duration reconnectDelay(int failures) =>
      Duration(seconds: math.min(30, 5 * math.max(1, failures)));

  void _startNotifications() {
    _failures = 0;
    refreshNotifications();
    _connect();
  }

  void _stopNotifications() {
    _reconnect?.cancel();
    _reconnect = null;
    _live?.cancel();
    _live = null;
    _notifications = [];
    _readPending.clear();
    _receivedLive.clear();
    _notificationsFailed = false;
    _banner = null;
  }

  void _connect() {
    final token = _session?.accessToken;
    if (token == null) return;
    _live?.cancel();
    _live = _notificationsApi
        .live(token)
        .listen(
          _onLive,
          onError: _onLiveEnded,
          onDone: () => _onLiveEnded(null),
          cancelOnError: true,
        );
  }

  void _onLive(LiveEvent event) {
    switch (event) {
      case LiveOpened():
        // Anything created while the stream was closed is in the list.
        _failures = 0;
        refreshNotifications();
      case LiveNotificationEvent(:final notification):
        _receive(notification);
    }
  }

  void _onLiveEnded(Object? error) {
    _live = null;
    if (!signedIn) return;
    // A refused token ends the session's stream: signing in again opens it.
    if (error is NotificationsException && error.code == 'UNAUTHORIZED') {
      return;
    }
    _failures++;
    _reconnect?.cancel();
    _reconnect = Timer(reconnectDelay(_failures), _connect);
  }

  /// A notification from the live stream: added and shown as a banner,
  /// unless it is already listed.
  void _receive(AppNotification notification) {
    if (_notifications.any((n) => n.id == notification.id)) return;
    _receivedLive.add(notification.id);
    _notifications = [..._notifications, notification];
    _banner = notification;
    notifyListeners();
  }

  /// Loads the notification center again, keeping the ones marked read
  /// and the ones received live while the request was on its way.
  Future<void> refreshNotifications() async {
    final token = _session?.accessToken;
    if (token == null) return;
    try {
      final page = await _notificationsApi.list(token);
      if (_session?.accessToken != token) return;
      final listed = {for (final n in page.notifications) n.id};
      for (final n in page.notifications) {
        if (_readPending.contains(n.id)) n.read = true;
      }
      _notifications = [
        ...page.notifications,
        ..._notifications.where(
          (n) => _receivedLive.contains(n.id) && !listed.contains(n.id),
        ),
      ];
      _notificationsFailed = false;
    } on NotificationsException {
      if (_session?.accessToken != token) return;
      _notificationsFailed = true;
    }
    notifyListeners();
  }

  /// Marks [notification] read, here at once and then in Notifications. If
  /// that fails, the next refresh shows it unread again.
  void markRead(AppNotification notification) {
    if (_banner?.id == notification.id) _banner = null;
    // The banner and the list may hold different copies after a refresh.
    final listed = _notifications
        .where((n) => n.id == notification.id)
        .firstOrNull;
    final wasRead = notification.read && (listed?.read ?? true);
    notification.read = true;
    listed?.read = true;
    notifyListeners();
    final token = _session?.accessToken;
    if (wasRead || listed == null || token == null) return;
    _readPending.add(notification.id);
    _notificationsApi
        .markRead(token, notification.id)
        .catchError((Object _) {})
        .whenComplete(() => _readPending.remove(notification.id));
  }

  /// Answers a yes/no request of the organization (P17).
  void answerRequest(AppNotification notification, bool answer) {
    notification
      ..answer = answer
      ..read = true;
    notifyListeners();
  }

  @override
  void dispose() {
    _reconnect?.cancel();
    _live?.cancel();
    for (final follow in _follows.values) {
      follow.close();
    }
    super.dispose();
  }

  // Reports, confirmations and contributions (P11–P15, P20).

  ParticipantReport? lastReport;

  ParticipantReport sendReport({
    required Event event,
    required ReportCategory category,
    required VenuePlace place,
    WaitTime? waitTime,
    CrowdLevel? crowdLevel,
    String description = '',
  }) {
    final report = ParticipantReport(
      id: 'report-${now.millisecondsSinceEpoch}',
      eventId: event.id,
      category: category,
      place: place,
      sentAt: now,
      waitTime: waitTime,
      crowdLevel: crowdLevel,
      description: description.trim(),
    );
    lastReport = report;
    _contributions.add(
      Contribution(
        id: report.id,
        kind: ContributionKind.report,
        eventId: event.id,
        eventName: event.name,
        title: report.title,
        status: 'Enviado · a pedir confirmação a quem está perto',
        at: now,
        tone: AppTagTone.warning,
      ),
    );
    notifyListeners();
    return report;
  }

  ConfirmationRequest? confirmationRequest(String id) =>
      _confirmations.where((c) => c.id == id).firstOrNull;

  void answerConfirmation(
    ConfirmationRequest request,
    ConfirmationAnswer answer,
  ) {
    request.answer = answer;
    if (answer != ConfirmationAnswer.cantSee) {
      _contributions.add(
        Contribution(
          id: 'confirmation-${request.id}',
          kind: ContributionKind.confirmation,
          eventId: request.eventId,
          eventName: request.eventName,
          title: request.title,
          status: answer == ConfirmationAnswer.yes
              ? 'Respondeste Sim'
              : 'Respondeste Não',
          at: now,
          tone: AppTagTone.info,
        ),
      );
    }
    notifyListeners();
  }

  Recognition? recognition(String id) =>
      _recognitions.where((r) => r.id == id).firstOrNull;

  /// Newest first.
  List<Contribution> get contributions =>
      [..._contributions]..sort((a, b) => b.at.compareTo(a.at));

  // Organizer requests (P17).

  /// Yes/no requests sent to the participants of [eventId].
  List<AppNotification> yesNoRequestsFor(String eventId) =>
      _requests.where((n) => n.eventId == eventId).toList();

  List<IdeaPrompt> ideasFor(String eventId) =>
      _ideas.where((i) => i.eventId == eventId).toList();

  void shareIdea(IdeaPrompt prompt, String idea) {
    if (idea.trim().isEmpty) return;
    prompt
      ..shared = true
      ..ideasShared += 1;
    notifyListeners();
  }

  // Ticket import (P2 → P2a / P2b). With [ticketsApi], a PDF is sent to
  // Core; without it, files are "read" with demo data: a PDF matches a
  // registered event, an image gives a misread event code.

  /// The file being imported when its event code has to be typed (P2b).
  TicketImport? pendingImport;

  /// The ticket added by the last import (P2a).
  Ticket? lastImported;

  static const _demoCode = 'FADO-2026';

  /// Imports a ticket PDF.
  ///
  /// Throws [TicketsApiException] when Core refuses it or cannot be reached.
  Future<ImportOutcome> importPdf(Uint8List bytes, String fileName) async {
    final api = ticketsApi;
    if (api == null) return _addTicket(_demoCode, TicketOrigin.pdf);
    final result = await api.uploadPdf(bytes, fileName);
    switch (result) {
      case TicketWithoutQrCode():
        pendingImport = null;
        notifyListeners();
        return ImportOutcome.noQrCode;
      case TicketNeedsEventCode(:final importId):
        pendingImport = TicketImport(
          fileName: fileName,
          origin: TicketOrigin.pdf,
          importId: importId,
        );
        notifyListeners();
        return ImportOutcome.needsEventCode;
    }
  }

  /// Imports a screenshot or photo of a ticket, with demo data.
  ImportOutcome importImage() {
    pendingImport = const TicketImport(
      fileName: 'bilhete-fado.jpg',
      origin: TicketOrigin.image,
      readCode: 'FADO-2O26',
    );
    notifyListeners();
    return ImportOutcome.needsEventCode;
  }

  /// Completes [pendingImport] with the event code printed on the ticket.
  ImportOutcome submitEventCode(String code) {
    final origin = pendingImport?.origin ?? TicketOrigin.pdf;
    final outcome = _addTicket(code, origin);
    if (outcome != ImportOutcome.added) notifyListeners();
    return outcome;
  }

  ImportOutcome _addTicket(String code, TicketOrigin origin) {
    final normalized = code.trim().toUpperCase();
    final event = _registered.values
        .where((e) => e.eventCode == normalized)
        .firstOrNull;
    if (event == null) {
      pendingImport = null;
      return ImportOutcome.eventNotOnCrowded;
    }
    final existing = ticketFor(event.id);
    if (existing != null) {
      // Importing the same ticket again changes nothing.
      lastImported = existing;
      pendingImport = null;
      notifyListeners();
      return ImportOutcome.added;
    }
    final ticket = Ticket(
      id: 't-${event.id}-${now.millisecondsSinceEpoch}',
      event: event,
      origin: origin,
      importedAt: now,
      gate: 'Principal',
      seat: 'Plateia D-14',
      codeReference: '7730',
    );
    _tickets.add(ticket);
    _left.remove(event.id);
    lastImported = ticket;
    pendingImport = null;
    notifyListeners();
    return ImportOutcome.added;
  }

  /// "Não é o teu evento? Remover" in P2a.
  void undoImport() {
    final ticket = lastImported;
    if (ticket == null) return;
    _tickets.remove(ticket);
    lastImported = null;
    notifyListeners();
  }
}

/// Makes the [AppStore] available to the widget tree.
class AppStoreScope extends InheritedNotifier<AppStore> {
  const AppStoreScope({
    super.key,
    required AppStore store,
    required super.child,
  }) : super(notifier: store);
}

/// An event's live stream, shared by the pages that show it.
class _EventFollow {
  int pages = 1;
  StreamSubscription<EventPage>? stream;
  Timer? retry;
  int failures = 0;

  /// The newest page the stream sent.
  EventPage? latest;

  void close() {
    retry?.cancel();
    retry = null;
    stream?.cancel();
    stream = null;
  }
}
