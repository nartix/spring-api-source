package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalOrder;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AdminCategoryRegistrationValidatorTest {

  @Test
  void missingDefinitionAndBindingEmitOnlyTheFinalTopLevelComponents() {
    AdminCategorySystemRegistration candidate = new AdminCategorySystemRegistration(
      Optional.empty(),
      Optional.empty()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(List.of(candidate));

    assertThat(diagnostics)
      .hasSize(2)
      .allSatisfy(diagnostic -> {
        assertThat(diagnostic.safeSystemKey()).isEqualTo("<unavailable>");
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.MISSING_COMPONENT
        );
        assertThat(diagnostic.capability()).isEmpty();
        assertThat(diagnostic.policyReference()).isEmpty();
      });
    assertThat(diagnostics)
      .extracting(diagnostic -> diagnostic.component().orElseThrow())
      .containsExactly(
        AdminCategoryValidationComponent.DEFINITION,
        AdminCategoryValidationComponent.REGISTRATION
      );
  }

  @Test
  void rejectsADeclaredOperationWhoseRequiredRuntimePortIsMissing() {
    AdminCategorySystemRegistration complete =
      AdminCategoryRegistrationFixtures.completeRegistration();
    AdminCategoryRuntimeBinding completeBinding = complete.runtimeBinding().orElseThrow();
    AdminCategoryRuntimePorts completePorts = completeBinding.ports();
    AdminCategoryRuntimePorts missingOrdering = new AdminCategoryRuntimePorts(
      completePorts.categoryRead(),
      completePorts.hierarchyRead(),
      completePorts.basicMutation(),
      completePorts.hierarchyMutation(),
      Optional.empty(),
      completePorts.lifecycle()
    );
    AdminCategoryRuntimeBinding incompleteBinding = runtimeBinding(
      completeBinding.identifierCodec(),
      completeBinding.permissionMapping(),
      completeBinding.policyBindings(),
      missingOrdering
    );
    AdminCategorySystemRegistration candidate = AdminCategorySystemRegistration.of(
      complete.definition().orElseThrow(),
      incompleteBinding
    );

    AdminCategoryRegistrationAggregateValidationException failure = assertThrows(
      AdminCategoryRegistrationAggregateValidationException.class,
      () -> new AdminCategorySystemRegistry(List.of(candidate))
    );

    assertThat(failure.diagnostics())
      .extracting(AdminCategoryValidationDiagnostic::category)
      .contains(AdminCategoryValidationCategory.MISSING_COLLABORATOR);
  }

  @Test
  void acceptsEmptyAndCompleteCoherentSnapshots() {
    assertThatCode(() ->
      AdminCategoryRegistrationValidator.validate(List.of())
    ).doesNotThrowAnyException();
    assertThatCode(() ->
      AdminCategoryRegistrationValidator.validate(
        List.of(
          AdminCategoryRegistrationFixtures.completeRegistration("alpha-system", "shared-owner"),
          AdminCategoryRegistrationFixtures.completeRegistration("omega-system", "shared-owner")
        )
      )
    ).doesNotThrowAnyException();
  }

  @Test
  void acceptsACompleteDirectNarrowPortRegistration() {
    AdminCategorySystemRegistration complete =
      AdminCategoryRegistrationFixtures.completeRegistration();
    AdminCategoryRuntimeBinding binding = complete.runtimeBinding().orElseThrow();
    AdminCategorySystemRegistration direct = AdminCategorySystemRegistration.of(
      complete.definition().orElseThrow(),
      runtimeBinding(
        binding.identifierCodec(),
        binding.permissionMapping(),
        binding.policyBindings(),
        binding.ports()
      )
    );

    assertThatCode(() ->
      new AdminCategorySystemRegistry(List.of(direct))
    ).doesNotThrowAnyException();
  }

  @Test
  void incompleteCandidateDiagnosticsRemainTypedBoundedAndSafe() {
    AdminCategorySystemRegistration candidate = new AdminCategorySystemRegistration(
      Optional.empty(),
      Optional.empty()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(List.of(candidate));

    assertThat(diagnostics)
      .hasSize(2)
      .allSatisfy(diagnostic -> {
        assertThat(diagnostic.safeSystemKey()).isEqualTo("<unavailable>");
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.MISSING_COMPONENT
        );
        assertThat(diagnostic.capability()).isEmpty();
        assertThat(diagnostic.policyReference()).isEmpty();
      });
    assertThat(diagnostics)
      .extracting(diagnostic -> diagnostic.component().orElseThrow())
      .containsExactly(
        AdminCategoryValidationComponent.DEFINITION,
        AdminCategoryValidationComponent.REGISTRATION
      );
    assertThat(exceptionFor(List.of(candidate)).getMessage())
      .doesNotContain("class ")
      .doesNotContain("@")
      .doesNotContain("Exception");
  }

  @Test
  void oneAdapterMayFillEverySlotWithinTheSameRegistration() {
    AdminCategoryReadPort adapter = mock(
      AdminCategoryReadPort.class,
      withSettings().extraInterfaces(
        AdminCategoryHierarchyReadPort.class,
        AdminCategoryBasicMutationPort.class,
        AdminCategoryHierarchyMutationPort.class,
        AdminCategoryOrderingPort.class,
        AdminCategoryLifecyclePort.class
      )
    );
    AdminCategorySystemDefinition definition =
      AdminCategoryRegistrationFixtures.completeDefinition();
    AdminCategoryRuntimePorts ports = new AdminCategoryRuntimePorts(
      Optional.of(adapter),
      Optional.of((AdminCategoryHierarchyReadPort) adapter),
      Optional.of((AdminCategoryBasicMutationPort) adapter),
      Optional.of((AdminCategoryHierarchyMutationPort) adapter),
      Optional.of((AdminCategoryOrderingPort) adapter),
      Optional.of((AdminCategoryLifecyclePort) adapter)
    );

    assertThatCode(() ->
      AdminCategoryRegistrationValidator.validate(List.of(completeRegistration(definition, ports)))
    ).doesNotThrowAnyException();
  }

  @Test
  void duplicateDefinitionGroupsEmitOneOccurrencePerParticipant() {
    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(
        AdminCategoryRegistrationFixtures.completeRegistration("duplicate-system", "owner-one"),
        AdminCategoryRegistrationFixtures.completeRegistration("duplicate-system", "owner-two")
      )
    );

    assertThat(diagnostics)
      .hasSize(2)
      .allSatisfy(diagnostic -> {
        assertThat(diagnostic.safeSystemKey()).isEqualTo("duplicate-system");
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.DUPLICATE_SYSTEM_KEY
        );
        assertThat(diagnostic.component()).contains(AdminCategoryValidationComponent.REGISTRATION);
      });
  }

  @Test
  void crossRegistrationPortReuseUsesReferenceIdentityAndReportsEachConflictingSlot() {
    AdminCategoryReadPort sharedAdapter = mock(
      AdminCategoryReadPort.class,
      withSettings().extraInterfaces(AdminCategoryHierarchyReadPort.class)
    );
    AdminCategoryRuntimePorts alphaPorts = portsWithSharedReadSlots(sharedAdapter);
    AdminCategoryRuntimePorts omegaPorts = portsWithSharedReadSlots(sharedAdapter);
    AdminCategorySystemRegistration alpha = completeRegistration(
      AdminCategoryRegistrationFixtures.completeDefinition("alpha-system", "shared-owner"),
      alphaPorts
    );
    AdminCategorySystemRegistration omega = completeRegistration(
      AdminCategoryRegistrationFixtures.completeDefinition("omega-system", "shared-owner"),
      omegaPorts
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(List.of(alpha, omega));

    assertThat(diagnostics)
      .hasSize(4)
      .allSatisfy(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.DUPLICATE_RUNTIME_PORT_OWNERSHIP
        );
        assertThat(diagnostic.component()).contains(AdminCategoryValidationComponent.RUNTIME_PORTS);
        assertThat(diagnostic.capability()).isEmpty();
        assertThat(diagnostic.policyReference()).isEmpty();
      })
      .extracting(AdminCategoryValidationDiagnostic::safeSystemKey)
      .containsExactly("alpha-system", "alpha-system", "omega-system", "omega-system");
  }

  @Test
  void portIdentityDiagnosticsNeverCallContributorObjectMethodsOrLeakDetails() {
    AdminCategoryReadPort first = new ThrowingObjectMethodsReadPort();
    AdminCategoryRuntimePorts alphaPorts = portsWithCategoryRead(first);
    AdminCategoryRuntimePorts omegaPorts = portsWithCategoryRead(first);

    AdminCategoryRegistrationAggregateValidationException exception = exceptionFor(
      List.of(
        completeRegistration(
          AdminCategoryRegistrationFixtures.completeDefinition("alpha-system", "owner"),
          alphaPorts
        ),
        completeRegistration(
          AdminCategoryRegistrationFixtures.completeDefinition("omega-system", "owner"),
          omegaPorts
        )
      )
    );

    assertThat(exception.diagnostics())
      .hasSize(2)
      .allSatisfy(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.DUPLICATE_RUNTIME_PORT_OWNERSHIP
        );
        assertThat(diagnostic.component()).contains(AdminCategoryValidationComponent.RUNTIME_PORTS);
      });
    assertThat(exception.getMessage())
      .doesNotContain("identity-secret")
      .doesNotContain("hash-secret")
      .doesNotContain("string-secret")
      .doesNotContain("IllegalStateException");
  }

  @Test
  void codecProfileValidationCallsEachCodecOnceAndUsesNoImplementationFallback() {
    AdminCategoryIdentifierCodec<?> nullCodec = mock(AdminCategoryIdentifierCodec.class);
    when(nullCodec.identifierProfile()).thenReturn(null);
    AdminCategoryIdentifierCodec<?> throwingCodec = mock(AdminCategoryIdentifierCodec.class);
    when(throwingCodec.identifierProfile()).thenThrow(
      new IllegalStateException("codec-implementation-secret")
    );
    AdminCategoryIdentifierProfile incompatibleProfile = mock(AdminCategoryIdentifierProfile.class);
    AdminCategoryIdentifierCodec<?> incompatibleCodec = mock(AdminCategoryIdentifierCodec.class);
    when(incompatibleCodec.identifierProfile()).thenReturn(incompatibleProfile);

    AdminCategoryRegistrationAggregateValidationException exception = exceptionFor(
      List.of(
        completeRegistration(
          AdminCategoryRegistrationFixtures.completeDefinition("null-codec", "owner"),
          nullCodec
        ),
        completeRegistration(
          AdminCategoryRegistrationFixtures.completeDefinition("throwing-codec", "owner"),
          throwingCodec
        ),
        completeRegistration(
          AdminCategoryRegistrationFixtures.completeDefinition("incompatible-codec", "owner"),
          incompatibleCodec
        )
      )
    );

    assertThat(exception.diagnostics())
      .hasSize(3)
      .allSatisfy(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.PROFILE_CODEC_INCOMPATIBILITY
        );
        assertThat(diagnostic.component()).contains(
          AdminCategoryValidationComponent.IDENTIFIER_CODEC
        );
      });
    assertThat(exception.getMessage()).doesNotContain("codec-implementation-secret");
    verify(nullCodec, times(1)).identifierProfile();
    verify(throwingCodec, times(1)).identifierProfile();
    verify(incompatibleCodec, times(1)).identifierProfile();
  }

  @Test
  void standardProfileRequiresEachCoreLogicalFacetAsAnIndependentOccurrence() {
    AdminCategorySystemDefinition definition = minimalDefinition("missing-core");
    FieldQueryDeclaration noFields = new FieldQueryDeclaration(
      List.of(),
      List.of(),
      List.of(),
      List.of()
    );
    definition = copyDefinition(
      definition,
      definition.operations(),
      definition.hierarchy(),
      definition.ordering(),
      definition.lifecycle(),
      noFields,
      definition.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(minimalRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic ->
          diagnostic.category() == AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE &&
          diagnostic.capability().isEmpty()
      )
      .hasSize(6)
      .allSatisfy(diagnostic ->
        assertThat(diagnostic.component()).contains(AdminCategoryValidationComponent.DEFINITION)
      );
  }

  @Test
  void conditionalHierarchyAndRankFacetsAreRequiredIndependently() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "conditional-fields",
      "owner"
    );
    Set<LogicalField> fields = EnumSet.allOf(LogicalField.class);
    fields.remove(LogicalField.PARENT);
    fields.remove(LogicalField.HIERARCHY_CONTEXT);
    fields.remove(LogicalField.RANK);
    FieldQueryDeclaration missingConditionalFields = new FieldQueryDeclaration(
      fields,
      complete.fieldsAndQueries().searchableFields(),
      complete.fieldsAndQueries().allowedFilters(),
      complete.fieldsAndQueries().allowedSorts()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      complete.hierarchy(),
      complete.ordering(),
      complete.lifecycle(),
      missingConditionalFields,
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(diagnostic ->
        diagnostic.capability().equals(Optional.of(AdminCategoryValidationCapability.HIERARCHY))
      )
      .hasSize(2);
    assertThat(diagnostics)
      .filteredOn(diagnostic ->
        diagnostic.capability().equals(Optional.of(AdminCategoryValidationCapability.RANKING))
      )
      .hasSize(1);
  }

  @Test
  void rejectsDisabledHierarchyDeclarationsAndSuppressesDependentLimitCascades() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "disabled-hierarchy",
      "owner"
    );
    HierarchyDeclaration disabledButPopulated = new HierarchyDeclaration(
      false,
      complete.hierarchy().semantics(),
      complete.hierarchy().rootModel(),
      complete.hierarchy().supportedOperations()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      disabledButPopulated,
      complete.ordering(),
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic -> diagnostic.category() == AdminCategoryValidationCategory.INVALID_HIERARCHY
      )
      .isNotEmpty();
    assertThat(diagnostics)
      .filteredOn(
        diagnostic ->
          diagnostic.category() == AdminCategoryValidationCategory.INVALID_LIMIT &&
          Set.of(
            AdminCategoryValidationCapability.TREE,
            AdminCategoryValidationCapability.PARENT_OPTIONS,
            AdminCategoryValidationCapability.HIERARCHY
          ).contains(diagnostic.capability().orElse(null))
      )
      .isEmpty();
  }

  @Test
  void incoherentHierarchySuppressesDependentCollaboratorCascades() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "hierarchy-oracle",
      "owner"
    );
    HierarchyDeclaration missingRoot = new HierarchyDeclaration(
      true,
      complete.hierarchy().semantics(),
      Optional.empty(),
      complete.hierarchy().supportedOperations()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      missingRoot,
      complete.ordering(),
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .singleElement()
      .satisfies(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.INVALID_HIERARCHY
        );
        assertThat(diagnostic.capability()).contains(AdminCategoryValidationCapability.HIERARCHY);
      });
  }

  @Test
  void disabledHierarchyOwnsEachPresentHierarchyLimitOccurrence() {
    AdminCategorySystemDefinition minimal = minimalDefinition("disabled-hierarchy-limits");
    OperationalLimits hierarchyLimits = new OperationalLimits(
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.of(10_000L),
      Optional.of(50L),
      Optional.of(25L),
      Optional.of(100L)
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      minimal,
      minimal.operations(),
      minimal.hierarchy(),
      minimal.ordering(),
      minimal.lifecycle(),
      minimal.fieldsAndQueries(),
      hierarchyLimits
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(minimalRegistration(definition))
    );

    assertThat(diagnostics)
      .hasSize(4)
      .allSatisfy(diagnostic ->
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.INVALID_HIERARCHY
        )
      );
    assertThat(diagnostics)
      .extracting(diagnostic -> diagnostic.capability().orElseThrow())
      .containsExactly(
        AdminCategoryValidationCapability.HIERARCHY,
        AdminCategoryValidationCapability.PARENT_OPTIONS,
        AdminCategoryValidationCapability.PARENT_OPTIONS,
        AdminCategoryValidationCapability.TREE
      );
  }

  @Test
  void rejectsEnabledHierarchyWithoutRootSemanticsOrOperationAgreement() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "incomplete-hierarchy",
      "owner"
    );
    HierarchyDeclaration incomplete = new HierarchyDeclaration(
      true,
      Optional.empty(),
      Optional.empty(),
      List.of(Operation.TREE)
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      incomplete,
      complete.ordering(),
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic -> diagnostic.category() == AdminCategoryValidationCategory.INVALID_HIERARCHY
      )
      .extracting(diagnostic -> diagnostic.capability().orElseThrow())
      .contains(
        AdminCategoryValidationCapability.HIERARCHY,
        AdminCategoryValidationCapability.BREADCRUMB,
        AdminCategoryValidationCapability.PARENT_OPTIONS,
        AdminCategoryValidationCapability.MOVE
      );
  }

  @Test
  void rejectsOrderingRankingPlacementAndReorderContradictions() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "invalid-ordering",
      "owner"
    );
    OrderingDeclaration contradictory = new OrderingDeclaration(
      false,
      true,
      true,
      true,
      complete.ordering().placementModes(),
      complete.ordering().fallbackOrder()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      complete.hierarchy(),
      contradictory,
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic ->
          diagnostic.category() == AdminCategoryValidationCategory.INVALID_ORDERING_RANKING
      )
      .isNotEmpty()
      .extracting(diagnostic -> diagnostic.capability().orElseThrow())
      .contains(
        AdminCategoryValidationCapability.ORDERING,
        AdminCategoryValidationCapability.RANKING,
        AdminCategoryValidationCapability.REORDER
      );
  }

  @Test
  void acceptsAnExplicitlyEmptyFallbackOrderWhenOrderingIsOtherwiseCoherent() {
    AdminCategorySystemDefinition source = AdminCategoryRegistrationFixtures.completeDefinition(
      "empty-fallback",
      "owner"
    );
    OrderingDeclaration ordering = source.ordering();
    AdminCategorySystemDefinition definition = copyDefinition(
      source,
      source.operations(),
      source.hierarchy(),
      new OrderingDeclaration(
        ordering.orderingEnabled(),
        ordering.rankingEnabled(),
        ordering.rankExposed(),
        ordering.reorderSupported(),
        ordering.placementModes(),
        List.of()
      ),
      source.lifecycle(),
      source.fieldsAndQueries(),
      source.operationalLimits()
    );

    assertThatCode(() ->
      AdminCategoryRegistrationValidator.validate(List.of(completeRegistration(definition)))
    ).doesNotThrowAnyException();
  }

  @Test
  void incoherentOrderingSuppressesDependentCollaboratorCascades() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "ordering-oracle",
      "owner"
    );
    OrderingDeclaration missingIdentifierTieBreaker = new OrderingDeclaration(
      true,
      true,
      true,
      true,
      complete.ordering().placementModes(),
      List.of(new LogicalOrder(LogicalField.NAME, SortDirection.ASC))
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      complete.hierarchy(),
      missingIdentifierTieBreaker,
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .singleElement()
      .satisfies(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.INVALID_ORDERING_RANKING
        );
        assertThat(diagnostic.capability()).contains(AdminCategoryValidationCapability.SORT);
      });
  }

  @Test
  void duplicateFallbackLogicalKeysEmitOneOccurrencePerEntry() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "duplicate-fallback",
      "owner"
    );
    OrderingDeclaration duplicateNameKeys = new OrderingDeclaration(
      true,
      true,
      true,
      true,
      complete.ordering().placementModes(),
      List.of(
        new LogicalOrder(LogicalField.NAME, SortDirection.ASC),
        new LogicalOrder(LogicalField.NAME, SortDirection.DESC),
        new LogicalOrder(LogicalField.IDENTIFIER, SortDirection.ASC)
      )
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      complete.hierarchy(),
      duplicateNameKeys,
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .hasSize(2)
      .allSatisfy(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.INVALID_ORDERING_RANKING
        );
        assertThat(diagnostic.capability()).contains(AdminCategoryValidationCapability.SORT);
      });
  }

  @Test
  void descendingRankFallbackEmitsOneRankingOccurrence() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "descending-rank",
      "owner"
    );
    OrderingDeclaration descendingRank = new OrderingDeclaration(
      true,
      true,
      true,
      true,
      complete.ordering().placementModes(),
      List.of(
        new LogicalOrder(LogicalField.RANK, SortDirection.DESC),
        new LogicalOrder(LogicalField.IDENTIFIER, SortDirection.ASC)
      )
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      complete.hierarchy(),
      descendingRank,
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .singleElement()
      .satisfies(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.INVALID_ORDERING_RANKING
        );
        assertThat(diagnostic.capability()).contains(AdminCategoryValidationCapability.RANKING);
      });
  }

  @Test
  void rejectsLifecycleStateAndActionContradictions() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "invalid-lifecycle",
      "owner"
    );
    LifecycleDeclaration disabledButPopulated = new LifecycleDeclaration(
      false,
      complete.lifecycle().states(),
      complete.lifecycle().actions()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      complete.hierarchy(),
      complete.ordering(),
      disabledButPopulated,
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic -> diagnostic.category() == AdminCategoryValidationCategory.INVALID_LIFECYCLE
      )
      .isNotEmpty()
      .extracting(diagnostic -> diagnostic.capability().orElseThrow())
      .contains(
        AdminCategoryValidationCapability.LIFECYCLE,
        AdminCategoryValidationCapability.ACTIVATE,
        AdminCategoryValidationCapability.DEACTIVATE
      );
  }

  @Test
  void incoherentLifecycleSuppressesDependentCollaboratorCascades() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "lifecycle-oracle",
      "owner"
    );
    LifecycleDeclaration missingInactiveState = new LifecycleDeclaration(
      true,
      List.of(AdminCategorySystemDefinition.LifecycleState.ACTIVE),
      complete.lifecycle().actions()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      complete.hierarchy(),
      complete.ordering(),
      missingInactiveState,
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .singleElement()
      .satisfies(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.INVALID_LIFECYCLE
        );
        assertThat(diagnostic.capability()).contains(AdminCategoryValidationCapability.LIFECYCLE);
      });
  }

  @Test
  void suppressesQueryLimitCascadesWhenTheOnlyQueryDeclarationIsIncoherent() {
    AdminCategorySystemDefinition minimal = minimalDefinition("incoherent-query");
    FieldQueryDeclaration incoherentQuery = new FieldQueryDeclaration(
      minimal.fieldsAndQueries().fields(),
      List.of(LogicalField.PARENT),
      List.of(),
      List.of()
    );
    OperationalLimits rawQueryLimits = new OperationalLimits(
      Optional.of(100L),
      Optional.of(50L),
      Optional.of(2L),
      Optional.of(2L),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      minimal,
      minimal.operations(),
      minimal.hierarchy(),
      minimal.ordering(),
      minimal.lifecycle(),
      incoherentQuery,
      rawQueryLimits
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(minimalRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic ->
          diagnostic.category() == AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE &&
          diagnostic.capability().equals(Optional.of(AdminCategoryValidationCapability.SEARCH))
      )
      .hasSize(1);
    assertThat(diagnostics)
      .filteredOn(
        diagnostic -> diagnostic.category() == AdminCategoryValidationCategory.INVALID_LIMIT
      )
      .isEmpty();
  }

  @Test
  void coherentFilterTriggersQueryOracleDespiteAnIncoherentSearchDeclaration() {
    AdminCategorySystemDefinition minimal = minimalDefinition("mixed-query-oracle");
    FieldQueryDeclaration mixedQuery = new FieldQueryDeclaration(
      minimal.fieldsAndQueries().fields(),
      List.of(LogicalField.PARENT),
      List.of(LogicalField.ACTIVE),
      List.of()
    );
    OperationDeclaration queryClaim = new OperationDeclaration(
      minimal.operations().supportedOperations(),
      List.of(AdminCategoryPolicyObligation.GENERAL_VALIDATION, AdminCategoryPolicyObligation.QUERY)
    );
    OperationalLimits queryLimits = new OperationalLimits(
      Optional.of(100L),
      Optional.of(50L),
      Optional.of(2L),
      Optional.of(2L),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      minimal,
      queryClaim,
      minimal.hierarchy(),
      minimal.ordering(),
      minimal.lifecycle(),
      mixedQuery,
      queryLimits
    );
    AdminCategoryPolicyReferences references = new AdminCategoryPolicyReferences(
      List.of(
        AdminCategoryRegistrationFixtures.policyReference(
          AdminCategoryPolicyObligation.GENERAL_VALIDATION,
          "governance",
          "general-validation"
        ),
        AdminCategoryRegistrationFixtures.policyReference(
          AdminCategoryPolicyObligation.QUERY,
          "governance",
          "query"
        )
      )
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(
        registration(
          definition,
          AdminCategoryRegistrationFixtures.numericCodec(),
          minimalPermissionMapping(definition.systemKey()),
          references
        )
      )
    );

    assertThat(diagnostics)
      .singleElement()
      .satisfies(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE
        );
        assertThat(diagnostic.capability()).contains(AdminCategoryValidationCapability.SEARCH);
      });
  }

  @Test
  void completeProfileTriggersAllEightRequiredOperationalLimitSlots() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "missing-limits",
      "owner"
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      complete.operations(),
      complete.hierarchy(),
      complete.ordering(),
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      emptyLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic -> diagnostic.category() == AdminCategoryValidationCategory.INVALID_LIMIT
      )
      .extracting(diagnostic -> diagnostic.capability().orElseThrow())
      .containsExactlyInAnyOrder(
        AdminCategoryValidationCapability.LIST,
        AdminCategoryValidationCapability.SEARCH,
        AdminCategoryValidationCapability.FILTER,
        AdminCategoryValidationCapability.SORT,
        AdminCategoryValidationCapability.TREE,
        AdminCategoryValidationCapability.PARENT_OPTIONS,
        AdminCategoryValidationCapability.PARENT_OPTIONS,
        AdminCategoryValidationCapability.HIERARCHY
      );
  }

  @Test
  void untriggeredQueryLimitsAreEachContradictory() {
    AdminCategorySystemDefinition minimal = minimalDefinition("contradictory-query-limits");
    OperationalLimits queryLimits = new OperationalLimits(
      Optional.of(100L),
      Optional.of(50L),
      Optional.of(2L),
      Optional.of(2L),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      minimal,
      minimal.operations(),
      minimal.hierarchy(),
      minimal.ordering(),
      minimal.lifecycle(),
      minimal.fieldsAndQueries(),
      queryLimits
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(minimalRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic -> diagnostic.category() == AdminCategoryValidationCategory.INVALID_LIMIT
      )
      .extracting(diagnostic -> diagnostic.capability().orElseThrow())
      .containsExactlyInAnyOrder(
        AdminCategoryValidationCapability.LIST,
        AdminCategoryValidationCapability.SEARCH,
        AdminCategoryValidationCapability.FILTER,
        AdminCategoryValidationCapability.SORT
      );
  }

  @Test
  void permissionOracleRejectsMissingAndExtraOperationBindings() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "permission-coverage",
      "owner"
    );
    AdminCategoryPermissionMapping missingList = mappingFor(
      complete.systemKey(),
      EnumSet.complementOf(EnumSet.of(Operation.LIST)),
      operation -> canonicalPermission(complete.systemKey(), operation)
    );
    AdminCategorySystemDefinition minimal = minimalDefinition("extra-permissions");

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(
        registration(
          complete,
          AdminCategoryRegistrationFixtures.numericCodec(),
          missingList,
          AdminCategoryRegistrationFixtures.completePolicyReferences()
        ),
        registration(
          minimal,
          AdminCategoryRegistrationFixtures.numericCodec(),
          AdminCategoryRegistrationFixtures.completePermissionMapping(minimal.systemKey()),
          minimalPolicyReferences()
        )
      )
    );

    assertThat(diagnostics)
      .filteredOn(diagnostic -> diagnostic.safeSystemKey().equals("permission-coverage"))
      .singleElement()
      .satisfies(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING
        );
        assertThat(diagnostic.capability()).contains(AdminCategoryValidationCapability.LIST);
      });
    assertThat(diagnostics)
      .filteredOn(diagnostic -> diagnostic.safeSystemKey().equals("extra-permissions"))
      .hasSize(Operation.values().length - 2)
      .allSatisfy(diagnostic ->
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING
        )
      );
  }

  @Test
  void permissionOracleAcceptsExplicitCompleteValuesForEveryOperation() {
    List<AdminCategorySystemRegistration> candidates = new ArrayList<>();
    for (Operation operation : Operation.values()) {
      String key = "wrong-" + operation.code();
      AdminCategorySystemDefinition definition =
        AdminCategoryRegistrationFixtures.completeDefinition(key, "owner");
      AdminCategoryPermissionMapping mapping = mappingFor(
        key,
        EnumSet.allOf(Operation.class),
        candidateOperation ->
          candidateOperation == operation
            ? "ROLE_ADMIN"
            : canonicalPermission(key, candidateOperation)
      );
      candidates.add(
        registration(
          definition,
          AdminCategoryRegistrationFixtures.numericCodec(),
          mapping,
          AdminCategoryRegistrationFixtures.completePolicyReferences()
        )
      );
    }

    assertThatCode(() ->
      AdminCategoryRegistrationValidator.validate(candidates)
    ).doesNotThrowAnyException();
  }

  @Test
  void exactCollaboratorOracleRejectsEveryDefinitionObligationMismatch() {
    AdminCategorySystemDefinition complete = AdminCategoryRegistrationFixtures.completeDefinition(
      "obligation-mismatch",
      "owner"
    );
    OperationDeclaration noClaimedObligations = new OperationDeclaration(
      complete.operations().supportedOperations(),
      List.of()
    );
    AdminCategorySystemDefinition definition = copyDefinition(
      complete,
      noClaimedObligations,
      complete.hierarchy(),
      complete.ordering(),
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(completeRegistration(definition))
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic ->
          diagnostic.category() == AdminCategoryValidationCategory.INVALID_OPERATION_DECLARATION
      )
      .hasSize(AdminCategoryPolicyObligation.values().length)
      .allSatisfy(diagnostic -> {
        assertThat(diagnostic.capability()).isEmpty();
        assertThat(diagnostic.policyReference()).isEmpty();
      });
  }

  @Test
  void exactCollaboratorOracleRejectsEveryMissingRegistrationReference() {
    AdminCategorySystemDefinition definition = AdminCategoryRegistrationFixtures.completeDefinition(
      "missing-policies",
      "owner"
    );
    AdminCategoryPolicyReferences emptyReferences = new AdminCategoryPolicyReferences(List.of());

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(
        registration(
          definition,
          AdminCategoryRegistrationFixtures.numericCodec(),
          AdminCategoryRegistrationFixtures.completePermissionMapping(definition.systemKey()),
          emptyReferences
        )
      )
    );

    assertThat(diagnostics)
      .filteredOn(
        diagnostic -> diagnostic.category() == AdminCategoryValidationCategory.MISSING_COLLABORATOR
      )
      .hasSize(AdminCategoryPolicyObligation.values().length)
      .allSatisfy(diagnostic -> {
        assertThat(diagnostic.component()).contains(
          AdminCategoryValidationComponent.POLICY_REFERENCES
        );
        assertThat(diagnostic.capability()).isEmpty();
        assertThat(diagnostic.policyReference()).isEmpty();
      });
  }

  @Test
  void minimalReadDefinitionRejectsAnUnexpectedReferenceWithItsTypedContext() {
    AdminCategorySystemDefinition definition = minimalDefinition("unexpected-policy");
    AdminCategoryPolicyReference general = AdminCategoryRegistrationFixtures.policyReference(
      AdminCategoryPolicyObligation.GENERAL_VALIDATION,
      "governance",
      "general"
    );
    AdminCategoryPolicyReference extra = AdminCategoryRegistrationFixtures.policyReference(
      AdminCategoryPolicyObligation.QUERY,
      "another-module",
      "arbitrary-query-name"
    );

    List<AdminCategoryValidationDiagnostic> diagnostics = diagnosticsFor(
      List.of(
        registration(
          definition,
          AdminCategoryRegistrationFixtures.numericCodec(),
          minimalPermissionMapping(definition.systemKey()),
          new AdminCategoryPolicyReferences(List.of(general, extra))
        )
      )
    );

    assertThat(diagnostics)
      .singleElement()
      .satisfies(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.UNEXPECTED_COLLABORATOR
        );
        assertThat(diagnostic.component()).contains(
          AdminCategoryValidationComponent.POLICY_REFERENCES
        );
        assertThat(diagnostic.capability()).isEmpty();
        assertThat(diagnostic.policyReference()).isEmpty();
        assertThat(diagnostic.policyObligation()).contains(extra.obligation());
      });
  }

  @Test
  void acceptsCanonicalPolicyIdsWithoutSemanticVocabularyFiltering() {
    AdminCategorySystemDefinition definition = minimalDefinition("vocabulary-neutral");
    AdminCategoryPolicyReferences references = new AdminCategoryPolicyReferences(
      List.of(
        AdminCategoryRegistrationFixtures.policyReference(
          AdminCategoryPolicyObligation.GENERAL_VALIDATION,
          "sql-storage",
          "implementation-credential-policy"
        )
      )
    );

    assertThatCode(() ->
      AdminCategoryRegistrationValidator.validate(
        List.of(
          registration(
            definition,
            AdminCategoryRegistrationFixtures.numericCodec(),
            minimalPermissionMapping(definition.systemKey()),
            references
          )
        )
      )
    ).doesNotThrowAnyException();
  }

  @Test
  void assemblyOrderingAndRetentionAreIndependentOfDiscoveryOrder() {
    List<AdminCategorySystemRegistration> forward = new ArrayList<>();
    for (int index = 0; index < 63; index++) {
      forward.add(emptyCandidate());
    }
    List<AdminCategorySystemRegistration> reverse = new ArrayList<>(forward);
    Collections.reverse(reverse);

    AdminCategoryRegistrationAggregateValidationException first = exceptionFor(forward);
    AdminCategoryRegistrationAggregateValidationException second = exceptionFor(reverse);

    assertThat(first.diagnostics()).hasSize(100).containsExactlyElementsOf(second.diagnostics());
    assertThat(first.omittedCount()).isEqualTo(26L);
    assertThat(second.omittedCount()).isEqualTo(26L);
    assertThat(first.diagnostics()).isSorted();
    assertThat(first.diagnostics().stream().distinct()).hasSizeLessThan(100);
  }

  @Test
  void assemblyRetainsExactlyOneHundredWithoutOmissionAtTheBoundary() {
    List<AdminCategorySystemRegistration> candidates = new ArrayList<>();
    for (int index = 0; index < 50; index++) {
      candidates.add(emptyCandidate());
    }

    AdminCategoryRegistrationAggregateValidationException exception = exceptionFor(candidates);

    assertThat(exception.diagnostics()).hasSize(100).isSorted().isUnmodifiable();
    assertThat(exception.omittedCount()).isZero();
  }

  private static AdminCategoryRegistrationAggregateValidationException exceptionFor(
    List<AdminCategorySystemRegistration> candidates
  ) {
    AdminCategoryRegistrationAggregateValidationException exception = catchThrowableOfType(
      () -> AdminCategoryRegistrationValidator.validate(candidates),
      AdminCategoryRegistrationAggregateValidationException.class
    );
    assertThat(exception).isNotNull();
    return exception;
  }

  private static List<AdminCategoryValidationDiagnostic> diagnosticsFor(
    List<AdminCategorySystemRegistration> candidates
  ) {
    return exceptionFor(candidates).diagnostics();
  }

  private static AdminCategorySystemRegistration emptyCandidate() {
    return new AdminCategorySystemRegistration(Optional.empty(), Optional.empty());
  }

  private static AdminCategorySystemRegistration completeRegistration(
    AdminCategorySystemDefinition definition
  ) {
    return completeRegistration(definition, AdminCategoryRegistrationFixtures.numericCodec());
  }

  private static AdminCategorySystemRegistration completeRegistration(
    AdminCategorySystemDefinition definition,
    AdminCategoryIdentifierCodec<?> codec
  ) {
    return registration(
      definition,
      codec,
      AdminCategoryRegistrationFixtures.completePermissionMapping(definition.systemKey()),
      AdminCategoryRegistrationFixtures.completePolicyReferences()
    );
  }

  private static AdminCategorySystemRegistration completeRegistration(
    AdminCategorySystemDefinition definition,
    AdminCategoryRuntimePorts ports
  ) {
    AdminCategoryPermissionMapping permissions =
      AdminCategoryRegistrationFixtures.completePermissionMapping(definition.systemKey());
    AdminCategoryPolicyReferences references =
      AdminCategoryRegistrationFixtures.completePolicyReferences();
    return AdminCategorySystemRegistration.of(
      definition,
      runtimeBinding(
        AdminCategoryRegistrationFixtures.numericCodec(),
        permissions,
        AdminCategoryRegistrationFixtures.runtimePolicyBindings(references),
        ports
      )
    );
  }

  private static AdminCategorySystemRegistration minimalRegistration(
    AdminCategorySystemDefinition definition
  ) {
    return registration(
      definition,
      AdminCategoryRegistrationFixtures.numericCodec(),
      minimalPermissionMapping(definition.systemKey()),
      minimalPolicyReferences()
    );
  }

  private static AdminCategorySystemRegistration registration(
    AdminCategorySystemDefinition definition,
    AdminCategoryIdentifierCodec<?> codec,
    AdminCategoryPermissionMapping permissions,
    AdminCategoryPolicyReferences policyReferences
  ) {
    AdminCategoryRuntimeBinding binding = runtimeBinding(
      codec,
      permissions,
      AdminCategoryRegistrationFixtures.runtimePolicyBindings(policyReferences),
      runtimePortsFor(definition)
    );
    return AdminCategorySystemRegistration.of(definition, binding);
  }

  private static AdminCategoryRuntimeBinding runtimeBinding(
    AdminCategoryIdentifierCodec<?> codec,
    AdminCategoryPermissionMapping permissions,
    AdminCategoryRuntimePolicyBindings policyBindings,
    AdminCategoryRuntimePorts ports
  ) {
    try {
      for (var constructor : AdminCategoryRuntimeBinding.class.getConstructors()) {
        if (constructor.getParameterCount() == 4) {
          return (AdminCategoryRuntimeBinding) constructor.newInstance(
            codec,
            permissions,
            policyBindings,
            ports
          );
        }
        if (constructor.getParameterCount() == 5) {
          return (AdminCategoryRuntimeBinding) constructor.newInstance(
            codec,
            permissions,
            policyBindings,
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

  private static final class ThrowingObjectMethodsReadPort implements AdminCategoryReadPort {

    @Override
    public PageResult<AdminCategoryModels.CategoryNode> list(AdminCategoryListQuery query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AdminCategoryModels.Category get(Long id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object other) {
      throw new IllegalStateException("identity-secret");
    }

    @Override
    public int hashCode() {
      throw new IllegalStateException("hash-secret");
    }

    @Override
    public String toString() {
      throw new IllegalStateException("string-secret");
    }
  }

  private static AdminCategoryRuntimePorts portsWithSharedReadSlots(
    AdminCategoryReadPort sharedAdapter
  ) {
    return new AdminCategoryRuntimePorts(
      Optional.of(sharedAdapter),
      Optional.of((AdminCategoryHierarchyReadPort) sharedAdapter),
      Optional.of(mock(AdminCategoryBasicMutationPort.class)),
      Optional.of(mock(AdminCategoryHierarchyMutationPort.class)),
      Optional.of(mock(AdminCategoryOrderingPort.class)),
      Optional.of(mock(AdminCategoryLifecyclePort.class))
    );
  }

  private static AdminCategoryRuntimePorts portsWithCategoryRead(
    AdminCategoryReadPort categoryRead
  ) {
    return new AdminCategoryRuntimePorts(
      Optional.of(categoryRead),
      Optional.of(mock(AdminCategoryHierarchyReadPort.class)),
      Optional.of(mock(AdminCategoryBasicMutationPort.class)),
      Optional.of(mock(AdminCategoryHierarchyMutationPort.class)),
      Optional.of(mock(AdminCategoryOrderingPort.class)),
      Optional.of(mock(AdminCategoryLifecyclePort.class))
    );
  }

  private static AdminCategoryRuntimePorts runtimePortsFor(
    AdminCategorySystemDefinition definition
  ) {
    EnumSet<AdminCategoryProviderCapability> required = EnumSet.noneOf(
      AdminCategoryProviderCapability.class
    );
    definition
      .operations()
      .supportedOperations()
      .forEach(operation ->
        AdminCategoryProviderCapability.forOperation(operation).ifPresent(required::add)
      );
    return new AdminCategoryRuntimePorts(
      required.contains(AdminCategoryProviderCapability.CATEGORY_READ)
        ? Optional.of(mock(AdminCategoryReadPort.class))
        : Optional.empty(),
      required.contains(AdminCategoryProviderCapability.HIERARCHY_READ)
        ? Optional.of(mock(AdminCategoryHierarchyReadPort.class))
        : Optional.empty(),
      required.contains(AdminCategoryProviderCapability.BASIC_MUTATION)
        ? Optional.of(mock(AdminCategoryBasicMutationPort.class))
        : Optional.empty(),
      required.contains(AdminCategoryProviderCapability.HIERARCHY_MUTATION)
        ? Optional.of(mock(AdminCategoryHierarchyMutationPort.class))
        : Optional.empty(),
      required.contains(AdminCategoryProviderCapability.ORDERING)
        ? Optional.of(mock(AdminCategoryOrderingPort.class))
        : Optional.empty(),
      required.contains(AdminCategoryProviderCapability.LIFECYCLE)
        ? Optional.of(mock(AdminCategoryLifecyclePort.class))
        : Optional.empty()
    );
  }

  private static AdminCategorySystemDefinition minimalDefinition(String systemKey) {
    return new AdminCategorySystemDefinition(
      systemKey,
      "neutral-owner",
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC,
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
      new OperationDeclaration(
        List.of(Operation.CAPABILITIES, Operation.GET),
        List.of(AdminCategoryPolicyObligation.GENERAL_VALIDATION)
      ),
      new HierarchyDeclaration(false, Optional.empty(), Optional.empty(), List.of()),
      new OrderingDeclaration(false, false, false, false, List.of(), List.of()),
      new LifecycleDeclaration(false, List.of(), List.of()),
      new FieldQueryDeclaration(
        List.of(
          LogicalField.IDENTIFIER,
          LogicalField.NAME,
          LogicalField.SLUG,
          LogicalField.ACTIVE,
          LogicalField.CREATED_AT,
          LogicalField.UPDATED_AT
        ),
        List.of(),
        List.of(),
        List.of()
      ),
      emptyLimits()
    );
  }

  private static AdminCategorySystemDefinition copyDefinition(
    AdminCategorySystemDefinition source,
    OperationDeclaration operations,
    HierarchyDeclaration hierarchy,
    OrderingDeclaration ordering,
    LifecycleDeclaration lifecycle,
    FieldQueryDeclaration fieldsAndQueries,
    OperationalLimits operationalLimits
  ) {
    return new AdminCategorySystemDefinition(
      source.systemKey(),
      source.owningModule(),
      source.apiProfile(),
      source.identifierProfile(),
      operations,
      hierarchy,
      ordering,
      lifecycle,
      fieldsAndQueries,
      operationalLimits
    );
  }

  private static OperationalLimits emptyLimits() {
    return new OperationalLimits(
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );
  }

  private static AdminCategoryPermissionMapping minimalPermissionMapping(String systemKey) {
    return mappingFor(systemKey, EnumSet.of(Operation.CAPABILITIES, Operation.GET), operation ->
      canonicalPermission(systemKey, operation)
    );
  }

  private static AdminCategoryPermissionMapping mappingFor(
    String systemKey,
    Set<Operation> operations,
    Function<Operation, String> permission
  ) {
    return new AdminCategoryPermissionMapping(
      operations
        .stream()
        .map(operation -> new Binding(operation, permission.apply(operation)))
        .toList()
    );
  }

  private static String canonicalPermission(String systemKey, Operation operation) {
    boolean read = Set.of(
      Operation.CAPABILITIES,
      Operation.LIST,
      Operation.TREE,
      Operation.PARENT_OPTIONS,
      Operation.GET,
      Operation.BREADCRUMB
    ).contains(operation);
    return "admin:" + systemKey + "-categories:" + (read ? "read" : "write");
  }

  private static AdminCategoryPolicyReferences minimalPolicyReferences() {
    return new AdminCategoryPolicyReferences(
      List.of(
        AdminCategoryRegistrationFixtures.policyReference(
          AdminCategoryPolicyObligation.GENERAL_VALIDATION,
          "governance",
          "general-validation"
        )
      )
    );
  }
}
