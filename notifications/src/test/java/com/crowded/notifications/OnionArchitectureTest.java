package com.crowded.notifications;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

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

  /** Its entities guard their state (ADR 0017). */
  @Test
  void entitiesAreClassesThatGuardTheirState() {
    classes()
        .that()
        .resideInAPackage("com.crowded.notifications..domain.model..")
        .should(beGuardedEntitiesWhenTheyHaveAnId())
        .check(NOTIFICATIONS);
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
