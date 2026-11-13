package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which report categories ask other participants to confirm a report at an event, and the rules of
 * those confirmations.
 *
 * @param promptCategories codes of the categories that ask for confirmations
 */
public record ConfirmationSettings(Set<String> promptCategories, ConfirmationRules rules) {

  public ConfirmationSettings {
    promptCategories = Set.copyOf(promptCategories);
    Objects.requireNonNull(rules, "rules");
  }

  /** The categories enabled by default and the default rules, for an event that chose nothing. */
  public static ConfirmationSettings defaults(Collection<ReportCategory> catalog) {
    return new ConfirmationSettings(
        catalog.stream()
            .filter(c -> c.promptsEnabledByDefault() && !c.personal())
            .map(ReportCategory::code)
            .collect(Collectors.toSet()),
        ConfirmationRules.DEFAULT);
  }

  /**
   * The settings an organization chose.
   *
   * @param catalog every report category
   * @throws RuleViolationException when a code is not a category, or the category is personal
   */
  public static ConfirmationSettings chosen(
      Set<String> codes, ConfirmationRules rules, Collection<ReportCategory> catalog) {
    Map<String, ReportCategory> byCode =
        catalog.stream().collect(Collectors.toMap(ReportCategory::code, Function.identity()));
    for (String code : new TreeSet<>(codes)) {
      ReportCategory category = byCode.get(code);
      if (category == null) {
        throw new RuleViolationException(
            "REPORT_CATEGORY_UNKNOWN", "There is no report category " + code + ".");
      }
      if (category.personal()) {
        throw new RuleViolationException(
            "PERSONAL_CATEGORY_NOT_CONFIRMED",
            "Reports in a personal category never ask others to confirm them.");
      }
    }
    return new ConfirmationSettings(codes, rules);
  }

  /** Whether reports in {@code category} ask other participants to confirm them. */
  public boolean promptsFor(String category) {
    return promptCategories.contains(category);
  }
}
