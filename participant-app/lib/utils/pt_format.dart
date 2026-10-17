/// Date and time formatting in European Portuguese, as used in the mockups
/// ("SÁB 8 NOV", "21:30", "HOJE").
library;

const _weekdays = ['SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB', 'DOM'];
const _months = [
  'JAN',
  'FEV',
  'MAR',
  'ABR',
  'MAI',
  'JUN',
  'JUL',
  'AGO',
  'SET',
  'OUT',
  'NOV',
  'DEZ',
];

const _monthNames = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
];

DateTime dateOnly(DateTime d) => DateTime(d.year, d.month, d.day);

int daysBetween(DateTime from, DateTime to) =>
    dateOnly(to).difference(dateOnly(from)).inDays;

bool isSameDay(DateTime a, DateTime b) => daysBetween(a, b) == 0;

/// "8 NOV".
String formatDayMonth(DateTime d) => '${d.day} ${_months[d.month - 1]}';

/// "12 de dezembro".
String formatLongDate(DateTime d) => '${d.day} de ${_monthNames[d.month - 1]}';

/// "18 min", "1 h 05 min".
String formatDuration(Duration d) {
  if (d.inHours == 0) return '${d.inMinutes} min';
  final minutes = d.inMinutes % 60;
  return minutes == 0
      ? '${d.inHours} h'
      : '${d.inHours} h ${minutes.toString().padLeft(2, '0')} min';
}

/// "1 240", with a space as the thousands separator.
String formatCount(int n) {
  final digits = n.toString();
  final buffer = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) buffer.write(' ');
    buffer.write(digits[i]);
  }
  return buffer.toString();
}

/// "SÁB 8 NOV".
String formatWeekdayDate(DateTime d) =>
    '${_weekdays[d.weekday - 1]} ${formatDayMonth(d)}';

/// "21:30".
String formatTime(DateTime d) =>
    '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';

/// "17/10/2026".
String formatNumericDate(DateTime d) =>
    '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}';

/// "HOJE", "AMANHÃ" or "14 NOV".
String formatRelativeDay(DateTime d, DateTime now) {
  return switch (daysBetween(now, d)) {
    0 => 'HOJE',
    1 => 'AMANHÃ',
    _ => formatDayMonth(d),
  };
}

/// Title case for running text: "Hoje", "Amanhã", "Sáb 8 Nov".
String formatRelativeDaySentence(DateTime d, DateTime now) {
  return switch (daysBetween(now, d)) {
    0 => 'Hoje',
    1 => 'Amanhã',
    _ => _titleCase(formatWeekdayDate(d)),
  };
}

String _titleCase(String s) => s
    .split(' ')
    .map((w) => w.isEmpty ? w : w[0] + w.substring(1).toLowerCase())
    .join(' ');

/// "5,4 km" or "800 m".
String formatDistance(double km) {
  if (km < 1) return '${(km * 1000).round()} m';
  return '${km.toStringAsFixed(1).replaceAll('.', ',')} km';
}
