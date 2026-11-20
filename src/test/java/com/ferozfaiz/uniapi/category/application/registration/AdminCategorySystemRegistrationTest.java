package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AdminCategorySystemRegistrationTest {

  @Test
  void atomicFactoryPublishesOnlyTheDefinitionAndRuntimeBinding() {
    AdminCategorySystemDefinition definition =
      AdminCategoryRegistrationFixtures.completeDefinition();
    AdminCategoryRuntimeBinding binding = AdminCategoryRegistrationFixtures.completeRegistration()
      .runtimeBinding()
      .orElseThrow();

    AdminCategorySystemRegistration registration = AdminCategorySystemRegistration.of(
      definition,
      binding
    );

    assertThat(registration.definition()).containsSame(definition);
    assertThat(registration.runtimeBinding()).containsSame(binding);
    assertThat(registration.systemKey()).contains(definition.systemKey());
  }

  @Test
  void finalAggregateHasExactlyTwoFieldsOneConstructorAndThreeAccessors() {
    assertThat(
      Arrays.stream(AdminCategorySystemRegistration.class.getDeclaredFields())
        .filter(field -> !field.isSynthetic())
        .map(Field::getName)
    ).containsExactly("definition", "runtimeBinding");
    assertThat(
      Arrays.stream(AdminCategorySystemRegistration.class.getDeclaredConstructors()).map(
        Constructor::getParameterTypes
      )
    ).containsExactly(new Class<?>[] { Optional.class, Optional.class });
    assertThat(
      Arrays.stream(AdminCategorySystemRegistration.class.getDeclaredMethods())
        .filter(method -> Modifier.isPublic(method.getModifiers()))
        .map(Method::getName)
    ).containsExactlyInAnyOrder("of", "definition", "runtimeBinding", "systemKey");
  }

  @Test
  void explicitMissingValuesRemainRepresentableOnlyAsAnUnpublishedCandidate() {
    AdminCategorySystemRegistration candidate = new AdminCategorySystemRegistration(
      Optional.empty(),
      Optional.empty()
    );

    assertThat(candidate.definition()).isEmpty();
    assertThat(candidate.runtimeBinding()).isEmpty();
    assertThat(candidate.systemKey()).isEmpty();
  }

  @Test
  void retainedCollaboratorsUseReferenceIdentity() {
    AdminCategorySystemDefinition definition =
      AdminCategoryRegistrationFixtures.completeDefinition();
    AdminCategoryRuntimeBinding binding = AdminCategoryRegistrationFixtures.completeRegistration()
      .runtimeBinding()
      .orElseThrow();

    AdminCategorySystemRegistration first = AdminCategorySystemRegistration.of(definition, binding);
    AdminCategorySystemRegistration second = AdminCategorySystemRegistration.of(
      definition,
      binding
    );

    assertThat(first.definition().orElseThrow()).isSameAs(definition);
    assertThat(first.runtimeBinding().orElseThrow()).isSameAs(binding);
    assertThat(first).isNotSameAs(second).isNotEqualTo(second);
  }

  @Test
  void rejectsEachNullOptionalWithItsFinalComponentAndSafeKey() {
    AdminCategorySystemDefinition definition =
      AdminCategoryRegistrationFixtures.completeDefinition();
    AdminCategoryRuntimeBinding binding = AdminCategoryRegistrationFixtures.completeRegistration()
      .runtimeBinding()
      .orElseThrow();

    assertInvalidSlot(
      () -> new AdminCategorySystemRegistration(null, Optional.of(binding)),
      "<unavailable>",
      AdminCategoryValidationComponent.DEFINITION
    );
    assertInvalidSlot(
      () -> new AdminCategorySystemRegistration(Optional.of(definition), null),
      definition.systemKey(),
      AdminCategoryValidationComponent.REGISTRATION
    );
  }

  @Test
  void atomicFactoryMapsEachNullWithoutLeakingNullPointerException() {
    AdminCategorySystemDefinition definition =
      AdminCategoryRegistrationFixtures.completeDefinition();
    AdminCategoryRuntimeBinding binding = AdminCategoryRegistrationFixtures.completeRegistration()
      .runtimeBinding()
      .orElseThrow();

    assertInvalidSlot(
      () -> AdminCategorySystemRegistration.of(null, binding),
      "<unavailable>",
      AdminCategoryValidationComponent.DEFINITION
    );
    assertInvalidSlot(
      () -> AdminCategorySystemRegistration.of(definition, null),
      definition.systemKey(),
      AdminCategoryValidationComponent.REGISTRATION
    );
  }

  private static void assertInvalidSlot(
    Executable executable,
    String safeSystemKey,
    AdminCategoryValidationComponent component
  ) {
    AdminCategoryRegistrationValidationException failure = assertThrows(
      AdminCategoryRegistrationValidationException.class,
      executable
    );

    assertThat(failure.diagnostic().safeSystemKey()).isEqualTo(safeSystemKey);
    assertThat(failure.diagnostic().category()).isSameAs(
      AdminCategoryValidationCategory.INVALID_COMPONENT_SLOT
    );
    assertThat(failure.diagnostic().component()).contains(component);
    assertThat(failure.diagnostic().capability()).isEmpty();
    assertThat(failure.diagnostic().policyReference()).isEmpty();
  }
}
