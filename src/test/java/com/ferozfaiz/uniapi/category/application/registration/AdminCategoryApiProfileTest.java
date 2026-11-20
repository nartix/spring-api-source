package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.RootModel;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdminCategoryApiProfileTest {

  private static final AdminCategoryApiProfile PROFILE =
    AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC;

  @Test
  void exposesTheExactStableProfileIdentityAndVocabularies() {
    assertThat(AdminCategoryApiProfile.values()).containsExactly(PROFILE);
    assertThat(PROFILE.identifierProfile()).isSameAs(
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1
    );
    assertThat(PROFILE.rootModel()).isSameAs(RootModel.IMPLICIT_NULL_PARENT);
    assertThat(PROFILE.supportedOperations()).containsExactlyInAnyOrder(Operation.values());
    assertThat(PROFILE.standardLogicalFields()).containsExactlyInAnyOrder(LogicalField.values());
  }

  @Test
  void ownsTheEightNumericV1HardMaxima() {
    AdminCategoryApiProfile.HardMaxima maxima = PROFILE.hardMaxima();

    assertThat(maxima.pageSize()).isEqualTo(1_000L);
    assertThat(maxima.searchInputLength()).isEqualTo(120L);
    assertThat(maxima.filterSelections()).isEqualTo(4L);
    assertThat(maxima.sortSelections()).isEqualTo(5L);
    assertThat(maxima.treeSize()).isEqualTo(10_000L);
    assertThat(maxima.parentOptionPageSize()).isEqualTo(50L);
    assertThat(maxima.includeIdCount()).isEqualTo(25L);
    assertThat(maxima.hierarchyDepth()).isEqualTo(Integer.MAX_VALUE);
    assertThat(PROFILE.hardMaxima()).isSameAs(maxima);
  }

  @Test
  void exposesImmutableProfileOwnedCollections() {
    assertThatThrownBy(() -> PROFILE.supportedOperations().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
    assertThatThrownBy(() -> PROFILE.standardLogicalFields().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("limitCases")
  void acceptsOneAndTheProfileMaximumForEveryOperationalLimit(LimitCase limitCase) {
    assertThatCode(() ->
      definitionWith(limitCase.limits(Optional.of(1L)))
    ).doesNotThrowAnyException();
    assertThatCode(() ->
      definitionWith(limitCase.limits(Optional.of(limitCase.maximum())))
    ).doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("limitCases")
  void rejectsNullZeroAndNegativeOperationalLimitValuesAtConstruction(LimitCase limitCase) {
    assertLimitFailure(() -> limitCase.limits(null), "<unavailable>", limitCase.capability());
    assertLimitFailure(
      () -> limitCase.limits(Optional.of(0L)),
      "<unavailable>",
      limitCase.capability()
    );
    assertLimitFailure(
      () -> limitCase.limits(Optional.of(-1L)),
      "<unavailable>",
      limitCase.capability()
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("limitCases")
  void rejectsValuesAboveEveryProfileMaximumWithoutAnOverridePath(LimitCase limitCase) {
    assertLimitFailure(
      () -> definitionWith(limitCase.limits(Optional.of(limitCase.maximum() + 1L))),
      AdminCategoryRegistrationFixtures.DEFAULT_SYSTEM_KEY,
      limitCase.capability()
    );
    assertLimitFailure(
      () -> definitionWith(limitCase.limits(Optional.of(Long.MAX_VALUE))),
      AdminCategoryRegistrationFixtures.DEFAULT_SYSTEM_KEY,
      limitCase.capability()
    );
  }

  @Test
  void callerConstructedHardMaximaCannotBeSuppliedToDefinitionsOrRegistrations() {
    for (var constructor : AdminCategorySystemDefinition.class.getDeclaredConstructors()) {
      assertThat(constructor.getParameterTypes()).doesNotContain(
        AdminCategoryApiProfile.HardMaxima.class
      );
    }
    for (var constructor : AdminCategorySystemRegistration.class.getDeclaredConstructors()) {
      assertThat(constructor.getParameterTypes()).doesNotContain(
        AdminCategoryApiProfile.HardMaxima.class
      );
    }
  }

  private static Stream<LimitCase> limitCases() {
    AdminCategoryApiProfile.HardMaxima maxima = PROFILE.hardMaxima();
    return Stream.of(
      new LimitCase("page-size", 0, maxima.pageSize(), AdminCategoryValidationCapability.LIST),
      new LimitCase(
        "search-input-length",
        1,
        maxima.searchInputLength(),
        AdminCategoryValidationCapability.SEARCH
      ),
      new LimitCase(
        "filter-selections",
        2,
        maxima.filterSelections(),
        AdminCategoryValidationCapability.FILTER
      ),
      new LimitCase(
        "sort-selections",
        3,
        maxima.sortSelections(),
        AdminCategoryValidationCapability.SORT
      ),
      new LimitCase("tree-size", 4, maxima.treeSize(), AdminCategoryValidationCapability.TREE),
      new LimitCase(
        "parent-option-page-size",
        5,
        maxima.parentOptionPageSize(),
        AdminCategoryValidationCapability.PARENT_OPTIONS
      ),
      new LimitCase(
        "include-id-count",
        6,
        maxima.includeIdCount(),
        AdminCategoryValidationCapability.PARENT_OPTIONS
      ),
      new LimitCase(
        "hierarchy-depth",
        7,
        maxima.hierarchyDepth(),
        AdminCategoryValidationCapability.HIERARCHY
      )
    );
  }

  private static AdminCategorySystemDefinition definitionWith(OperationalLimits limits) {
    AdminCategorySystemDefinition base = AdminCategoryRegistrationFixtures.completeDefinition();
    return new AdminCategorySystemDefinition(
      base.systemKey(),
      base.owningModule(),
      base.apiProfile(),
      base.identifierProfile(),
      base.operations(),
      base.hierarchy(),
      base.ordering(),
      base.lifecycle(),
      base.fieldsAndQueries(),
      limits
    );
  }

  private static void assertLimitFailure(
    Executable executable,
    String safeSystemKey,
    AdminCategoryValidationCapability capability
  ) {
    AdminCategoryRegistrationValidationException failure = assertThrows(
      AdminCategoryRegistrationValidationException.class,
      executable
    );

    assertThat(failure.diagnostic().safeSystemKey()).isEqualTo(safeSystemKey);
    assertThat(failure.diagnostic().category()).isSameAs(
      AdminCategoryValidationCategory.INVALID_LIMIT
    );
    assertThat(failure.diagnostic().component()).contains(
      AdminCategoryValidationComponent.DEFINITION
    );
    assertThat(failure.diagnostic().capability()).contains(capability);
    assertThat(failure.diagnostic().policyReference()).isEmpty();
  }

  private record LimitCase(
    String name,
    int slot,
    long maximum,
    AdminCategoryValidationCapability capability
  ) {
    OperationalLimits limits(Optional<Long> value) {
      @SuppressWarnings("unchecked")
      Optional<Long>[] values = new Optional[] {
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
      };
      values[slot] = value;
      return new OperationalLimits(
        values[0],
        values[1],
        values[2],
        values[3],
        values[4],
        values[5],
        values[6],
        values[7]
      );
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
