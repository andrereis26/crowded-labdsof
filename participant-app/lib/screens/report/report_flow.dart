import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../event/location_request_sheet.dart';
import 'report_result_screen.dart';
import 'report_screen.dart';

/// Distance from the venue within which participants can report (P12).
const reportRadiusMeters = 500;

enum _AwayChoice { retry, lostAndFound }

/// Report flow from the event page: location (P10, asked once), then the
/// form (P11) and its result (P12). Pages on mobile, dialogs on desktop.
Future<void> startReport(
  BuildContext context,
  Event event, {
  ReportCategory? category,
}) async {
  final store = AppStore.read(context);
  if (!store.locationAllowed) {
    final allowed = await showLocationRequest(context, event.name);
    if (!allowed || !context.mounted) return;
    store.allowLocation();
  }

  final near = store.isNearVenue(event);
  final blocked = !near && (category?.needsPresence ?? true);

  if (!isDesktopLayout(context)) {
    if (blocked) {
      context.push('/event/${event.id}/report-result?status=away');
    } else {
      final query = category == null ? '' : '?category=${category.name}';
      context.push('/event/${event.id}/report$query');
    }
    return;
  }

  if (blocked) {
    final choice = await showAppDialog<_AwayChoice>(
      context: context,
      builder: (context) => AppDialog(
        width: 500,
        child: ReportAwayView(
          event: event,
          desktop: true,
          onBack: () => Navigator.pop(context),
          onRetry: () => Navigator.pop(context, _AwayChoice.retry),
          onLostAndFound: () =>
              Navigator.pop(context, _AwayChoice.lostAndFound),
        ),
      ),
    );
    if (!context.mounted) return;
    return switch (choice) {
      _AwayChoice.retry => startReport(context, event, category: category),
      _AwayChoice.lostAndFound => startReport(
        context,
        event,
        category: ReportCategory.lostAndFound,
      ),
      null => null,
    };
  }

  final report = await showAppDialog<ParticipantReport>(
    context: context,
    builder: (context) => AppDialog(
      title: 'Reportar algo',
      width: 760,
      onClose: () => Navigator.pop(context),
      child: ReportForm(
        event: event,
        nearVenue: near,
        initialCategory: category,
        desktop: true,
        onSent: (report) => Navigator.pop(context, report),
        onCancel: () => Navigator.pop(context),
      ),
    ),
  );
  if (report == null || !context.mounted) return;
  await showAppDialog<void>(
    context: context,
    builder: (context) => AppDialog(
      width: 500,
      child: ReportSentView(
        event: event,
        report: report,
        onDone: () => Navigator.pop(context),
      ),
    ),
  );
}
