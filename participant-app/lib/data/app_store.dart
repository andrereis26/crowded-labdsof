import 'package:flutter/widgets.dart';

import '../ui/ui.dart';

import '../utils/pt_format.dart';
import 'demo_data.dart';
import 'models.dart';

/// App state for the participant screens, backed by [DemoData] until the
/// Core and Notifications APIs exist. Screens read it with
/// `AppStore.of(context)` and rebuild when it changes.
class AppStore extends ChangeNotifier {
  AppStore({DateTime Function()? clock}) : _clock = clock ?? DateTime.now {
    final demo = DemoData(now);
    _registered = {for (final e in demo.registered) e.id: e};
    _tickets = demo.tickets;
    _public = demo.publicIds;
    _left = demo.leftEvents;
    _notifications = demo.notifications;
    _preferences = NotificationPreferences()
      ..perEvent[demo.jazzNoParque.id] = EventNotificationLevel.officialOnly;
    _confirmations = demo.confirmationRequests;
    _recognitions = demo.recognitions;
    _ideas = demo.ideaPrompts;
    _contributions = demo.contributions;
  }

  final DateTime Function() _clock;
  late final Map<String, Event> _registered;
  late List<Ticket> _tickets;
  late Set<String> _public;
  late Map<String, DateTime> _left;
  late List<AppNotification> _notifications;
  late NotificationPreferences _preferences;
  late List<ConfirmationRequest> _confirmations;
  late List<Recognition> _recognitions;
  late List<IdeaPrompt> _ideas;
  late List<Contribution> _contributions;

  /// Name of the signed-in participant (entry code holder in P19).
  String get userName => _account?.name ?? '';

  DateTime get now => _clock();

  static AppStore of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppStoreScope>()!.notifier!;

  // Account (C0, C1, C3). Sign-in is a demo until Core authentication exists.

  Account? _account = const Account(
    name: 'Inês Martins',
    email: 'ines.martins@email.pt',
  );
  Account? get account => _account;
  bool get signedIn => _account != null;

  static const minPasswordLength = 8;

  /// Returns an error message, or null when signed in.
  String? signIn({required String email, required String password}) {
    if (!_looksLikeEmail(email)) return 'Introduz um email válido.';
    if (password.length < minPasswordLength) {
      return 'A palavra-passe tem pelo menos $minPasswordLength caracteres.';
    }
    final name = _account?.name ?? email.split('@').first;
    _account = Account(name: name, email: email.trim());
    notifyListeners();
    return null;
  }

  /// Returns an error message, or null when the account was created.
  String? register({
    required String name,
    required String email,
    required String password,
  }) {
    if (name.trim().isEmpty) return 'Indica o teu nome.';
    if (!_looksLikeEmail(email)) return 'Introduz um email válido.';
    if (password.length < minPasswordLength) {
      return 'Usa pelo menos $minPasswordLength caracteres na palavra-passe.';
    }
    _account = Account(name: name.trim(), email: email.trim());
    notifyListeners();
    return null;
  }

  void signOut() {
    _account = null;
    notifyListeners();
  }

  /// Deletes the account and its personal data (C3, FR-071). Reports stay,
  /// without a link to the account.
  void deleteAccount() {
    _tickets = [];
    _public = {};
    _left = {};
    _notifications = [];
    _confirmations = [];
    _recognitions = [];
    _contributions = [];
    _preferences = NotificationPreferences();
    pendingImport = null;
    _account = null;
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

  bool isConnected(String eventId) =>
      ticketFor(eventId) != null || _public.contains(eventId);

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

  /// A registered event. Pages of events the participant is not connected
  /// to are not shown (SR-020): use [isConnected].
  Event? eventById(String id) => _registered[id];

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

  /// Connects to a public event with its access code (P21, FR-085).
  /// Returns the event, or null when no public event has that code.
  Event? joinWithAccessCode(String code) {
    final normalized = code.trim().toUpperCase();
    final event = _registered.values
        .where((e) => e.accessCode == normalized)
        .firstOrNull;
    if (event == null) return null;
    _public.add(event.id);
    _left.remove(event.id);
    notifyListeners();
    return event;
  }

  /// Removes a ticket or leaves a public event (FR-008): its page is no
  /// longer shown.
  void leaveEvent(String eventId) {
    _tickets.removeWhere((t) => t.event.id == eventId);
    _public.remove(eventId);
    _left[eventId] = now;
    notifyListeners();
  }

  // Notifications.

  List<AppNotification> get notifications =>
      [..._notifications]..sort((a, b) => b.receivedAt.compareTo(a.receivedAt));

  int get unreadCount => _notifications.where((n) => !n.read).length;

  void markRead(AppNotification notification) {
    if (notification.read) return;
    notification.read = true;
    notifyListeners();
  }

  void answerRequest(AppNotification notification, bool answer) {
    notification
      ..answer = answer
      ..read = true;
    notifyListeners();
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
  List<AppNotification> yesNoRequestsFor(String eventId) => notifications
      .where((n) => n.eventId == eventId && n.kind == NotificationKind.request)
      .toList();

  List<IdeaPrompt> ideasFor(String eventId) =>
      _ideas.where((i) => i.eventId == eventId).toList();

  void shareIdea(IdeaPrompt prompt, String idea) {
    if (idea.trim().isEmpty) return;
    prompt
      ..shared = true
      ..ideasShared += 1;
    notifyListeners();
  }

  // Ticket import (P2 → P2a / P2b). Files are "read" with demo data: a PDF
  // matches a registered event, an image gives a misread event code.

  /// The file being imported when its event code has to be typed (P2b).
  TicketImport? pendingImport;

  /// The ticket added by the last import (P2a).
  Ticket? lastImported;

  static const _demoCode = 'FADO-2026';

  ImportOutcome importFile(TicketOrigin origin) {
    if (origin == TicketOrigin.image) {
      pendingImport = TicketImport(
        fileName: 'bilhete-fado.jpg',
        origin: origin,
        readCode: 'FADO-2O26',
      );
      notifyListeners();
      return ImportOutcome.needsEventCode;
    }
    return _addTicket(_demoCode, origin);
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
      // Importing the same ticket again changes nothing (FR-084).
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
