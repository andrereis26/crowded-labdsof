/// A requirement of the password rule Core checks, with the wording the
/// registration screen lists.
enum PasswordRequirement {
  length('Pelo menos 8 caracteres'),
  uppercase('Uma letra maiúscula (A–Z)'),
  lowercase('Uma letra minúscula (a–z)'),
  digit('Um algarismo (0–9)'),
  symbol('Um símbolo, como ! @ # \$ % & * ? ou -');

  const PasswordRequirement(this.label);

  /// The line shown in the list of requirements, in European Portuguese.
  final String label;

  static const minLength = 8;

  bool isMetBy(String password) => switch (this) {
    length => password.length >= minLength,
    uppercase => password.runes.any(_isUpper),
    lowercase => password.runes.any(_isLower),
    digit => password.runes.any(_isDigit),
    symbol => password.runes.any(_isSymbol),
  };

  static bool _isUpper(int c) {
    final s = String.fromCharCode(c);
    return s != s.toLowerCase();
  }

  static bool _isLower(int c) {
    final s = String.fromCharCode(c);
    return s != s.toUpperCase();
  }

  static bool _isDigit(int c) => c >= 0x30 && c <= 0x39;

  /// Anything that is not a letter, a digit or a space, such as `-`, `!` or `€`.
  static bool _isSymbol(int c) {
    final s = String.fromCharCode(c);
    return !_isDigit(c) &&
        s.toLowerCase() == s.toUpperCase() &&
        s.trim().isNotEmpty;
  }
}

/// How strong a password the participant is choosing is: below moderate,
/// Core refuses it.
enum PasswordStrength {
  /// Three or more requirements are missing.
  veryWeak('Muito fraca'),

  /// One or two requirements are missing.
  weak('Fraca'),

  /// It meets every requirement.
  moderate('Moderada'),

  /// It meets every requirement and has 12 characters or more.
  strong('Forte'),

  /// It meets every requirement and has 16 characters or more.
  veryStrong('Muito forte');

  const PasswordStrength(this.label);

  /// The label shown under the password, in European Portuguese.
  final String label;

  static const strongLength = 12;
  static const veryStrongLength = 16;

  static PasswordStrength of(String password) {
    final missingCount = missing(password).length;
    if (missingCount >= 3) return veryWeak;
    if (missingCount > 0) return weak;
    if (password.length >= veryStrongLength) return veryStrong;
    return password.length >= strongLength ? strong : moderate;
  }

  /// Whether Core accepts a password this strong.
  bool get meetsTheRule => index >= moderate.index;

  /// The requirements the password does not meet yet.
  static List<PasswordRequirement> missing(String password) => [
    for (final requirement in PasswordRequirement.values)
      if (!requirement.isMetBy(password)) requirement,
  ];
}
