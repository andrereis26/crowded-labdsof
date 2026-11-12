package com.crowded.notifications;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Notifications follows the onion architecture, as Core does: every module ({@code notification},
 * {@code shared}) has a {@code domain} at its centre, an {@code application} around it, and the
 * adapters outside ({@code api}, {@code infrastructure.persistence}, {@code
 * infrastructure.messaging}, {@code infrastructure.config}). Dependencies only point inwards, and
 * adapters never use each other.
 */
class OnionArchitectureTest {

  private static final JavaClasses NOTIFICATIONS =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.crowded.notifications");

  @Test
  void dependenciesPointInwards() {
    onionArchitecture()
        .domainModels(
            "com.crowded.notifications..domain.model..",
            "com.crowded.notifications..domain.exception..")
        .domainServices(
            "com.crowded.notifications..domain.service..",
            "com.crowded.notifications..domain.repository..")
        .applicationServices("com.crowded.notifications..application..")
        .adapter("api", "com.crowded.notifications..api..")
        .adapter("persistence", "com.crowded.notifications..infrastructure.persistence..")
        .adapter("messaging", "com.crowded.notifications..infrastructure.messaging..")
        .adapter("config", "com.crowded.notifications..infrastructure.config..")
        .withOptionalLayers(true)
        .check(NOTIFICATIONS);
  }

  @Test
  void theDomainUsesNoFramework() {
    noClasses()
        .that()
        .resideInAPackage("com.crowded.notifications..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "jakarta.validation..",
            "org.hibernate..",
            "tools.jackson..",
            "com.fasterxml.jackson..",
            "org.mapstruct..")
        .check(NOTIFICATIONS);
  }
}
