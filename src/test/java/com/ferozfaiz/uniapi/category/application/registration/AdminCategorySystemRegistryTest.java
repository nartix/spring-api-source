package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import java.lang.reflect.Field;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdminCategorySystemRegistryTest {

  @Test
  void acceptsAnEmptyContributionSnapshot() {
    AdminCategorySystemRegistry registry = new AdminCategorySystemRegistry(List.of());

    assertThat(registry.registrations()).isEmpty();
    assertThat(registry.findRegistration("unregistered-system")).isEmpty();
  }

  @Test
  void snapshotsCandidatesAndEnumeratesThemByCanonicalSystemKey() {
    AdminCategorySystemRegistration taxonomic =
      AdminCategoryRegistrationFixtures.completeRegistration("taxonomic-fixture", "merchandising");
    AdminCategorySystemRegistration alpha = AdminCategoryRegistrationFixtures.completeRegistration(
      "alpha-system",
      "merchandising"
    );
    List<AdminCategorySystemRegistration> candidates = new ArrayList<>(List.of(taxonomic, alpha));

    AdminCategorySystemRegistry registry = new AdminCategorySystemRegistry(candidates);
    candidates.clear();

    assertThat(registry.registrations())
      .extracting(registration -> registration.systemKey().orElseThrow())
      .containsExactly("alpha-system", "taxonomic-fixture");
  }

  @Test
  void canonicalLookupReturnsTheExactAcceptedInstanceWithoutFallback() {
    AdminCategorySystemRegistration registration =
      AdminCategoryRegistrationFixtures.completeRegistration();
    AdminCategorySystemRegistry registry = new AdminCategorySystemRegistry(List.of(registration));

    assertThat(
      registry.findRegistration(AdminCategoryRegistrationFixtures.DEFAULT_SYSTEM_KEY).orElseThrow()
    ).isSameAs(registration);
    assertThat(registry.findRegistration("another-system")).isEmpty();
  }

  @Test
  void publishesOnlyDeeplyImmutableRegistrationOwnedCollections() {
    AdminCategorySystemRegistration registration =
      AdminCategoryRegistrationFixtures.completeRegistration();
    AdminCategorySystemRegistry registry = new AdminCategorySystemRegistry(List.of(registration));
    AdminCategorySystemDefinition definition = registration.definition().orElseThrow();

    assertThatThrownBy(() -> registry.registrations().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
    assertThatThrownBy(() -> definition.operations().supportedOperations().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
    assertThatThrownBy(() -> definition.ordering().fallbackOrder().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
    assertThatThrownBy(() ->
      registration.runtimeBinding().orElseThrow().permissionMapping().bindings().clear()
    ).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() ->
      registration.runtimeBinding().orElseThrow().policyBindings().values().clear()
    ).isInstanceOf(UnsupportedOperationException.class);
    assertThat(
      Arrays.stream(AdminCategoryRuntimeBinding.class.getDeclaredFields())
        .filter(field -> !field.isSynthetic())
        .map(Field::getName)
    ).containsExactly("identifierCodec", "permissionMapping", "policyBindings", "ports");
  }

  @Test
  void rejectsEitherFinalAggregateSlotBeforePublishingARegistry() {
    AdminCategorySystemDefinition definition =
      AdminCategoryRegistrationFixtures.completeDefinition();
    AdminCategoryRuntimeBinding binding = AdminCategoryRegistrationFixtures.completeRegistration()
      .runtimeBinding()
      .orElseThrow();

    assertMissingComponent(
      new AdminCategorySystemRegistration(Optional.empty(), Optional.of(binding)),
      "<unavailable>",
      AdminCategoryValidationComponent.DEFINITION
    );
    assertMissingComponent(
      new AdminCategorySystemRegistration(Optional.of(definition), Optional.empty()),
      definition.systemKey(),
      AdminCategoryValidationComponent.REGISTRATION
    );
  }

  @Test
  void rejectsNullMembersAndUnsnapshotableCandidateCollectionsSafely() {
    assertRegistryCollectionFailure(() ->
      new AdminCategorySystemRegistry((Collection<AdminCategorySystemRegistration>) null)
    );
    assertRegistryCollectionFailure(() ->
      new AdminCategorySystemRegistry(
        Arrays.asList(AdminCategoryRegistrationFixtures.completeRegistration(), null)
      )
    );
    Collection<AdminCategorySystemRegistration> unsnapshotable = new AbstractCollection<>() {
      @Override
      public Iterator<AdminCategorySystemRegistration> iterator() {
        throw new IllegalStateException("unsafe-source-detail");
      }

      @Override
      public int size() {
        return 1;
      }
    };
    assertRegistryCollectionFailure(() -> new AdminCategorySystemRegistry(unsnapshotable));
  }

  @ParameterizedTest(name = "invalid registry lookup [{index}]")
  @MethodSource("invalidLookupKeys")
  void rejectsEveryInvalidLookupWithoutNormalization(String invalidKey) {
    AdminCategorySystemRegistry registry = new AdminCategorySystemRegistry(
      List.of(AdminCategoryRegistrationFixtures.completeRegistration())
    );

    AdminCategoryRegistrationValidationException failure = assertThrows(
      AdminCategoryRegistrationValidationException.class,
      () -> registry.findRegistration(invalidKey)
    );

    assertThat(failure.diagnostic().safeSystemKey()).isEqualTo("<unavailable>");
    assertThat(failure.diagnostic().category()).isSameAs(
      AdminCategoryValidationCategory.INVALID_SYSTEM_KEY
    );
    assertThat(failure.diagnostic().component()).contains(
      AdminCategoryValidationComponent.REGISTRY
    );
    assertThat(failure.diagnostic().capability()).isEmpty();
    assertThat(failure.diagnostic().policyReference()).isEmpty();
  }

  @Test
  void rejectsEveryCandidateParticipatingInADuplicateDefinitionKeyGroup() {
    AdminCategorySystemRegistration first = AdminCategoryRegistrationFixtures.completeRegistration(
      "taxonomic-fixture",
      "merchandising"
    );
    AdminCategorySystemRegistration duplicate =
      AdminCategoryRegistrationFixtures.completeRegistration("taxonomic-fixture", "another-module");

    AdminCategoryRegistrationAggregateValidationException failure = aggregateFailure(() ->
      new AdminCategorySystemRegistry(List.of(first, duplicate))
    );

    assertThat(failure.diagnostics()).hasSize(2);
    assertThat(failure.diagnostics()).allSatisfy(diagnostic -> {
      assertThat(diagnostic.safeSystemKey()).isEqualTo("taxonomic-fixture");
      assertThat(diagnostic.category()).isSameAs(
        AdminCategoryValidationCategory.DUPLICATE_SYSTEM_KEY
      );
      assertThat(diagnostic.component()).contains(AdminCategoryValidationComponent.REGISTRATION);
    });
    assertThat(failure.omittedCount()).isZero();
  }

  @Test
  void rejectsExactRuntimePortReferenceReuseAcrossDistinctSystemKeys() {
    AdminCategoryReadPort sharedPort = mock(AdminCategoryReadPort.class);
    AdminCategorySystemDefinition alpha = AdminCategoryRegistrationFixtures.completeDefinition(
      "alpha-system",
      "merchandising"
    );
    AdminCategorySystemDefinition beta = AdminCategoryRegistrationFixtures.completeDefinition(
      "beta-system",
      "merchandising"
    );

    AdminCategoryRegistrationAggregateValidationException failure = aggregateFailure(() ->
      new AdminCategorySystemRegistry(
        List.of(
          registrationWithCategoryRead(alpha, sharedPort),
          registrationWithCategoryRead(beta, sharedPort)
        )
      )
    );

    assertThat(failure.diagnostics()).hasSize(2);
    assertThat(failure.diagnostics())
      .extracting(AdminCategoryValidationDiagnostic::safeSystemKey)
      .containsExactly("alpha-system", "beta-system");
    assertThat(failure.diagnostics()).allSatisfy(diagnostic -> {
      assertThat(diagnostic.category()).isSameAs(
        AdminCategoryValidationCategory.DUPLICATE_RUNTIME_PORT_OWNERSHIP
      );
      assertThat(diagnostic.component()).contains(AdminCategoryValidationComponent.RUNTIME_PORTS);
    });
  }

  @Test
  void mixedValidAndInvalidCandidatesPublishNoPartialRegistry() {
    AdminCategorySystemRegistration valid =
      AdminCategoryRegistrationFixtures.completeRegistration();
    AdminCategorySystemRegistration invalid = new AdminCategorySystemRegistration(
      Optional.empty(),
      Optional.empty()
    );

    AdminCategoryRegistrationAggregateValidationException failure = aggregateFailure(() ->
      new AdminCategorySystemRegistry(List.of(valid, invalid))
    );

    assertThat(failure.diagnostics())
      .extracting(diagnostic -> diagnostic.component().orElseThrow())
      .containsExactly(
        AdminCategoryValidationComponent.DEFINITION,
        AdminCategoryValidationComponent.REGISTRATION
      );
    AdminCategorySystemRegistry accepted = new AdminCategorySystemRegistry(List.of(valid));
    assertThat(accepted.registrations()).containsExactly(valid);
  }

  private static void assertMissingComponent(
    AdminCategorySystemRegistration candidate,
    String safeSystemKey,
    AdminCategoryValidationComponent component
  ) {
    AdminCategoryRegistrationAggregateValidationException failure = aggregateFailure(() ->
      new AdminCategorySystemRegistry(List.of(candidate))
    );

    assertThat(failure.diagnostics())
      .singleElement()
      .satisfies(diagnostic -> {
        assertThat(diagnostic.safeSystemKey()).isEqualTo(safeSystemKey);
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.MISSING_COMPONENT
        );
        assertThat(diagnostic.capability()).isEmpty();
        assertThat(diagnostic.policyReference()).isEmpty();
      });
    assertThat(failure.diagnostics())
      .extracting(diagnostic -> diagnostic.component().orElseThrow())
      .containsExactly(component);
    assertThat(failure.omittedCount()).isZero();
  }

  private static void assertRegistryCollectionFailure(Executable executable) {
    AdminCategoryRegistrationValidationException failure = assertThrows(
      AdminCategoryRegistrationValidationException.class,
      executable
    );

    assertThat(failure.diagnostic().safeSystemKey()).isEqualTo("<unavailable>");
    assertThat(failure.diagnostic().category()).isSameAs(
      AdminCategoryValidationCategory.INVALID_COLLECTION
    );
    assertThat(failure.diagnostic().component()).contains(
      AdminCategoryValidationComponent.REGISTRY
    );
    assertThat(failure.diagnostic().capability()).isEmpty();
    assertThat(failure.diagnostic().policyReference()).isEmpty();
  }

  private static AdminCategorySystemRegistration registrationWithCategoryRead(
    AdminCategorySystemDefinition definition,
    AdminCategoryReadPort categoryRead
  ) {
    AdminCategoryRuntimeBinding template = AdminCategoryRegistrationFixtures.completeRegistration()
      .runtimeBinding()
      .orElseThrow();
    AdminCategoryRuntimePorts templatePorts = template.ports();
    AdminCategoryRuntimePorts ports = new AdminCategoryRuntimePorts(
      Optional.of(categoryRead),
      templatePorts.hierarchyRead(),
      templatePorts.basicMutation(),
      templatePorts.hierarchyMutation(),
      templatePorts.ordering(),
      templatePorts.lifecycle()
    );
    AdminCategoryPermissionMapping permissions =
      AdminCategoryRegistrationFixtures.completePermissionMapping(definition.systemKey());
    AdminCategoryRuntimePolicyBindings policies =
      AdminCategoryRegistrationFixtures.runtimePolicyBindings(
        AdminCategoryRegistrationFixtures.completePolicyReferences()
      );
    return AdminCategorySystemRegistration.of(
      definition,
      runtimeBinding(template.identifierCodec(), permissions, policies, ports)
    );
  }

  private static AdminCategoryRuntimeBinding runtimeBinding(
    AdminCategoryIdentifierCodec<?> codec,
    AdminCategoryPermissionMapping permissions,
    AdminCategoryRuntimePolicyBindings policies,
    AdminCategoryRuntimePorts ports
  ) {
    try {
      for (var constructor : AdminCategoryRuntimeBinding.class.getConstructors()) {
        if (constructor.getParameterCount() == 4) {
          return (AdminCategoryRuntimeBinding) constructor.newInstance(
            codec,
            permissions,
            policies,
            ports
          );
        }
        if (constructor.getParameterCount() == 5) {
          return (AdminCategoryRuntimeBinding) constructor.newInstance(
            codec,
            permissions,
            policies,
            ports,
            Optional.empty()
          );
        }
      }
      throw new AssertionError("Expected the runtime-binding constructor");
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("Could not construct a runtime binding", exception);
    }
  }

  private static AdminCategoryRegistrationAggregateValidationException aggregateFailure(
    Executable executable
  ) {
    return assertThrows(AdminCategoryRegistrationAggregateValidationException.class, executable);
  }

  private static Stream<String> invalidLookupKeys() {
    return Stream.concat(
      Stream.of((String) null),
      Stream.of(
        "",
        " ",
        "taxonomic-fixture ",
        " taxonomic-fixture",
        "Taxonomic-fixture",
        "taxonomic_Fixture",
        "1-taxonomic",
        "-taxonomic",
        "taxonomic-",
        "taxonomic--fixture",
        "taxonomic.fixture",
        "táxonomic",
        "a".repeat(64)
      )
    );
  }
}
