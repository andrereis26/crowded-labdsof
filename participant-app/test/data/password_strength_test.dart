import 'package:crowded/data/password_strength.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('rates each password on the five levels', () {
    const expected = {
      'curta': PasswordStrength.veryWeak,
      'abcdefgh': PasswordStrength.veryWeak, // ggignore
      'semmaiusculas1!': PasswordStrength.weak, // ggignore
      'SEMMINUSCULAS1!': PasswordStrength.weak, // ggignore
      'Semalgarismos!': PasswordStrength.weak, // ggignore
      'Semsimbolos1': PasswordStrength.weak, // ggignore
      'Com espaco1': PasswordStrength.weak, // ggignore
      'palavra-secreta': PasswordStrength.weak, // ggignore
      'Segr3do!': PasswordStrength.moderate, // ggignore
      'Çeia-2026': PasswordStrength.moderate, // ggignore
      'Segr3do!-Forte': PasswordStrength.strong, // ggignore
      'Palavra-secr3ta!': PasswordStrength.veryStrong, // ggignore
    };
    expected.forEach((password, strength) {
      expect(PasswordStrength.of(password), strength, reason: password);
    });
  });

  test('only moderate and above meet the rule Core checks', () {
    expect(PasswordStrength.veryWeak.meetsTheRule, isFalse);
    expect(PasswordStrength.weak.meetsTheRule, isFalse);
    expect(PasswordStrength.moderate.meetsTheRule, isTrue);
    expect(PasswordStrength.veryStrong.label, 'Muito forte');
  });

  test('lists what is missing', () {
    expect(PasswordStrength.missing('abc'), [
      PasswordRequirement.length,
      PasswordRequirement.uppercase,
      PasswordRequirement.digit,
      PasswordRequirement.symbol,
    ]);
    expect(PasswordRequirement.symbol.label, contains('!'));
    expect(PasswordStrength.missing('Pass €2026'), isEmpty); // ggignore
  });
}
