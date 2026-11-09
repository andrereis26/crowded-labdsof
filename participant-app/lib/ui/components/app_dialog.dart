import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';
import 'app_circle_button.dart';

/// Shows [AppDialog] content over a dimmed page: the desktop counterpart of
/// [showAppBottomSheet] (report in P11, result in P12, confirmation in P13).
Future<T?> showAppDialog<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool barrierDismissible = true,
}) {
  return showDialog<T>(
    context: context,
    barrierDismissible: barrierDismissible,
    barrierColor: AppColors.scrim,
    builder: builder,
  );
}

/// White rounded dialog with an optional title and close button.
class AppDialog extends StatelessWidget {
  const AppDialog({
    super.key,
    required this.child,
    this.title,
    this.leading,
    this.onClose,
    this.width = 520,
  });

  final Widget child;

  /// Large title on the left ("Reportar algo").
  final String? title;

  /// Shown instead of [title], e.g. an [AppTag] ("PEDIDO DE CONFIRMAÇÃO").
  final Widget? leading;

  /// Shows a close button; usually `Navigator.pop`.
  final VoidCallback? onClose;
  final double width;

  @override
  Widget build(BuildContext context) {
    final hasHeader = title != null || leading != null || onClose != null;
    return Dialog(
      backgroundColor: Colors.transparent,
      elevation: 0,
      insetPadding: const EdgeInsets.all(AppSpacing.xxl),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: width),
        child: AppFloatingCard(
          radius: const BorderRadius.all(Radius.circular(20)),
          padding: const EdgeInsets.all(28),
          shadow: const BoxShadow(
            color: Color(0x38000000),
            blurRadius: 40,
            offset: Offset(0, 12),
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (hasHeader) ...[
                  Row(
                    spacing: AppSpacing.md,
                    children: [
                      Expanded(
                        child: title != null
                            ? Semantics(
                                header: true,
                                child: Text(
                                  title!,
                                  style: AppTextStyles.title.copyWith(
                                    fontSize: 24,
                                  ),
                                ),
                              )
                            : Align(
                                alignment: Alignment.centerLeft,
                                child: leading ?? const SizedBox.shrink(),
                              ),
                      ),
                      if (onClose != null)
                        AppCircleButton(
                          icon: Icons.close,
                          semanticLabel: 'Fechar',
                          size: 32,
                          onPressed: onClose,
                        ),
                    ],
                  ),
                  const SizedBox(height: 18),
                ],
                child,
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// White card raised above the grey background, used by desktop pages that
/// centre a single task (recognition P15, entry code P19).
class AppFloatingCard extends StatelessWidget {
  const AppFloatingCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(40),
    this.radius = const BorderRadius.all(AppRadii.sheet),
    this.shadow = const BoxShadow(
      color: Color(0x1F000000),
      blurRadius: 32,
      offset: Offset(0, 8),
    ),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final BorderRadius radius;
  final BoxShadow shadow;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: radius,
        boxShadow: [shadow],
      ),
      child: child,
    );
  }
}
