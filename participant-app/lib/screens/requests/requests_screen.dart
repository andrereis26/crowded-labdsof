import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../shared/event_context_row.dart';
import '../shared/shell.dart';
import '../shared/ticket_card.dart';

/// P17 · Pedidos da organização: yes/no requests and ideas the
/// organizer asks the event's participants for.
class RequestsScreen extends StatelessWidget {
  const RequestsScreen({super.key, required this.eventId});

  final String eventId;

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final event = store.eventById(eventId);
    if (event == null) {
      return AppPage(
        children: [
          AppErrorState(
            message: 'Não encontrámos este evento',
            retryLabel: 'Voltar aos meus eventos',
            onRetry: () => context.go('/eventos'),
          ),
        ],
      );
    }
    final now = store.now;
    final yesNo = store.yesNoRequestsFor(eventId);
    final ideas = store.ideasFor(eventId);
    final count = yesNo.length + ideas.length;
    final urgent = yesNo.where((n) => n.urgent && n.answer == null).length;
    final cards = [
      for (final request in yesNo)
        _YesNoCard(
          request: request,
          now: now,
          onAnswer: (answer) => store.answerRequest(request, answer),
        ),
      for (final prompt in ideas)
        _IdeaCard(
          key: ValueKey(prompt.id),
          prompt: prompt,
          now: now,
          onShare: (idea) {
            store.shareIdea(prompt, idea);
            showMessage(context, 'Ideia partilhada. Obrigado!');
          },
        ),
    ];
    final empty = count == 0
        ? const AppEmptyState(
            icon: Icons.chat_bubble_outline,
            message: 'Sem pedidos da organização',
          )
        : null;

