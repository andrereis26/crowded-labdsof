import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';
import 'app_bottom_sheet.dart';

/// Label shown above a form field, with an optional "(opcional)" hint.
class AppFieldLabel extends StatelessWidget {
  const AppFieldLabel(this.text, {super.key, this.optional = false});

  final String text;
  final bool optional;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Flexible(child: Text(text, style: AppTextStyles.label)),
        if (optional) ...[
          const SizedBox(width: AppSpacing.xs),
          Text('(opcional)', style: AppTextStyles.caption),
        ],
      ],
    );
  }
}

/// Text input with a label above a white rounded box (P3, P11).
class AppTextField extends StatelessWidget {
  const AppTextField({
    super.key,
    required this.label,
    this.controller,
    this.initialValue,
    this.hint,
    this.optional = false,
    this.prefixIcon,
    this.suffix,
    this.minLines,
    this.maxLines = 1,
    this.keyboardType,
    this.textInputAction,
    this.inputFormatters,
    this.obscureText = false,
    this.autofillHints,
    this.errorText,
    this.enabled = true,
    this.onChanged,
    this.onSubmitted,
    this.borderColor,
  }) : assert(controller == null || initialValue == null);

  /// Multi-line description box ("Descrição (opcional)").
  const AppTextField.multiline({
    super.key,
    required this.label,
    this.controller,
    this.initialValue,
    this.hint,
    this.optional = false,
    this.minLines = 3,
    this.maxLines = 6,
    this.errorText,
    this.enabled = true,
    this.onChanged,
  }) : assert(controller == null || initialValue == null),
       prefixIcon = null,
       suffix = null,
       keyboardType = TextInputType.multiline,
       textInputAction = TextInputAction.newline,
       inputFormatters = null,
       obscureText = false,
       autofillHints = null,
       onSubmitted = null,
       borderColor = null;

  final String label;
  final TextEditingController? controller;
  final String? initialValue;
  final String? hint;
  final bool optional;
  final IconData? prefixIcon;

  /// Trailing widget inside the box, e.g. a show-password button.
  final Widget? suffix;
  final int? minLines;
  final int? maxLines;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final List<TextInputFormatter>? inputFormatters;
  final bool obscureText;
  final Iterable<String>? autofillHints;
  final String? errorText;
  final bool enabled;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;

  /// Highlights the box when it is not focused, e.g. [AppColors.attention]
  /// for a value the participant should check.
  final Color? borderColor;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        AppFieldLabel(label, optional: optional),
        const SizedBox(height: AppSpacing.xs),
        TextFormField(
          controller: controller,
          initialValue: initialValue,
          enabled: enabled,
          minLines: minLines,
          maxLines: obscureText ? 1 : maxLines,
          keyboardType: keyboardType,
          textInputAction: textInputAction,
          inputFormatters: inputFormatters,
          obscureText: obscureText,
          autofillHints: autofillHints,
          onChanged: onChanged,
          onFieldSubmitted: onSubmitted,
          style: AppTextStyles.body.copyWith(height: 1.4),
          decoration: appInputDecoration(
            hint: hint,
            prefixIcon: prefixIcon,
            suffixIcon: suffix == null
                ? null
                : Padding(
                    padding: const EdgeInsets.only(right: AppSpacing.xs),
                    child: suffix,
                  ),
            errorText: errorText,
            borderColor: borderColor,
          ),
        ),
      ],
    );
  }
}

