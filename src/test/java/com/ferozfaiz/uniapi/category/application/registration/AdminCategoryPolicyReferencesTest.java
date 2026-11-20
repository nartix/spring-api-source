package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdminCategoryPolicyReferencesTest {

  @Test
  void exposesTheExactObligationCodes() {
    assertThat(AdminCategoryPolicyObligation.values())
      .extracting(AdminCategoryPolicyObligation::code)
      .containsExactly(
        "general-validation",
        "query",
        "hierarchy",
        "mutation-validation",
        "ordering",
        "deletion",
        "lifecycle"
      );
  }

  @Test
  void logicalPolicyIdsUseExactValueEqualityParsingAndRendering() {
    AdminCategoryLogicalPolicyId first = new AdminCategoryLogicalPolicyId(
      "governance",
      "query-rules"
    );
    AdminCategoryLogicalPolicyId equal = AdminCategoryLogicalPolicyId.parse(
      "governance:query-rules"
    );

    assertThat(first.value()).isEqualTo("governance:query-rules");
    assertThat(first).isEqualTo(equal);
    assertThat(first.hashCode()).isEqualTo(equal.hashCode());
  }

  @Test
  void canonicalizesReferencesByStableObligationCodeAndSupportsLookup() {
    AdminCategoryPolicyReferences references =
      AdminCategoryRegistrationFixtures.completePolicyReferences();

    assertThat(references.references())
      .extracting(AdminCategoryPolicyReference::obligation)
      .containsExactly(
        AdminCategoryPolicyObligation.DELETION,
        AdminCategoryPolicyObligation.GENERAL_VALIDATION,
        AdminCategoryPolicyObligation.HIERARCHY,
        AdminCategoryPolicyObligation.LIFECYCLE,
        AdminCategoryPolicyObligation.MUTATION_VALIDATION,
        AdminCategoryPolicyObligation.ORDERING,
        AdminCategoryPolicyObligation.QUERY
      );
    assertThat(references.find(AdminCategoryPolicyObligation.QUERY)).contains(
      AdminCategoryRegistrationFixtures.policyReference(
        AdminCategoryPolicyObligation.QUERY,
        "governance",
        "query"
      )
    );
    assertThat(
      new AdminCategoryPolicyReferences(List.of()).find(AdminCategoryPolicyObligation.QUERY)
    ).isEmpty();
  }

  @Test
  void acceptsCanonicalLogicalIdsWithoutAConfiguredTokenVocabulary() {
    AdminCategoryPolicyReference neutral = AdminCategoryRegistrationFixtures.policyReference(
      AdminCategoryPolicyObligation.QUERY,
      "uncoupled-module",
      "invented-policy-token"
    );

    AdminCategoryPolicyReferences references = new AdminCategoryPolicyReferences(List.of(neutral));

    assertThat(references.find(AdminCategoryPolicyObligation.QUERY)).contains(neutral);
    assertThat(neutral.logicalId().value()).isEqualTo("uncoupled-module:invented-policy-token");
  }

  @Test
  void equalityAndHashCodeUseAnImmutableCanonicalCopy() {
    List<AdminCategoryPolicyReference> source = new ArrayList<>(
      AdminCategoryRegistrationFixtures.completePolicyReferences().references()
    );
    List<AdminCategoryPolicyReference> reversed = new ArrayList<>(source);
    Collections.reverse(reversed);

    AdminCategoryPolicyReferences first = new AdminCategoryPolicyReferences(source);
    AdminCategoryPolicyReferences second = new AdminCategoryPolicyReferences(reversed);
    source.clear();
    reversed.clear();

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first.references()).hasSize(AdminCategoryPolicyObligation.values().length);
    assertThatThrownBy(() -> first.references().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
  }

  @ParameterizedTest(name = "invalid policy-id segment [{index}]")
  @MethodSource("invalidPolicyIdSegments")
  void rejectsEveryNoncanonicalModuleAndPolicySegment(String invalidSegment) {
    assertPolicyFailure(
      () -> new AdminCategoryLogicalPolicyId(invalidSegment, "query"),
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      null
    );
    assertPolicyFailure(
      () -> new AdminCategoryLogicalPolicyId("governance", invalidSegment),
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      null
    );
  }

  @ParameterizedTest(name = "invalid rendered policy id [{index}]")
  @MethodSource("invalidRenderedPolicyIds")
  void rejectsMalformedPaddedAndExtraSegmentRenderedIds(String invalidValue) {
    assertPolicyFailure(
      () -> AdminCategoryLogicalPolicyId.parse(invalidValue),
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      null
    );
  }

  @Test
  void rejectsNullAndForgedReferenceComponents() {
    AdminCategoryPolicyObligation forgedObligation = mock(AdminCategoryPolicyObligation.class);
    AdminCategoryLogicalPolicyId logicalId = new AdminCategoryLogicalPolicyId(
      "governance",
      "query"
    );

    assertPolicyFailure(
      () -> new AdminCategoryPolicyReference(null, logicalId),
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      null
    );
    assertPolicyFailure(
      () -> new AdminCategoryPolicyReference(forgedObligation, logicalId),
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      null
    );
    assertPolicyFailure(
      () -> new AdminCategoryPolicyReference(AdminCategoryPolicyObligation.QUERY, null),
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      null
    );
  }

  @Test
  void rejectsNullCollectionsMembersDuplicatePairsAndRepeatedObligations() {
    AdminCategoryPolicyReference first = AdminCategoryRegistrationFixtures.policyReference(
      AdminCategoryPolicyObligation.QUERY,
      "governance",
      "query"
    );
    AdminCategoryPolicyReference sameObligation = AdminCategoryRegistrationFixtures.policyReference(
      AdminCategoryPolicyObligation.QUERY,
      "uncoupled-module",
      "alternate-query"
    );

    assertPolicyFailure(
      () -> new AdminCategoryPolicyReferences((Collection<AdminCategoryPolicyReference>) null),
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      null
    );
    assertPolicyFailure(
      () -> new AdminCategoryPolicyReferences(Arrays.asList(first, null)),
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      null
    );
    assertPolicyFailure(
      () -> new AdminCategoryPolicyReferences(List.of(first, first)),
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      first
    );
    assertPolicyFailure(
      () -> new AdminCategoryPolicyReferences(List.of(first, sameObligation)),
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      sameObligation
    );
  }

  @Test
  void rejectsNullLookupWhileAValidAbsentObligationRemainsEmpty() {
    AdminCategoryPolicyReferences references = new AdminCategoryPolicyReferences(
      List.of(
        AdminCategoryRegistrationFixtures.policyReference(
          AdminCategoryPolicyObligation.QUERY,
          "governance",
          "query"
        )
      )
    );

    assertPolicyFailure(
      () -> references.find(null),
      AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
      null
    );
    assertThat(references.find(AdminCategoryPolicyObligation.DELETION)).isEmpty();
  }

  private static Stream<String> invalidPolicyIdSegments() {
    return Stream.concat(
      Stream.of((String) null),
      Stream.of(
        "",
        " ",
        "query ",
        " query",
        "Query",
        "1-query",
        "-query",
        "query-",
        "query--policy",
        "query_policy",
        "query:policy",
        "quéry",
        "a".repeat(64)
      )
    );
  }

  private static Stream<String> invalidRenderedPolicyIds() {
    return Stream.concat(
      Stream.of((String) null),
      Stream.of(
        "",
        "governance",
        ":query",
        "governance:",
        "governance:query:extra",
        " governance:query",
        "governance:query ",
        "Governance:query",
        "governance:Query"
      )
    );
  }

  private static void assertPolicyFailure(
    Executable executable,
    AdminCategoryValidationCategory category,
    AdminCategoryPolicyReference policyReference
  ) {
    AdminCategoryRegistrationValidationException failure = assertThrows(
      AdminCategoryRegistrationValidationException.class,
      executable
    );

    assertThat(failure.diagnostic().safeSystemKey()).isEqualTo("<unavailable>");
    assertThat(failure.diagnostic().category()).isSameAs(category);
    assertThat(failure.diagnostic().component()).contains(
      AdminCategoryValidationComponent.POLICY_REFERENCES
    );
    assertThat(failure.diagnostic().capability()).isEmpty();
    assertThat(failure.diagnostic().policyReference()).isEmpty();
    assertThat(failure.diagnostic().policyObligation()).isEqualTo(
      Optional.ofNullable(policyReference).map(AdminCategoryPolicyReference::obligation)
    );
    if (policyReference != null) {
      assertThat(failure.getMessage())
        .doesNotContain(policyReference.logicalId().moduleKey())
        .doesNotContain("policyId=");
    }
  }
}
