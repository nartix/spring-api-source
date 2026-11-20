package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class AdminCategoryValidationContractsTest {

  @Test
  void exposesExactlyTheTwentyOneStableValidationCategoryCodes() {
    assertThat(AdminCategoryValidationCategory.values())
      .extracting(AdminCategoryValidationCategory::code)
      .containsExactly(
        "invalid-system-key",
        "invalid-owning-module",
        "invalid-identifier",
        "invalid-profile",
        "invalid-operation-declaration",
        "invalid-hierarchy",
        "invalid-ordering-ranking",
        "invalid-lifecycle",
        "invalid-logical-reference",
        "invalid-limit",
        "invalid-permission-mapping",
        "invalid-policy-reference",
        "invalid-collection",
        "invalid-component-slot",
        "duplicate-system-key",
        "duplicate-runtime-port-ownership",
        "missing-component",
        "profile-codec-incompatibility",
        "missing-collaborator",
        "unexpected-collaborator"
      );
  }

  @Test
  void exposesExactlyTheSevenStableValidationComponentCodes() {
    assertThat(AdminCategoryValidationComponent.values())
      .extracting(AdminCategoryValidationComponent::code)
      .containsExactly(
        "definition",
        "identifier-codec",
        "permission-mapping",
        "policy-references",
        "runtime-binding",
        "runtime-ports",
        "runtime-policy-bindings",
        "registration-source",
        "registration",
        "registry"
      );
  }

  @Test
  void exposesExactlyTheTwentyOneStableValidationCapabilityCodes() {
    assertThat(AdminCategoryValidationCapability.values())
      .extracting(AdminCategoryValidationCapability::code)
      .containsExactly(
        "capabilities",
        "list",
        "tree",
        "parent-options",
        "get",
        "breadcrumb",
        "create",
        "replace",
        "patch",
        "delete",
        "move",
        "reorder",
        "activate",
        "deactivate",
        "search",
        "filter",
        "sort",
        "hierarchy",
        "ordering",
        "ranking",
        "lifecycle"
      );
  }

  @Test
  void comparesCategoriesByTheirExplicitCodesRatherThanEnumOrder() {
    AdminCategoryValidationDiagnostic systemKey = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_SYSTEM_KEY
    );
    AdminCategoryValidationDiagnostic owningModule = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_OWNING_MODULE
    );

    assertThat(List.of(systemKey, owningModule).stream().sorted().toList()).containsExactly(
      owningModule,
      systemKey
    );
  }

  @Test
  void comparesEveryTypedContextByCodeWithAbsentBeforePresent() {
    AdminCategoryPolicyReference generalAlpha = AdminCategoryRegistrationFixtures.policyReference(
      AdminCategoryPolicyObligation.GENERAL_VALIDATION,
      "alpha-module",
      "alpha-policy"
    );
    AdminCategoryPolicyReference generalOmega = AdminCategoryRegistrationFixtures.policyReference(
      AdminCategoryPolicyObligation.GENERAL_VALIDATION,
      "omega-module",
      "alpha-policy"
    );
    AdminCategoryPolicyReference hierarchy = AdminCategoryRegistrationFixtures.policyReference(
      AdminCategoryPolicyObligation.HIERARCHY,
      "alpha-module",
      "alpha-policy"
    );

    AdminCategoryValidationDiagnostic noComponent = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION
    );
    AdminCategoryValidationDiagnostic definitionOnly = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      Optional.of(AdminCategoryValidationComponent.DEFINITION),
      Optional.empty(),
      Optional.empty()
    );
    AdminCategoryValidationDiagnostic providerOnly = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
      Optional.empty(),
      Optional.empty()
    );
    AdminCategoryValidationDiagnostic providerNoCapability = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
      Optional.empty(),
      Optional.of(generalAlpha)
    );
    AdminCategoryValidationDiagnostic providerGet = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
      Optional.of(AdminCategoryValidationCapability.GET),
      Optional.empty()
    );
    AdminCategoryValidationDiagnostic providerList = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
      Optional.of(AdminCategoryValidationCapability.LIST),
      Optional.empty()
    );
    AdminCategoryValidationDiagnostic providerListNoPolicy = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
      Optional.of(AdminCategoryValidationCapability.LIST),
      Optional.empty()
    );
    AdminCategoryValidationDiagnostic providerListGeneralAlpha = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
      Optional.of(AdminCategoryValidationCapability.LIST),
      Optional.of(generalAlpha)
    );
    AdminCategoryValidationDiagnostic providerListGeneralOmega = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
      Optional.of(AdminCategoryValidationCapability.LIST),
      Optional.of(generalOmega)
    );
    AdminCategoryValidationDiagnostic providerListHierarchy = diagnostic(
      "stable-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
      Optional.of(AdminCategoryValidationCapability.LIST),
      Optional.of(hierarchy)
    );

    List<AdminCategoryValidationDiagnostic> shuffled = new ArrayList<>(
      List.of(
        providerListHierarchy,
        providerListGeneralOmega,
        providerListGeneralAlpha,
        providerListNoPolicy,
        providerList,
        providerGet,
        providerNoCapability,
        providerOnly,
        definitionOnly,
        noComponent
      )
    );
    Collections.reverse(shuffled);

    assertThat(shuffled.stream().sorted().toList()).containsExactly(
      noComponent,
      definitionOnly,
      providerOnly,
      providerNoCapability,
      providerGet,
      providerList,
      providerListNoPolicy,
      providerListGeneralAlpha,
      providerListGeneralOmega,
      providerListHierarchy
    );
  }

  @Test
  void comparesSafeSystemKeysBeforeEveryOtherField() {
    AdminCategoryValidationDiagnostic alpha = diagnostic(
      "alpha-key",
      AdminCategoryValidationCategory.UNEXPECTED_COLLABORATOR,
      Optional.of(AdminCategoryValidationComponent.REGISTRY),
      Optional.of(AdminCategoryValidationCapability.TREE),
      Optional.of(
        AdminCategoryRegistrationFixtures.policyReference(
          AdminCategoryPolicyObligation.QUERY,
          "omega-module",
          "omega-policy"
        )
      )
    );
    AdminCategoryValidationDiagnostic omega = diagnostic(
      "omega-key",
      AdminCategoryValidationCategory.INVALID_COLLECTION
    );

    assertThat(List.of(omega, alpha).stream().sorted().toList()).containsExactly(alpha, omega);
  }

  @Test
  void acceptsOnlyCanonicalKeysOrTheExactUnavailableSentinel() {
    AdminCategoryValidationDiagnostic unavailable = diagnostic(
      AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
      AdminCategoryValidationCategory.INVALID_SYSTEM_KEY
    );

    assertThat(unavailable.safeSystemKey()).isEqualTo("<unavailable>");
    assertThatThrownBy(() ->
      diagnostic("<UNAVAILABLE>", AdminCategoryValidationCategory.INVALID_SYSTEM_KEY)
    )
      .isInstanceOf(AdminCategoryRegistrationValidationException.class)
      .satisfies(throwable ->
        assertInvalidContractDiagnostic(
          ((AdminCategoryRegistrationValidationException) throwable).diagnostic()
        )
      );
  }

  @Test
  void rejectsNullDiagnosticFieldsThroughOneSafeTypedViolation() {
    assertInvalidContract(() ->
      new AdminCategoryValidationDiagnostic(
        "stable-key",
        null,
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
      )
    );
    assertInvalidContract(() ->
      new AdminCategoryValidationDiagnostic(
        "stable-key",
        AdminCategoryValidationCategory.INVALID_COLLECTION,
        null,
        Optional.empty(),
        Optional.empty()
      )
    );
    assertInvalidContract(() ->
      new AdminCategoryValidationDiagnostic(
        "stable-key",
        AdminCategoryValidationCategory.INVALID_COLLECTION,
        Optional.empty(),
        null,
        Optional.empty()
      )
    );
    assertInvalidContract(() ->
      new AdminCategoryValidationDiagnostic(
        "stable-key",
        AdminCategoryValidationCategory.INVALID_COLLECTION,
        Optional.empty(),
        Optional.empty(),
        null
      )
    );
  }

  @Test
  void boundedAccumulatorRetainsTheSmallestHundredOccurrencesInOrder() {
    BoundedRegistrationDiagnosticAccumulator accumulator =
      new BoundedRegistrationDiagnosticAccumulator();
    List<AdminCategoryValidationDiagnostic> all = new ArrayList<>();
    for (int index = 0; index < 137; index++) {
      all.add(
        diagnostic(
          "system-" + String.format("%03d", index),
          AdminCategoryValidationCategory.MISSING_COMPONENT
        )
      );
    }

    List<AdminCategoryValidationDiagnostic> reverse = new ArrayList<>(all);
    Collections.reverse(reverse);
    reverse.forEach(accumulator::add);

    assertThat(accumulator.isEmpty()).isFalse();
    assertThat(accumulator.retainedInOrder()).containsExactlyElementsOf(all.subList(0, 100));
    assertThat(accumulator.retainedInOrder()).isUnmodifiable();
    assertThat(accumulator.omittedCount()).isEqualTo(37L);
  }

  @Test
  void boundedAccumulatorPreservesDuplicateOccurrences() {
    BoundedRegistrationDiagnosticAccumulator accumulator =
      new BoundedRegistrationDiagnosticAccumulator();
    AdminCategoryValidationDiagnostic duplicate = diagnostic(
      "duplicate-key",
      AdminCategoryValidationCategory.MISSING_COMPONENT,
      Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
      Optional.empty(),
      Optional.empty()
    );

    for (int index = 0; index < 101; index++) {
      accumulator.add(duplicate);
    }

    assertThat(accumulator.retainedInOrder()).hasSize(100).allMatch(duplicate::equals);
    assertThat(accumulator.omittedCount()).isOne();
  }

  @Test
  void singleExceptionCarriesTheTypedDiagnosticAndRendersOnlySafeFields() {
    AdminCategoryValidationDiagnostic diagnostic = diagnostic(
      "safe-key",
      AdminCategoryValidationCategory.UNEXPECTED_COLLABORATOR,
      Optional.of(AdminCategoryValidationComponent.POLICY_REFERENCES),
      Optional.empty(),
      Optional.of(
        AdminCategoryRegistrationFixtures.policyReference(
          AdminCategoryPolicyObligation.QUERY,
          "governance",
          "query-policy"
        )
      )
    );

    AdminCategoryRegistrationValidationException first =
      new AdminCategoryRegistrationValidationException(diagnostic);
    AdminCategoryRegistrationValidationException second =
      new AdminCategoryRegistrationValidationException(diagnostic);

    assertThat(first.diagnostic()).isEqualTo(diagnostic);
    assertThat(first.diagnostic().policyObligation()).contains(AdminCategoryPolicyObligation.QUERY);
    assertThat(first.diagnostic().policyReference()).isEmpty();
    assertThat(first.getMessage())
      .isEqualTo(second.getMessage())
      .contains("safe-key")
      .contains("unexpected-collaborator")
      .contains("policy-references")
      .contains("query")
      .doesNotContain("governance")
      .doesNotContain("query-policy")
      .doesNotContain("AdminCategoryPolicyReference");
  }

  @Test
  void aggregateExceptionCopiesSortedRetainedDiagnosticsAndExactOmittedCount() {
    AdminCategoryValidationDiagnostic alpha = diagnostic(
      "alpha-key",
      AdminCategoryValidationCategory.MISSING_COMPONENT
    );
    AdminCategoryValidationDiagnostic omega = diagnostic(
      "omega-key",
      AdminCategoryValidationCategory.MISSING_COMPONENT
    );
    List<AdminCategoryValidationDiagnostic> source = new ArrayList<>(List.of(alpha, omega));

    AdminCategoryRegistrationAggregateValidationException exception =
      new AdminCategoryRegistrationAggregateValidationException(source, 7L);
    source.clear();

    assertThat(exception.diagnostics()).containsExactly(alpha, omega).isUnmodifiable();
    assertThat(exception.omittedCount()).isEqualTo(7L);
    assertThat(exception.getMessage())
      .contains("alpha-key")
      .contains("omega-key")
      .contains("7")
      .doesNotContain("omitted-secret");
  }

  @Test
  void aggregateExceptionRejectsInvalidRetainedStateThroughOneTypedViolation() {
    AdminCategoryValidationDiagnostic alpha = diagnostic(
      "alpha-key",
      AdminCategoryValidationCategory.MISSING_COMPONENT
    );
    AdminCategoryValidationDiagnostic omega = diagnostic(
      "omega-key",
      AdminCategoryValidationCategory.MISSING_COMPONENT
    );

    assertInvalidContract(() ->
      new AdminCategoryRegistrationAggregateValidationException(null, 0L)
    );
    assertInvalidContract(() ->
      new AdminCategoryRegistrationAggregateValidationException(List.of(), 0L)
    );
    assertInvalidContract(() ->
      new AdminCategoryRegistrationAggregateValidationException(
        Arrays.asList((AdminCategoryValidationDiagnostic) null),
        0L
      )
    );
    assertInvalidContract(() ->
      new AdminCategoryRegistrationAggregateValidationException(Collections.nCopies(101, alpha), 1L)
    );
    assertInvalidContract(() ->
      new AdminCategoryRegistrationAggregateValidationException(List.of(alpha), -1L)
    );
    assertInvalidContract(() ->
      new AdminCategoryRegistrationAggregateValidationException(List.of(omega, alpha), 0L)
    );
  }

  @Test
  void singleExceptionRejectsANullDiagnosticThroughOneTypedViolation() {
    assertInvalidContract(() -> new AdminCategoryRegistrationValidationException(null));
  }

  @Test
  void aggregateRenderingIsBoundedToTheRetainedHundred() {
    List<AdminCategoryValidationDiagnostic> retained = new ArrayList<>();
    for (int index = 0; index < 100; index++) {
      retained.add(
        diagnostic(
          "retained-" + String.format("%03d", index),
          AdminCategoryValidationCategory.MISSING_COMPONENT
        )
      );
    }

    AdminCategoryRegistrationAggregateValidationException exception =
      new AdminCategoryRegistrationAggregateValidationException(retained, 9_999L);

    assertThat(exception.getMessage())
      .contains("retained-000")
      .contains("retained-099")
      .contains("9999")
      .doesNotContain("omitted-value")
      .hasSizeLessThan(20_000);
  }

  private static AdminCategoryValidationDiagnostic diagnostic(
    String safeSystemKey,
    AdminCategoryValidationCategory category
  ) {
    return diagnostic(
      safeSystemKey,
      category,
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );
  }

  private static AdminCategoryValidationDiagnostic diagnostic(
    String safeSystemKey,
    AdminCategoryValidationCategory category,
    Optional<AdminCategoryValidationComponent> component,
    Optional<AdminCategoryValidationCapability> capability,
    Optional<AdminCategoryPolicyReference> policyReference
  ) {
    return new AdminCategoryValidationDiagnostic(
      safeSystemKey,
      category,
      component,
      capability,
      policyReference
    );
  }

  private static void assertInvalidContract(ThrowingCallable action) {
    AdminCategoryRegistrationValidationException exception = catchThrowableOfType(
      action,
      AdminCategoryRegistrationValidationException.class
    );

    assertThat(exception).isNotNull();
    assertInvalidContractDiagnostic(exception.diagnostic());
  }

  private static void assertInvalidContractDiagnostic(
    AdminCategoryValidationDiagnostic diagnostic
  ) {
    assertThat(diagnostic.safeSystemKey()).isEqualTo("<unavailable>");
    assertThat(diagnostic.category()).isSameAs(AdminCategoryValidationCategory.INVALID_COLLECTION);
    assertThat(diagnostic.component()).contains(AdminCategoryValidationComponent.REGISTRATION);
    assertThat(diagnostic.capability()).isEmpty();
    assertThat(diagnostic.policyReference()).isEmpty();
  }
}
