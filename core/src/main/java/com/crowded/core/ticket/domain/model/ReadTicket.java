package com.crowded.core.ticket.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A ticket file as read locally, without AI: the decoded QR code, which is the ticket's entry code,
 * and the text printed on the ticket. The entry code is a credential, so it is never printed and
 * never sent to the AI.
 */
public record ReadTicket(String entryCode, String text) {

  /** What replaces each value removed from the text sent to the AI. */
  public static final String REMOVED = "[REMOVIDO]";

  private static final Pattern EMAIL =
      Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");

  // Portuguese tax numbers (NIF) have nine digits, sometimes written in groups of three.
  private static final Pattern TAX_NUMBER =
      Pattern.compile("(?<!\\d)\\d{3} ?\\d{3} ?\\d{3}(?!\\d)");

  // The holder's name follows a label: the label stays, the value goes.
  private static final Pattern HOLDER_NAME =
      Pattern.compile(
          "(?im)^(\\h*(?:nome|titular|name)\\h*[:\\-]\\h*)\\S.*$", Pattern.UNICODE_CASE);

  /**
   * @throws IllegalArgumentException when there is no entry code: a ticket without a QR code is not
   *     read at all
   */
  public ReadTicket {
    if (entryCode == null || entryCode.isBlank()) {
      throw new IllegalArgumentException("A read ticket has its QR code");
    }
    text = text == null ? "" : text;
  }

  /** The refusal of a file that is not a readable ticket with a QR code; nothing is stored. */
  public static RuleViolationException noQrCode() {
    return new RuleViolationException(
        "QR_CODE_NOT_FOUND", "The file has no readable ticket QR code.");
  }

  /**
   * The ticket's text without personal data and without the entry code, the only part of a ticket
   * that may be sent to the AI: email addresses, tax numbers and the holder's name are removed.
   */
  public String textWithoutPersonalData() {
    String redacted = text.replace(entryCode, REMOVED);
    redacted = EMAIL.matcher(redacted).replaceAll(REMOVED);
    redacted = TAX_NUMBER.matcher(redacted).replaceAll(REMOVED);
    return HOLDER_NAME
        .matcher(redacted)
        .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + REMOVED));
  }

  @Override
  public String toString() {
    return "ReadTicket[entryCode=<hidden>, textLength=" + text.length() + "]";
  }
}
