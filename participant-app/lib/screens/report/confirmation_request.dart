import 'package:flutter/material.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../event/location_request_sheet.dart';
import '../shared/shell.dart';

const _tag = AppTag(
  label: 'PEDIDO DE CONFIRMAÇÃO',
  tone: AppTagTone.info,
  size: AppTagSize.medium,
);

/// P13 · Pedido de confirmação Sim / Não: a bottom sheet on mobile and a
/// dialog on desktop, after the location request (P10) if not yet allowed.
Future<void> showConfirmationRequest(
  BuildContext context,
  ConfirmationRequest request,
) async {
  final store = AppStore.read(context);
  if (request.answer != null) {
    showMessage(context, 'Já respondeste a este pedido.');
    return;
  }
  if (!store.locationAllowed) {
    final allowed = await showLocationRequest(context, request.eventName);
    if (!allowed || !context.mounted) return;
    store.allowLocation();
  }

  final now = store.now;
  final ConfirmationAnswer? answer;
  if (isDesktopLayout(context)) {
    answer = await showAppDialog<ConfirmationAnswer>(
      context: context,
      builder: (context) => AppDialog(
        leading: _tag,
        onClose: () => Navigator.pop(context),
        child: ConfirmationRequestView(
          request: request,
          now: now,
          showTag: false,
          onAnswer: (answer) => Navigator.pop(context, answer),
        ),
      ),
    );
  } else {
    answer = await showAppBottomSheet<ConfirmationAnswer>(
      context: context,
      builder: (context) => ConfirmationRequestView(
        request: request,
        now: now,
        onAnswer: (answer) => Navigator.pop(context, answer),
      ),
    );
  }
  if (answer == null || !context.mounted) return;

  store.answerConfirmation(request, answer);
  showMessage(
    context,
    answer == ConfirmationAnswer.cantSee
        ? 'Obrigado. Esta resposta não conta para a estimativa.'
        : 'Obrigado! A tua resposta ajuda quem está no recinto.',
  );
}

/// "Confirmas isto?" with the report and the Yes/No answers.
class ConfirmationRequestView extends StatelessWidget {
  const ConfirmationRequestView({
    super.key,
    required this.request,
    required this.now,
    required this.onAnswer,
    this.showTag = true,
  });

  final ConfirmationRequest request;
  final DateTime now;
  final ValueChanged<ConfirmationAnswer> onAnswer;

  /// The dialog shows the tag in its header instead.
  final bool showTag;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      spacing: AppSpacing.lg,
      children: [
        if (showTag) const Center(child: _tag),
        Semantics(
          header: true,
          child: Text(
            'Confirmas isto?',
            textAlign: TextAlign.center,
            style: AppTextStyles.sheetTitle,
          ),
        ),
        Container(
          padding: const EdgeInsets.all(AppSpacing.xl),
          decoration: const BoxDecoration(
            color: AppColors.background,
            borderRadius: BorderRadius.all(AppRadii.card),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            spacing: AppSpacing.xs,
            children: [
              Row(
                spacing: 10,
                children: [
                  Icon(
                    request.category.icon,
                    size: 22,
                    color: AppColors.warning,
                  ),
                  Expanded(
                    child: Text(
                      request.title,
                      style: AppTextStyles.buttonLarge,
                    ),
                  ),
                ],
              ),
              Text(request.detail, style: AppTextStyles.body),
              Text(
                'Reportado ${formatTimeAgo(request.reportedAt, now: now)} '
                'por um participante · ${request.eventName}',
                style: AppTextStyles.caption,
              ),
            ],
          ),
        ),
        Row(
          spacing: AppSpacing.md,
          children: [
            Expanded(
              child: AppButton.secondary(
                label: 'Não',
                icon: Icons.thumb_down_outlined,
                size: AppButtonSize.large,
                onPressed: () => onAnswer(ConfirmationAnswer.no),
              ),
            ),
            Expanded(
              child: AppButton(
                label: 'Sim',
                icon: Icons.thumb_up_outlined,
                size: AppButtonSize.large,
                onPressed: () => onAnswer(ConfirmationAnswer.yes),
              ),
            ),
          ],
        ),
        AppButton.link(
          label: 'Não consigo ver daqui',
          onPressed: () => onAnswer(ConfirmationAnswer.cantSee),
        ),
      ],
    );
  }
}
