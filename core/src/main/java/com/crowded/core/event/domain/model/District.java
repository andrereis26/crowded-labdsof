package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.Set;

/**
 * The district of mainland Portugal, or the autonomous region, where an event takes place, named as
 * in IPMA's weather warnings, which it selects.
 */
public record District(String name) {

  static final Set<String> NAMES =
      Set.of(
          "Aveiro",
          "Beja",
          "Braga",
          "Bragança",
          "Castelo Branco",
          "Coimbra",
          "Évora",
          "Faro",
          "Guarda",
          "Leiria",
          "Lisboa",
          "Portalegre",
          "Porto",
          "Santarém",
          "Setúbal",
          "Viana do Castelo",
          "Vila Real",
          "Viseu",
          "Região Autónoma dos Açores",
          "Região Autónoma da Madeira");

  /**
   * @throws RuleViolationException when it is not one of those districts or regions
   */
  public District {
    name = name == null ? "" : name.strip();
    if (!NAMES.contains(name)) {
      throw new RuleViolationException(
          "DISTRICT_UNKNOWN", "Choose a district of Portugal or an autonomous region.");
    }
  }
}
