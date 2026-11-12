package com.crowded.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Core follows the onion architecture: every module ({@code event}, {@code user}, ...) has a {@code
 * domain} at its centre, an {@code application} around it, and the adapters outside ({@code api},
 * {@code infrastructure.persistence}, {@code infrastructure.messaging}, {@code
 * infrastructure.config}). Dependencies only point inwards, and adapters never use each other.
 */
class OnionArchitectureTest {

  private static final JavaClasses CORE =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.crowded.core");

  @Test
  void dependenciesPointInwards() {
    onionArchitecture()
        .domainModels("com.crowded.core..domain.model..", "com.crowded.core..domain.exception..")
        .domainServices(
            "com.crowded.core..domain.service..", "com.crowded.core..domain.repository..")
        .applicationServices("com.crowded.core..application..")
        .adapter("api", "com.crowded.core..api..")
        .adapter("persistence", "com.crowded.core..infrastructure.persistence..")
        .adapter("messaging", "com.crowded.core..infrastructure.messaging..")
        .adapter("config", "com.crowded.core..infrastructure.config..")
        .check(CORE);
  }

  @Test
  void theDomainUsesNoFramework() {
    noClasses()
        .that()
        .resideInAPackage("com.crowded.core..domain..")
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
        .check(CORE);
  }

  @Test
  void onlyTheApiUsesTheWeb() {
    noClasses()
        .that()
        .resideOutsideOfPackages("com.crowded.core..api..", "com.crowded.core..infrastructure..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..", "org.springframework.http..")
        .check(CORE);
  }
}
