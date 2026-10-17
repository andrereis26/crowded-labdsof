import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Round icon button used in screen headers (notifications, add ticket,
/// search, more options, entry code).
class AppCircleButton extends StatelessWidget {
  const AppCircleButton({
    super.key,
    required this.icon,
    required this.semanticLabel,
    required this.onPressed,
    this.dark = false,
    this.badgeCount = 0,
    this.size = 36,
  });

  final IconData icon;
  final String semanticLabel;
  final VoidCallback? onPressed;

  /// Black background with a white icon ("Adicionar bilhete").
  final bool dark;

  /// Red counter on the top right, hidden when zero.
  final int badgeCount;
  final double size;

  @override
  Widget build(BuildContext context) {
    final label = badgeCount > 0
        ? '$semanticLabel, $badgeCount por ler'
        : semanticLabel;
    return Semantics(
      button: true,
      label: label,
      excludeSemantics: true,
      child: Material(
        color: dark ? AppColors.ink : AppColors.border,
        shape: const CircleBorder(),
        child: InkWell(
          onTap: onPressed,
          customBorder: const CircleBorder(),
          child: SizedBox.square(
            dimension: size,
            child: Center(
              child: Badge(
                isLabelVisible: badgeCount > 0,
                backgroundColor: AppColors.counter,
                offset: const Offset(10, -10),
                label: Text('$badgeCount'),
                child: Icon(
                  icon,
                  size: 20,
                  color: dark ? Colors.white : AppColors.ink,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
