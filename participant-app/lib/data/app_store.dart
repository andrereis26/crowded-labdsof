import 'package:flutter/widgets.dart';

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
  }

  final DateTime Function() _clock;
  late final DemoData _demo;
  late final Map<String, Event> _catalog;
  late List<Ticket> _tickets;
  late Set<String> _followed;
  late List<AppNotification> _notifications;

  DateTime get now => _clock();

  static AppStore of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppStoreScope>()!.notifier!;

  // Connectivity (demo toggle until real connectivity checks exist).

  DateTime? _offlineSince;
  bool get offline => _offlineSince != null;

  /// When the data on screen was last saved, while offline.
  DateTime? get savedAt => _offlineSince;

  void setOffline(bool value) {
    _offlineSince = value ? now : null;
    notifyListeners();
  }

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