/// Decoration shared by [AppTextField] and other inputs that need the same
/// box look (e.g. a search field).
InputDecoration appInputDecoration({
  String? hint,
  IconData? prefixIcon,
  Widget? suffixIcon,
  String? errorText,
  Color? borderColor,
}) {
  OutlineInputBorder border(Color color, [double width = 1]) =>
      OutlineInputBorder(
        borderRadius: const BorderRadius.all(AppRadii.field),
        borderSide: BorderSide(color: color, width: width),
      );

  return InputDecoration(
    isDense: true,
    filled: true,
    fillColor: AppColors.surface,
    hintText: hint,
    hintStyle: AppTextStyles.body.copyWith(color: AppColors.inkSecondary),
    errorText: errorText,
    errorStyle: AppTextStyles.caption.copyWith(color: AppColors.danger),
    errorMaxLines: 3,
    contentPadding: const EdgeInsets.symmetric(
      horizontal: AppSpacing.lg,
      vertical: AppSpacing.md,
    ),
    prefixIcon: prefixIcon == null
        ? null
        : Padding(
            padding: const EdgeInsets.only(
              left: AppSpacing.lg,
              right: AppSpacing.sm,
            ),
            child: Icon(prefixIcon, size: 18, color: AppColors.inkSecondary),
          ),
    prefixIconConstraints: const BoxConstraints(minWidth: 0, minHeight: 0),
    suffixIcon: suffixIcon,
    suffixIconConstraints: const BoxConstraints(minWidth: 0, minHeight: 0),
    border: border(AppColors.border),
    enabledBorder: border(borderColor ?? AppColors.border),
    disabledBorder: border(AppColors.border),
    focusedBorder: border(AppColors.primary, 1.5),
    errorBorder: border(AppColors.danger),
    focusedErrorBorder: border(AppColors.danger, 1.5),
  );
}

/// One choice of an [AppSelectField].
class AppSelectOption<T> {
  const AppSelectOption({required this.value, required this.label});

  final T value;
  final String label;
}

/// Field that looks like [AppTextField] with a chevron and opens a bottom
/// sheet to pick one option ("Onde?" in P11).
class AppSelectField<T> extends StatelessWidget {
  const AppSelectField({
    super.key,
    required this.label,
    required this.options,
    required this.value,
    required this.onChanged,
    this.hint = 'Escolher',
    this.optional = false,
    this.prefixIcon,
    this.errorText,
  });

  final String label;
  final List<AppSelectOption<T>> options;
  final T? value;
  final ValueChanged<T>? onChanged;
  final String hint;
  final bool optional;
  final IconData? prefixIcon;
  final String? errorText;

  Future<void> _open(BuildContext context) async {
    final picked = await showAppBottomSheet<T>(
      context: context,
      builder: (context) => Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.sm),
            child: Text(label, style: AppTextStyles.headline),
          ),
          for (final option in options)
            InkWell(
              borderRadius: const BorderRadius.all(AppRadii.field),
              onTap: () => Navigator.of(context).pop(option.value),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(option.label, style: AppTextStyles.body),
                    ),
                    if (option.value == value)
                      const Icon(
                        Icons.check,
                        size: 20,
                        color: AppColors.primary,
                      ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
    if (picked != null) onChanged?.call(picked);
  }

  @override
  Widget build(BuildContext context) {
    final selected = options.where((o) => o.value == value).firstOrNull;
    final hasError = errorText != null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        AppFieldLabel(label, optional: optional),
        const SizedBox(height: AppSpacing.xs),
        Semantics(
          button: true,
          label: label,
          value: selected?.label,
          child: InkWell(
            onTap: onChanged == null ? null : () => _open(context),
            borderRadius: const BorderRadius.all(AppRadii.field),
            child: Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.lg,
                vertical: AppSpacing.md,
              ),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: const BorderRadius.all(AppRadii.field),
                border: Border.all(
                  color: hasError ? AppColors.danger : AppColors.border,
                ),
              ),
              child: Row(
                children: [
                  if (prefixIcon != null) ...[
                    Icon(prefixIcon, size: 18, color: AppColors.inkSecondary),
                    const SizedBox(width: AppSpacing.sm),
                  ],
                  Expanded(
                    child: Text(
                      selected?.label ?? hint,
                      style: AppTextStyles.body.copyWith(
                        height: 1.4,
                        color: selected == null
                            ? AppColors.inkSecondary
                            : AppColors.ink,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  const Icon(
                    Icons.keyboard_arrow_down,
                    size: 20,
                    color: AppColors.inkSecondary,
                  ),
                ],
              ),
            ),
          ),
        ),
        if (hasError) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            errorText!,
            style: AppTextStyles.caption.copyWith(color: AppColors.danger),
          ),
        ],
      ],
    );
  }
}
