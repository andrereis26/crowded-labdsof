import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P16 · Responder a sondagem de presença (#46). Answering does not follow
/// the proposed event.
class PollScreen extends StatefulWidget {
  const PollScreen({super.key, required this.pollId});

  final String pollId;

  @override
  State<PollScreen> createState() => _PollScreenState();
}

class _PollScreenState extends State<PollScreen> {
  List<int?>? _choices;

  void _send(AppStore store, Poll poll) {
    store.answerPoll(poll, _choices!.cast<int>());
    showMessage(context, 'Resposta enviada. Obrigado!');
    AppPage.goBack(context);
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final poll = store.poll(widget.pollId);
    if (poll == null) {
      return AppPage(
        title: 'Sondagem',
        children: [
          AppErrorState(
            message: 'Não encontrámos esta sondagem',
            retryLabel: 'Voltar à carteira',
            onRetry: () => context.go('/carteira'),
          ),
        ],
      );
    }
    final answered = poll.answers != null;
    final choices = _choices ??= [
      for (final (i, _) in poll.questions.indexed) poll.answers?[i],
    ];
    final complete = choices.every((c) => c != null);
    final desktop = isDesktopLayout(context);

    final header = [
      Row(
        spacing: 10,
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: AppColors.poll,
            child: Text(
              poll.organizerInitials,
              style: AppTextStyles.subhead.copyWith(color: Colors.white),
            ),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: 1,
              children: [
                Text(poll.organizer, style: AppTextStyles.subhead),
                Text(
                  [
                    poll.proposedEvent,
                    formatWeekdayDate(poll.date),
                    if (desktop) poll.venue,
                  ].join(' · '),
                  style: AppTextStyles.caption.copyWith(height: 1.2),
                ),
              ],
            ),
          ),
          answered
              ? const AppTag(
                  label: 'Respondeste',
                  tone: AppTagTone.success,
                  icon: Icons.check,
                )
              : AppTag(
                  label: 'Fecha a ${formatDayMonth(poll.closesAt)}',
                  icon: Icons.schedule,
                ),
        ],
      ),
      Semantics(
        header: true,
        child: Text(
          poll.title,
          style: desktop
              ? AppTextStyles.title.copyWith(fontSize: 26, letterSpacing: -0.26)
              : AppTextStyles.title,
        ),
      ),
      Text(
        '${poll.questions.length} perguntas · menos de 1 minuto',
        style: desktop ? AppTextStyles.bodySecondary : AppTextStyles.footnote,
      ),
    ];
    final questions = [
      for (final (q, question) in poll.questions.indexed)
        AppPanel(
          title: '${q + 1}. ${question.text}',
          borderColor: desktop ? AppColors.border : null,
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.xl,
            vertical: AppSpacing.lg,
          ),
          spacing: 10,
          children: [
            for (final (o, option) in question.options.indexed)
              AppOptionTile(
                label: option,
                selected: choices[q] == o,
                onTap: answered ? null : () => setState(() => choices[q] = o),
              ),
          ],
        ),
    ];
    final send = AppButton(
      label: answered ? 'Resposta enviada' : 'Enviar resposta',
      icon: answered ? Icons.check : Icons.send_outlined,
      expand: !desktop,
      onPressed: answered || !complete ? null : () => _send(store, poll),
    );

    if (desktop) {
      return Scaffold(
        appBar: const CrowdedWebHeader(currentIndex: Sections.wallet),
        body: ListView(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.screen,
            vertical: 48,
          ),
          children: [
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 640),
                child: AppFloatingCard(
                  padding: const EdgeInsets.all(36),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    spacing: AppSpacing.xl,
                    children: [
                      ...header,
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        spacing: AppSpacing.md,
                        children: questions,
                      ),
                      Row(
                        spacing: AppSpacing.md,
                        children: [
                          send,
                          AppButton.secondary(
                            label: answered ? 'Fechar' : 'Agora não',
                            expand: false,
                            onPressed: () => AppPage.goBack(context),
                          ),
                          const Spacer(),
                          Text(
                            '${formatCount(poll.responses)} pessoas já '
                            'responderam',
                            style: AppTextStyles.caption,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      );
    }

    return AppPage(
      title: 'Sondagem',
      children: [...header, ...questions, send],
    );
  }
}