    if (isDesktopLayout(context)) {
      final ticket = store.ticketFor(eventId);
      return Scaffold(
        appBar: const CrowdedWebHeader(currentIndex: Sections.wallet),
        body: ListView(
          padding: const EdgeInsets.symmetric(vertical: 40),
          children: [
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1200),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 40),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    spacing: AppSpacing.xxl,
                    children: [
                      AppBreadcrumbs(
                        items: [
                          AppBreadcrumb(
                            'Os meus eventos',
                            onTap: () => context.go('/eventos'),
                          ),
                          AppBreadcrumb(
                            event.name,
                            onTap: () => context.go('/evento/${event.id}'),
                          ),
                          const AppBreadcrumb('Pedidos da organização'),
                        ],
                      ),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        spacing: 24,
                        children: [
                          SizedBox(
                            width: 380,
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              spacing: AppSpacing.lg,
                              children: [
                                if (ticket != null)
                                  TicketCard(ticket: ticket, now: now),
                                AppPanel(
                                  title: 'Pedidos da organização',
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: AppSpacing.xl,
                                    vertical: AppSpacing.lg,
                                  ),
                                  spacing: AppSpacing.sm,
                                  children: [
                                    Text(
                                      'Confirmações, sondagens e ideias que '
                                      'a organização te pede.',
                                      style: AppTextStyles.footnote.copyWith(
                                        height: 1.4,
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              spacing: AppSpacing.lg,
                              children: [
                                Text(
                                  'Pedidos ($count)',
                                  style: AppTextStyles.title.copyWith(
                                    fontSize: 20,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                ?empty,
                                ...cards,
                              ],
                            ),
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
      title: 'Pedidos',
      showReportButton: true,
      children: [
        EventContextRow(
          event: event,
          subtitle: [
            count == 1
                ? '1 pedido da organização'
                : '$count pedidos da '
                      'organização',
            if (urgent > 0) urgent == 1 ? '1 urgente' : '$urgent urgentes',
          ].join(' · '),
        ),
        ?empty,
        ...cards,
      ],
    );
  }
}

/// Header line of a request card: tags on the left, time on the right.
class _CardHeader extends StatelessWidget {
  const _CardHeader({required this.tags, required this.when});

  final List<Widget> tags;
  final String when;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      spacing: AppSpacing.sm,
      children: [
        Expanded(
          child: Wrap(
            spacing: AppSpacing.xs,
            runSpacing: AppSpacing.xs,
            children: tags,
          ),
        ),
        Text(when, style: AppTextStyles.caption),
      ],
    );
  }
}

const _cardPadding = EdgeInsets.all(AppSpacing.xl);

class _YesNoCard extends StatelessWidget {
  const _YesNoCard({
    required this.request,
    required this.now,
    required this.onAnswer,
  });

  final AppNotification request;
  final DateTime now;
  final ValueChanged<bool> onAnswer;

  @override
  Widget build(BuildContext context) {
    final answer = request.answer;
    final open = answer == null;
    final footer = [
      ?request.source,
      if (request.responses != null) '${request.responses} respostas',
    ].join(' · ');
    return AppPanel(
      padding: _cardPadding,
      spacing: 10,
      borderColor: request.urgent && open ? AppColors.dangerBorder : null,
      children: [
        _CardHeader(
          tags: [
            if (request.urgent)
              const AppTag(
                label: 'URGENTE',
                tone: AppTagTone.danger,
                icon: Icons.warning_amber_rounded,
              ),
            const AppTag(label: 'Confirmação', tone: AppTagTone.info),
          ],
          when: formatSentAgo(request.receivedAt, now: now),
        ),
        Text(request.title, style: AppTextStyles.headline),
        if (request.body != null)
          Text(
            request.body!,
            style: AppTextStyles.body.copyWith(fontSize: 14, height: 1.4),
          ),
        if (open)
          Row(
            spacing: 10,
            children: [
              Expanded(
                child: AppButton.secondary(
                  label: 'Não',
                  onPressed: () => onAnswer(false),
                ),
              ),
              Expanded(
                child: AppButton(label: 'Sim', onPressed: () => onAnswer(true)),
              ),
            ],
          )
        else
          Text(
            'Respondeste: ${answer ? 'Sim' : 'Não'}',
            style: AppTextStyles.caption.copyWith(
              color: AppColors.success,
              fontWeight: FontWeight.w600,
            ),
          ),
        if (footer.isNotEmpty) Text(footer, style: AppTextStyles.source),
      ],
    );
  }
}

class _IdeaCard extends StatefulWidget {
  const _IdeaCard({
    super.key,
    required this.prompt,
    required this.now,
    required this.onShare,
  });

  final IdeaPrompt prompt;
  final DateTime now;
  final ValueChanged<String> onShare;

  @override
  State<_IdeaCard> createState() => _IdeaCardState();
}

class _IdeaCardState extends State<_IdeaCard> {
  final _idea = TextEditingController();

  @override
  void dispose() {
    _idea.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final prompt = widget.prompt;
    final count = prompt.ideasShared;
    return AppPanel(
      padding: _cardPadding,
      spacing: 10,
      children: [
        _CardHeader(
          tags: const [
            AppTag(
              label: 'Ideia',
              tone: AppTagTone.warning,
              icon: Icons.lightbulb_outline,
            ),
          ],
          when: formatSentAgo(prompt.sentAt, now: widget.now),
        ),
        Text(prompt.question, style: AppTextStyles.headline),
        if (prompt.shared)
          Text(
            'Partilhaste a tua ideia. Obrigado!',
            style: AppTextStyles.caption.copyWith(
              color: AppColors.success,
              fontWeight: FontWeight.w600,
            ),
          )
        else
          Semantics(
            label: prompt.question,
            child: TextField(
              controller: _idea,
              minLines: 2,
              maxLines: 5,
              onChanged: (_) => setState(() {}),
              style: AppTextStyles.body.copyWith(height: 1.4),
              decoration: appInputDecoration(hint: 'Escreve a tua ideia…'),
            ),
          ),
        Row(
          spacing: 10,
          children: [
            Expanded(
              child: Text(
                count == 1 ? '1 ideia partilhada' : '$count ideias partilhadas',
                style: AppTextStyles.caption,
              ),
            ),
            if (!prompt.shared)
              AppButton(
                label: 'Partilhar ideia',
                size: AppButtonSize.small,
                expand: false,
                onPressed: _idea.text.trim().isEmpty
                    ? null
                    : () {
                        widget.onShare(_idea.text);
                        _idea.clear();
                      },
              ),
          ],
        ),
      ],
    );
  }
}
