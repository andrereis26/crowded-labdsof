import 'package:flutter/material.dart';

import '../../ui/ui.dart';

/// Where the route starts and ends, as a share of the map size.
const _origin = Offset(0.18, 0.88);
const _destination = Offset(0.66, 0.30);

/// Illustrated map with the route from the participant to the entrance
/// (P18). Drawn from the shapes of the mockup until a map provider is
/// chosen; it does not show real streets.
class MapPreview extends StatelessWidget {
  const MapPreview({
    super.key,
    required this.entranceLabel,
    this.originLabel,
    this.height = 230,
    this.radius = AppRadii.card,
  });

  /// "Porta A", "Porta A · entrada principal".
  final String entranceLabel;

  /// "Estás aqui"; only the icon when null.
  final String? originLabel;
  final double height;
  final Radius radius;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Mapa com o caminho até à entrada: $entranceLabel',
      excludeSemantics: true,
      child: ClipRRect(
        borderRadius: BorderRadius.all(radius),
        child: SizedBox(
          height: height,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final size = Size(constraints.maxWidth, height);
              Offset at(Offset share) =>
                  Offset(share.dx * size.width, share.dy * size.height);
              return Stack(
                children: [
                  Positioned.fill(child: CustomPaint(painter: _MapPainter())),
                  _Marker(
                    at: at(_destination),
                    label: entranceLabel,
                    icon: Icons.location_on_outlined,
                    iconBackground: AppColors.primary,
                    dark: true,
                  ),
                  _Marker(
                    at: at(_origin),
                    label: originLabel,
                    icon: Icons.my_location,
                    iconBackground: AppColors.mapOrigin,
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

/// Pill with a round icon whose centre sits on [at].
class _Marker extends StatelessWidget {
  const _Marker({
    required this.at,
    required this.icon,
    required this.iconBackground,
    this.label,
    this.dark = false,
  });

  final Offset at;
  final String? label;
  final IconData icon;
  final Color iconBackground;
  final bool dark;

  // Pill padding (5) plus half of the 22 px icon circle.
  static const _toCentre = 16.0;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: at.dx - _toCentre,
      top: at.dy - _toCentre,
      child: Container(
        padding: EdgeInsets.fromLTRB(5, 5, label == null ? 5 : 10, 5),
        decoration: BoxDecoration(
          color: dark ? AppColors.ink : AppColors.surface,
          borderRadius: const BorderRadius.all(AppRadii.pill),
          boxShadow: const [
            BoxShadow(
              color: Color(0x33000000),
              blurRadius: 6,
              offset: Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          spacing: AppSpacing.xs,
          children: [
            Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                color: iconBackground,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 14, color: Colors.white),
            ),
            if (label != null)
              Text(
                label!,
                style: AppTextStyles.tag.copyWith(
                  fontSize: 12,
                  color: dark ? Colors.white : AppColors.ink,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _MapPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    Offset p(double x, double y) => Offset(x * w, y * h);

    canvas.drawRect(Offset.zero & size, Paint()..color = AppColors.mapLand);

    final river = Path()
      ..moveTo(0, 0.74 * h)
      ..cubicTo(0.3 * w, 0.64 * h, 0.55 * w, 0.92 * h, w, 0.8 * h)
      ..lineTo(w, h)
      ..lineTo(0, h)
      ..close();
    canvas.drawPath(river, Paint()..color = AppColors.mapWater);

    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromPoints(p(0.5, 0.12), p(0.88, 0.48)),
        const Radius.circular(20),
      ),
      Paint()..color = AppColors.mapPark,
    );

    Paint road(double width) => Paint()
      ..color = Colors.white
      ..strokeWidth = width
      ..style = PaintingStyle.stroke;
    canvas
      ..drawLine(p(0, 0.42), p(1, 0.34), road(14))
      ..drawLine(p(0.28, 0), p(0.4, 1), road(12))
      ..drawPath(
        Path()
          ..moveTo(0, 0.16 * h)
          ..lineTo(0.62 * w, 0.6 * h)
          ..lineTo(w, 0.56 * h),
        road(8),
      )
      ..drawLine(p(0.72, 0), p(0.66, 1), road(8));

    final route = Path()
      ..moveTo(_origin.dx * w, _origin.dy * h)
      ..lineTo(0.34 * w, 0.56 * h)
      ..lineTo(_destination.dx * w, _destination.dy * h);
    canvas.drawPath(
      route,
      Paint()
        ..color = AppColors.primary
        ..strokeWidth = 6
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_MapPainter oldDelegate) => false;
}
