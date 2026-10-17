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
    _demo = demo;
    _catalog = {for (final e in demo.catalog) e.id: e};
    _tickets = demo.tickets;
    _followed = demo.followedIds;
    _notifications = demo.notifications;
    _preferences = NotificationPreferences()
      ..perEvent[demo.jazzNoParque.id] = EventNotificationLevel.officialOnly;
    _confirmations = demo.confirmationRequests;
    _recognitions = demo.recognitions;
    _polls = demo.polls;
    _ideas = demo.ideaPrompts;
    _contributions = demo.contributions;
  }

  final DateTime Function() _clock;
  late final DemoData _demo;
  late final Map<String, Event> _catalog;
  late List<Ticket> _tickets;
  late Set<String> _followed;
  late List<AppNotification> _notifications;
  late NotificationPreferences _preferences;
  late List<ConfirmationRequest> _confirmations;
  late List<Recognition> _recognitions;
  late List<Poll> _polls;
  late List<IdeaPrompt> _ideas;
  late List<Contribution> _contributions;

  /// Name of the signed-in participant (entry code holder in P19).
  String get userName => _account?.name ?? '';

  DateTime get now => _clock();

  static AppStore of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppStoreScope>()!.notifier!;

  // Account (C1, C3). Sign-in is a demo until Core authentication exists.

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
    _followed = {};
    _notifications = [];
    _confirmations = [];
    _recognitions = [];
    _contributions = [];
    _preferences = NotificationPreferences();
    importDraft = null;
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

  /// Events with a ticket or followed, for "Por evento".
  List<Event> get connectedEvents => [
    for (final t in tickets) t.event,
    ...followedEvents,
  ];

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

  Event? eventById(String id) =>
      _catalog[id] ??
      _tickets.where((t) => t.event.id == id).firstOrNull?.event;

  /// Wallet order: soonest first.
  List<Ticket> get tickets =>
      [..._tickets]..sort((a, b) => a.event.start.compareTo(b.event.start));

  Ticket? ticketFor(String eventId) =>
      _tickets.where((t) => t.event.id == eventId).firstOrNull;

  int get changedTicketCount =>
      _tickets.where((t) => t.event.status != EventStatus.scheduled).length;

  bool isFollowing(String eventId) => _followed.contains(eventId);

  void toggleFollow(String eventId) {
    if (!_followed.remove(eventId)) _followed.add(eventId);
    notifyListeners();
  }

  /// Followed events without a ticket, soonest first ("A seguir" in P1).
  List<Event> get followedEvents =>
      _followed
          .map(eventById)
          .whereType<Event>()
          .where((e) => ticketFor(e.id) == null)
          .toList()
        ..sort((a, b) => a.start.compareTo(b.start));

  /// Catalog events for P9, filtered by [filter].
  List<Event> discover(DiscoverFilter filter) {
    final today = now;
    final upcoming = _catalog.values.where(
      (e) => daysBetween(today, e.start) >= 0 && e.distanceKm != null,
    );
    final result = switch (filter) {
      DiscoverFilter.today => upcoming.where((e) => isSameDay(e.start, today)),
      DiscoverFilter.weekend => upcoming.where(
        (e) =>
            daysBetween(today, e.start) < 7 &&
            (e.start.weekday == DateTime.saturday ||
                e.start.weekday == DateTime.sunday),
      ),
      DiscoverFilter.nearby => upcoming.where((e) => e.distanceKm! <= 3),
    }.toList();
    result.sort(
      filter == DiscoverFilter.nearby
          ? (a, b) => a.distanceKm!.compareTo(b.distanceKm!)
          : (a, b) => a.start.compareTo(b.start),
    );
    return result;
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

  // Organizer requests (P16, P17).

  Poll? poll(String id) => _polls.where((p) => p.id == id).firstOrNull;

  void answerPoll(Poll poll, List<int> answers) {
    poll
      ..answers = answers
      ..responses += 1;
    notifyListeners();
  }

  /// Yes/no requests sent to the participants of [eventId].
  List<AppNotification> yesNoRequestsFor(String eventId) => notifications
      .where((n) => n.eventId == eventId && n.kind == NotificationKind.request)
      .toList();

  List<Poll> pollsFor(String eventId) =>
      _polls.where((p) => p.eventId == eventId).toList();

  List<IdeaPrompt> ideasFor(String eventId) =>
      _ideas.where((i) => i.eventId == eventId).toList();

  void shareIdea(IdeaPrompt prompt, String idea) {
    if (idea.trim().isEmpty) return;
    prompt
      ..shared = true
      ..ideasShared += 1;
    notifyListeners();
  }

  // Ticket import (P2 → P3 → P4).

  TicketDraft? importDraft;

  /// Starts an import. Files are "extracted" with demo data for now.
  TicketDraft startImport(TicketOrigin origin) {
    importDraft = origin == TicketOrigin.manual
        ? TicketDraft(origin: origin)
        : _demo.extractedDraft(origin);
    return importDraft!;
  }

  /// Catalog events that may be the one on the draft ticket.
  List<EventMatch> matchesFor(TicketDraft draft) {
    final words = draft.eventName
        .toLowerCase()
        .split(RegExp(r'\s+'))
        .where((w) => w.length > 3)
        .toSet();
    if (words.isEmpty) return const [];
    final matches = <EventMatch>[];
    for (final event in _catalog.values) {
      final name = event.name.toLowerCase();
      if (!words.any(name.contains)) continue;
      final sameDay = draft.date != null && isSameDay(draft.date!, event.start);
      matches.add(EventMatch(event: event, strong: sameDay));
    }
    matches.sort((a, b) => (b.strong ? 1 : 0) - (a.strong ? 1 : 0));
    return matches;
  }

  /// Saves the draft as a ticket, linked to [event] or, when null, to an
  /// event built from the ticket data (no official changes).
  Ticket saveDraft(TicketDraft draft, {Event? event}) {
    final linked = event ?? _unlinkedEvent(draft);
    _tickets.removeWhere((t) => t.event.id == linked.id);
    final ticket = Ticket(
      id: 't-${linked.id}-${now.millisecondsSinceEpoch}',
      event: linked,
      origin: draft.origin,
      importedAt: now,
      gate: draft.gate.isEmpty ? null : draft.gate,
      seat: draft.seat.isEmpty ? null : draft.seat,
      hasEntryCode: draft.hasEntryCode,
    );
    _tickets.add(ticket);
    _followed.remove(linked.id);
    importDraft = null;
    notifyListeners();
    return ticket;
  }

  Event _unlinkedEvent(TicketDraft draft) {
    final date = draft.date ?? now;
    final (hour, minute) = _parseTime(draft.start) ?? (0, 0);
    final parts = draft.place.split(',');
    return Event(
      id: 'ticket-${now.millisecondsSinceEpoch}',
      name: draft.eventName,
      venue: parts.first.trim(),
      city: parts.length > 1 ? parts.sublist(1).join(',').trim() : '',
      start: DateTime(date.year, date.month, date.day, hour, minute),
      art: const EventArt(Color(0xFF596B9E), Color(0xFF1A1F38)),
    );
  }

  static (int, int)? _parseTime(String value) {
    final match = RegExp(r'^(\d{1,2}):(\d{2})$').firstMatch(value.trim());
    if (match == null) return null;
    return (int.parse(match[1]!), int.parse(match[2]!));
  }
}

enum DiscoverFilter {
  today('Hoje'),
  weekend('Fim de semana'),
  nearby('Perto de mim');

  const DiscoverFilter(this.label);

  final String label;
}

/// Makes the [AppStore] available to the widget tree.
class AppStoreScope extends InheritedNotifier<AppStore> {
  const AppStoreScope({
    super.key,
    required AppStore store,
    required super.child,
  }) : super(notifier: store);
}
