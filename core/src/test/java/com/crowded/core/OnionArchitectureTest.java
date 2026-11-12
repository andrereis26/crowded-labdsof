package com.crowded.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

/**
 * Core follows the onion architecture: every module ({@code event}, {@code user}, ...) has a {@code
 * domain} at its centre, an {@code application} around it, and the adapters outside ({@code api},
 * {@code infrastructure.persistence}, {@code infrastructure.messaging}, {@code
 * infrastructure.config}). Dependencies only point inwards, and adapters never use each other. The
 * domain is a rich model (ADR 0017): its entities guard their invariants, and only the domain
 * refuses a request for breaking a rule.
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

  @Test
  void entitiesAreClassesThatGuardTheirState() {
    classes()
        .that()
        .resideInAPackage("com.crowded.core..domain.model..")
        .should(beGuardedEntitiesWhenTheyHaveAnId())
        .check(CORE);
  }

  @Test
  void onlyTheDomainRefusesARuleViolation() {
    noClasses()
        .that()
        .resideOutsideOfPackage("com.crowded.core..domain..")
        .should()
        .callConstructor(RuleViolationException.class, String.class, String.class)
        .because("business rules live on the model (ADR 0017)")
        .check(CORE);
  }

  /**
   * A type with an {@code id} is an entity: a class, not a record, without a public constructor or
   * a setter, so it changes only through its domain methods.
   */
  private static ArchCondition<JavaClass> beGuardedEntitiesWhenTheyHaveAnId() {
    return new ArchCondition<>(
        "be classes without public constructors or setters when they have an id") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        if (type.tryGetField("id").isEmpty()) {
          return;
        }
        if (type.isAssignableTo(Record.class)) {
          events.add(
              SimpleConditionEvent.violated(
                  type, type.getName() + " has an id, so it is an entity: make it a class"));
        }
        type.getConstructors().stream()
            .filter(constructor -> constructor.getModifiers().contains(JavaModifier.PUBLIC))
            .forEach(
                constructor ->
                    events.add(
                        SimpleConditionEvent.violated(
                            type,
                            constructor.getFullName()
                                + " is public: create the entity through a domain factory")));
        type.getMethods().stream()
            .filter(method -> method.getName().startsWith("set"))
            .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
            .forEach(
                method ->
                    events.add(
                        SimpleConditionEvent.violated(
                            type,
                            method.getFullName()
                                + " is a setter: change the entity through a domain method")));
      }
    };
  }
}
