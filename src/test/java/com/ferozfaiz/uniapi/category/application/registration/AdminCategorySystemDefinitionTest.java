package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchySemantics;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleState;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalOrder;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.PlacementMode;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.RootModel;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.SortDirection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdminCategorySystemDefinitionTest {

  @Test
  void constructsACompleteDefinitionWithIndependentSystemAndOwningModuleIdentities() {
    AdminCategorySystemDefinition definition = AdminCategoryRegistrationFixtures.completeDefinition(
      "taxonomic-fixture",
      "merchandising"
    );

    assertThat(definition.systemKey()).isEqualTo("taxonomic-fixture");
    assertThat(definition.owningModule()).isEqualTo("merchandising");
    assertThat(definition.systemKey()).doesNotStartWith(definition.owningModule());
    assertThat(definition.apiProfile()).isSameAs(
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC
    );
    assertThat(definition.identifierProfile()).isSameAs(
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1
    );
  }

  @Test
  void exposesTheCompleteLogicalDeclarations() {
    AdminCategorySystemDefinition definition =
      AdminCategoryRegistrationFixtures.completeDefinition();

    assertThat(definition.operations().supportedOperations()).containsExactlyInAnyOrder(
      Operation.values()
    );
    assertThat(definition.operations().requiredPolicyObligations()).containsExactlyInAnyOrder(
      AdminCategoryPolicyObligation.values()
    );
    assertThat(definition.hierarchy().enabled()).isTrue();
    assertThat(definition.hierarchy().semantics()).contains(HierarchySemantics.PARENT_CHILD_TREE);
    assertThat(definition.hierarchy().rootModel()).contains(RootModel.IMPLICIT_NULL_PARENT);
    assertThat(definition.hierarchy().supportedOperations()).containsExactlyInAnyOrder(
      Operation.TREE,
      Operation.BREADCRUMB,
      Operation.PARENT_OPTIONS,
      Operation.MOVE
    );
    assertThat(definition.ordering().orderingEnabled()).isTrue();
    assertThat(definition.ordering().rankingEnabled()).isTrue();
    assertThat(definition.ordering().rankExposed()).isTrue();
    assertThat(definition.ordering().reorderSupported()).isTrue();
    assertThat(definition.ordering().placementModes()).containsExactlyInAnyOrder(
      PlacementMode.values()
    );
    assertThat(definition.ordering().fallbackOrder()).containsExactly(
      new LogicalOrder(LogicalField.NAME, SortDirection.ASC),
      new LogicalOrder(LogicalField.IDENTIFIER, SortDirection.ASC)
    );
    assertThat(definition.lifecycle().enabled()).isTrue();
    assertThat(definition.lifecycle().states()).containsExactlyInAnyOrder(LifecycleState.values());
    assertThat(definition.lifecycle().actions()).containsExactlyInAnyOrder(
      Operation.ACTIVATE,
      Operation.DEACTIVATE
    );
    assertThat(definition.fieldsAndQueries().fields()).containsExactlyInAnyOrder(
      LogicalField.values()
    );
    assertThat(definition.fieldsAndQueries().searchableFields()).containsExactlyInAnyOrder(
      LogicalField.NAME,
      LogicalField.SLUG
    );
    assertThat(definition.fieldsAndQueries().allowedFilters()).containsExactlyInAnyOrder(
      LogicalField.PARENT,
      LogicalField.ACTIVE,
      LogicalField.SLUG
    );
    assertThat(definition.fieldsAndQueries().allowedSorts()).containsExactlyInAnyOrder(
      LogicalField.IDENTIFIER,
      LogicalField.PARENT,
      LogicalField.SLUG,
      LogicalField.NAME,
      LogicalField.RANK,
      LogicalField.ACTIVE,
      LogicalField.CREATED_AT,
      LogicalField.UPDATED_AT
    );
  }

  @Test
  void retainsTheEightDefinitionOwnedOperationalLimits() {
    AdminCategorySystemDefinition.OperationalLimits limits =
      AdminCategoryRegistrationFixtures.completeDefinition().operationalLimits();

    assertThat(limits.pageSize()).contains(1_000L);
    assertThat(limits.searchInputLength()).contains(120L);
    assertThat(limits.filterSelections()).contains(4L);
    assertThat(limits.sortSelections()).contains(5L);
    assertThat(limits.treeSize()).contains(10_000L);
    assertThat(limits.parentOptionPageSize()).contains(50L);
    assertThat(limits.includeIdCount()).contains(25L);
    assertThat(limits.hierarchyDepth()).contains(100L);
  }

  @Test
  void defensivelyCopiesEveryDeclarationCollection() {
    List<Operation> operations = new ArrayList<>(List.of(Operation.values()));
    List<AdminCategoryPolicyObligation> obligations = new ArrayList<>(
      List.of(AdminCategoryPolicyObligation.values())
    );
    List<Operation> hierarchyOperations = new ArrayList<>(
      List.of(Operation.TREE, Operation.BREADCRUMB, Operation.PARENT_OPTIONS, Operation.MOVE)
    );
    List<PlacementMode> placementModes = new ArrayList<>(List.of(PlacementMode.values()));
    List<LogicalOrder> fallbackOrder = new ArrayList<>(
      List.of(
        new LogicalOrder(LogicalField.NAME, SortDirection.ASC),
        new LogicalOrder(LogicalField.IDENTIFIER, SortDirection.ASC)
      )
    );
    List<LifecycleState> states = new ArrayList<>(List.of(LifecycleState.values()));
    List<Operation> actions = new ArrayList<>(List.of(Operation.ACTIVATE, Operation.DEACTIVATE));
    List<LogicalField> fields = new ArrayList<>(List.of(LogicalField.values()));
    List<LogicalField> searchableFields = new ArrayList<>(
      List.of(LogicalField.NAME, LogicalField.SLUG)
    );
    List<LogicalField> allowedFilters = new ArrayList<>(
      List.of(LogicalField.PARENT, LogicalField.ACTIVE, LogicalField.SLUG)
    );
    List<LogicalField> allowedSorts = new ArrayList<>(
      List.of(
        LogicalField.IDENTIFIER,
        LogicalField.PARENT,
        LogicalField.SLUG,
        LogicalField.NAME,
        LogicalField.RANK,
        LogicalField.ACTIVE,
        LogicalField.CREATED_AT,
        LogicalField.UPDATED_AT
      )
    );

    OperationDeclaration operationDeclaration = new OperationDeclaration(operations, obligations);
    HierarchyDeclaration hierarchy = new HierarchyDeclaration(
      true,
      Optional.of(HierarchySemantics.PARENT_CHILD_TREE),
      Optional.of(RootModel.IMPLICIT_NULL_PARENT),
      hierarchyOperations
    );
    OrderingDeclaration ordering = new OrderingDeclaration(
      true,
      true,
      true,
      true,
      placementModes,
      fallbackOrder
    );
    LifecycleDeclaration lifecycle = new LifecycleDeclaration(true, states, actions);
    FieldQueryDeclaration fieldsAndQueries = new FieldQueryDeclaration(
      fields,
      searchableFields,
      allowedFilters,
      allowedSorts
    );

    operations.clear();
    obligations.clear();
    hierarchyOperations.clear();
    placementModes.clear();
    fallbackOrder.clear();
    states.clear();
    actions.clear();
    fields.clear();
    searchableFields.clear();
    allowedFilters.clear();
    allowedSorts.clear();

    assertThat(operationDeclaration.supportedOperations()).hasSize(Operation.values().length);
    assertThat(operationDeclaration.requiredPolicyObligations()).hasSize(
      AdminCategoryPolicyObligation.values().length
    );
    assertThat(hierarchy.supportedOperations()).hasSize(4);
    assertThat(ordering.placementModes()).hasSize(4);
    assertThat(ordering.fallbackOrder()).hasSize(2);
    assertThat(lifecycle.states()).hasSize(2);
    assertThat(lifecycle.actions()).hasSize(2);
    assertThat(fieldsAndQueries.fields()).hasSize(LogicalField.values().length);
    assertThat(fieldsAndQueries.searchableFields()).hasSize(2);
    assertThat(fieldsAndQueries.allowedFilters()).hasSize(3);
    assertThat(fieldsAndQueries.allowedSorts()).hasSize(8);

    assertThatThrownBy(() -> operationDeclaration.supportedOperations().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
    assertThatThrownBy(() -> hierarchy.supportedOperations().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
    assertThatThrownBy(() -> ordering.placementModes().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
    assertThatThrownBy(() -> ordering.fallbackOrder().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
    assertThatThrownBy(() -> lifecycle.states().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
    assertThatThrownBy(() -> fieldsAndQueries.fields().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
  }

  @ParameterizedTest(name = "invalid system key [{index}]")
  @MethodSource("invalidCanonicalKeys")
  void rejectsEveryInvalidSystemKeyClassWithoutNormalization(String invalidKey) {
    AdminCategorySystemDefinition base = AdminCategoryRegistrationFixtures.completeDefinition();

    assertDefinitionFailure(
      () ->
        definition(
          invalidKey,
          base.owningModule(),
          base.apiProfile(),
          base.identifierProfile(),
          base.operations(),
          base.hierarchy(),
          base.ordering(),
          base.lifecycle(),
          base.fieldsAndQueries(),
          base.operationalLimits()
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_SYSTEM_KEY,
      null
    );
  }

  @ParameterizedTest(name = "invalid owning module [{index}]")
  @MethodSource("invalidCanonicalKeys")
  void rejectsEveryInvalidOwningModuleClassIndependently(String invalidModule) {
    AdminCategorySystemDefinition base = AdminCategoryRegistrationFixtures.completeDefinition();

    assertDefinitionFailure(
      () ->
        definition(
          base.systemKey(),
          invalidModule,
          base.apiProfile(),
          base.identifierProfile(),
          base.operations(),
          base.hierarchy(),
          base.ordering(),
          base.lifecycle(),
          base.fieldsAndQueries(),
          base.operationalLimits()
        ),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_OWNING_MODULE,
      null
    );
  }

  @Test
  void rejectsNullAndForgedProfilesAndProfileIdentityMismatch() {
    AdminCategorySystemDefinition base = AdminCategoryRegistrationFixtures.completeDefinition();
    AdminCategoryApiProfile forgedApiProfile = mock(AdminCategoryApiProfile.class);
    AdminCategoryIdentifierProfile forgedIdentifierProfile = mock(
      AdminCategoryIdentifierProfile.class
    );

    assertDefinitionFailure(
      () -> definitionWithProfiles(null, base.identifierProfile()),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_PROFILE,
      null
    );
    assertDefinitionFailure(
      () -> definitionWithProfiles(forgedApiProfile, base.identifierProfile()),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_PROFILE,
      null
    );
    assertDefinitionFailure(
      () -> definitionWithProfiles(base.apiProfile(), null),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_PROFILE,
      null
    );
    assertDefinitionFailure(
      () -> definitionWithProfiles(base.apiProfile(), forgedIdentifierProfile),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_PROFILE,
      null
    );
  }

  @Test
  void rejectsEachNullTopLevelDeclarationWithItsOwnedDiagnostic() {
    AdminCategorySystemDefinition base = AdminCategoryRegistrationFixtures.completeDefinition();

    assertDefinitionFailure(
      () ->
        definitionWith(
          null,
          base.hierarchy(),
          base.ordering(),
          base.lifecycle(),
          base.fieldsAndQueries(),
          base.operationalLimits()
        ),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_OPERATION_DECLARATION,
      null
    );
    assertDefinitionFailure(
      () ->
        definitionWith(
          base.operations(),
          null,
          base.ordering(),
          base.lifecycle(),
          base.fieldsAndQueries(),
          base.operationalLimits()
        ),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_HIERARCHY,
      AdminCategoryValidationCapability.HIERARCHY
    );
    assertDefinitionFailure(
      () ->
        definitionWith(
          base.operations(),
          base.hierarchy(),
          null,
          base.lifecycle(),
          base.fieldsAndQueries(),
          base.operationalLimits()
        ),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_ORDERING_RANKING,
      AdminCategoryValidationCapability.ORDERING
    );
    assertDefinitionFailure(
      () ->
        definitionWith(
          base.operations(),
          base.hierarchy(),
          base.ordering(),
          null,
          base.fieldsAndQueries(),
          base.operationalLimits()
        ),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_LIFECYCLE,
      AdminCategoryValidationCapability.LIFECYCLE
    );
    assertDefinitionFailure(
      () ->
        definitionWith(
          base.operations(),
          base.hierarchy(),
          base.ordering(),
          base.lifecycle(),
          null,
          base.operationalLimits()
        ),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
      null
    );
    assertDefinitionFailure(
      () ->
        definitionWith(
          base.operations(),
          base.hierarchy(),
          base.ordering(),
          base.lifecycle(),
          base.fieldsAndQueries(),
          null
        ),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_LIMIT,
      null
    );
  }

  @Test
  void rejectsOperationAndObligationCollectionShapeAndVocabularyViolations() {
    Operation forgedOperation = mock(Operation.class);
    AdminCategoryPolicyObligation forgedObligation = mock(AdminCategoryPolicyObligation.class);

    assertDefinitionFailure(
      () -> new OperationDeclaration((Collection<Operation>) null, List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      null
    );
    assertDefinitionFailure(
      () -> new OperationDeclaration(Arrays.asList(Operation.LIST, null), List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      null
    );
    assertDefinitionFailure(
      () -> new OperationDeclaration(List.of(Operation.LIST, Operation.LIST), List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.LIST
    );
    assertDefinitionFailure(
      () -> new OperationDeclaration(List.of(), (Collection<AdminCategoryPolicyObligation>) null),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      null
    );
    assertDefinitionFailure(
      () ->
        new OperationDeclaration(
          List.of(),
          Arrays.asList(AdminCategoryPolicyObligation.QUERY, null)
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      null
    );
    assertDefinitionFailure(
      () ->
        new OperationDeclaration(
          List.of(),
          List.of(AdminCategoryPolicyObligation.QUERY, AdminCategoryPolicyObligation.QUERY)
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      null
    );
    assertDefinitionFailure(
      () -> new OperationDeclaration(List.of(), List.of(forgedObligation)),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_OPERATION_DECLARATION,
      null
    );

    OperationDeclaration forgedDeclaration = new OperationDeclaration(
      List.of(forgedOperation),
      List.of()
    );
    AdminCategorySystemDefinition base = AdminCategoryRegistrationFixtures.completeDefinition();
    assertDefinitionFailure(
      () ->
        definitionWith(
          forgedDeclaration,
          base.hierarchy(),
          base.ordering(),
          base.lifecycle(),
          base.fieldsAndQueries(),
          base.operationalLimits()
        ),
      base.systemKey(),
      AdminCategoryValidationCategory.INVALID_OPERATION_DECLARATION,
      null
    );
  }

  @Test
  void rejectsHierarchyCollectionShapeAndClosedVocabularyViolations() {
    Operation forgedOperation = mock(Operation.class);
    HierarchySemantics forgedSemantics = mock(HierarchySemantics.class);
    RootModel forgedRootModel = mock(RootModel.class);

    assertDefinitionFailure(
      () -> new HierarchyDeclaration(true, null, Optional.empty(), List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.HIERARCHY
    );
    assertDefinitionFailure(
      () -> new HierarchyDeclaration(true, Optional.empty(), null, List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.HIERARCHY
    );
    assertDefinitionFailure(
      () ->
        new HierarchyDeclaration(
          true,
          Optional.empty(),
          Optional.empty(),
          (Collection<Operation>) null
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.HIERARCHY
    );
    assertDefinitionFailure(
      () ->
        new HierarchyDeclaration(
          true,
          Optional.empty(),
          Optional.empty(),
          Arrays.asList(Operation.TREE, null)
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.HIERARCHY
    );
    assertDefinitionFailure(
      () ->
        new HierarchyDeclaration(
          true,
          Optional.empty(),
          Optional.empty(),
          List.of(Operation.TREE, Operation.TREE)
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.TREE
    );
    assertDefinitionFailure(
      () ->
        new HierarchyDeclaration(true, Optional.empty(), Optional.empty(), List.of(Operation.LIST)),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_HIERARCHY,
      AdminCategoryValidationCapability.LIST
    );
    assertDefinitionFailure(
      () ->
        new HierarchyDeclaration(
          true,
          Optional.empty(),
          Optional.empty(),
          List.of(forgedOperation)
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_HIERARCHY,
      null
    );
    assertDefinitionFailure(
      () ->
        new HierarchyDeclaration(true, Optional.of(forgedSemantics), Optional.empty(), List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_HIERARCHY,
      AdminCategoryValidationCapability.HIERARCHY
    );
    assertDefinitionFailure(
      () ->
        new HierarchyDeclaration(true, Optional.empty(), Optional.of(forgedRootModel), List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_HIERARCHY,
      AdminCategoryValidationCapability.HIERARCHY
    );
  }

  @Test
  void rejectsOrderingCollectionShapeAndClosedVocabularyViolations() {
    PlacementMode forgedPlacement = mock(PlacementMode.class);
    LogicalField forgedField = mock(LogicalField.class);
    SortDirection forgedDirection = mock(SortDirection.class);
    LogicalOrder nameAscending = new LogicalOrder(LogicalField.NAME, SortDirection.ASC);

    assertDefinitionFailure(
      () ->
        new OrderingDeclaration(
          true,
          true,
          true,
          true,
          (Collection<PlacementMode>) null,
          List.of()
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.ORDERING
    );
    assertDefinitionFailure(
      () ->
        new OrderingDeclaration(
          true,
          true,
          true,
          true,
          Arrays.asList(PlacementMode.APPEND, null),
          List.of()
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.ORDERING
    );
    assertDefinitionFailure(
      () ->
        new OrderingDeclaration(
          true,
          true,
          true,
          true,
          List.of(PlacementMode.APPEND, PlacementMode.APPEND),
          List.of()
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.ORDERING
    );
    assertDefinitionFailure(
      () ->
        new OrderingDeclaration(true, true, true, true, List.of(), (Collection<LogicalOrder>) null),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.ORDERING
    );
    assertDefinitionFailure(
      () ->
        new OrderingDeclaration(
          true,
          true,
          true,
          true,
          List.of(),
          Arrays.asList(nameAscending, null)
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.ORDERING
    );
    assertDefinitionFailure(
      () ->
        new OrderingDeclaration(
          true,
          true,
          true,
          true,
          List.of(),
          List.of(nameAscending, nameAscending)
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.ORDERING
    );
    assertDefinitionFailure(
      () -> new LogicalOrder(null, SortDirection.ASC),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.SORT
    );
    assertDefinitionFailure(
      () -> new LogicalOrder(LogicalField.NAME, null),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.SORT
    );
    assertDefinitionFailure(
      () -> new OrderingDeclaration(true, true, true, true, List.of(forgedPlacement), List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_ORDERING_RANKING,
      AdminCategoryValidationCapability.ORDERING
    );
    assertDefinitionFailure(
      () -> new LogicalOrder(forgedField, SortDirection.ASC),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
      AdminCategoryValidationCapability.SORT
    );
    assertDefinitionFailure(
      () -> new LogicalOrder(LogicalField.NAME, forgedDirection),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_ORDERING_RANKING,
      AdminCategoryValidationCapability.SORT
    );
  }

  @Test
  void rejectsLifecycleCollectionShapeAndClosedVocabularyViolations() {
    LifecycleState forgedState = mock(LifecycleState.class);
    Operation forgedAction = mock(Operation.class);

    assertDefinitionFailure(
      () -> new LifecycleDeclaration(true, (Collection<LifecycleState>) null, List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.LIFECYCLE
    );
    assertDefinitionFailure(
      () -> new LifecycleDeclaration(true, Arrays.asList(LifecycleState.ACTIVE, null), List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.LIFECYCLE
    );
    assertDefinitionFailure(
      () ->
        new LifecycleDeclaration(
          true,
          List.of(LifecycleState.ACTIVE, LifecycleState.ACTIVE),
          List.of()
        ),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.LIFECYCLE
    );
    assertDefinitionFailure(
      () -> new LifecycleDeclaration(true, List.of(), (Collection<Operation>) null),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.LIFECYCLE
    );
    assertDefinitionFailure(
      () -> new LifecycleDeclaration(true, List.of(), Arrays.asList(Operation.ACTIVATE, null)),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.LIFECYCLE
    );
    assertDefinitionFailure(
      () ->
        new LifecycleDeclaration(true, List.of(), List.of(Operation.ACTIVATE, Operation.ACTIVATE)),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationCapability.ACTIVATE
    );
    assertDefinitionFailure(
      () -> new LifecycleDeclaration(true, List.of(forgedState), List.of()),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_LIFECYCLE,
      AdminCategoryValidationCapability.LIFECYCLE
    );
    assertDefinitionFailure(
      () -> new LifecycleDeclaration(true, List.of(), List.of(Operation.LIST)),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_LIFECYCLE,
      AdminCategoryValidationCapability.LIST
    );
    assertDefinitionFailure(
      () -> new LifecycleDeclaration(true, List.of(), List.of(forgedAction)),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_LIFECYCLE,
      null
    );
  }

  @Test
  void rejectsEveryFieldQueryCollectionShapeAndClosedVocabularyViolation() {
    assertLogicalFieldCollectionFailures(
      fields -> new FieldQueryDeclaration(fields, List.of(), List.of(), List.of()),
      null
    );
    assertLogicalFieldCollectionFailures(
      fields -> new FieldQueryDeclaration(List.of(), fields, List.of(), List.of()),
      AdminCategoryValidationCapability.SEARCH
    );
    assertLogicalFieldCollectionFailures(
      fields -> new FieldQueryDeclaration(List.of(), List.of(), fields, List.of()),
      AdminCategoryValidationCapability.FILTER
    );
    assertLogicalFieldCollectionFailures(
      fields -> new FieldQueryDeclaration(List.of(), List.of(), List.of(), fields),
      AdminCategoryValidationCapability.SORT
    );
  }

  private static void assertLogicalFieldCollectionFailures(
    Function<Collection<LogicalField>, FieldQueryDeclaration> declaration,
    AdminCategoryValidationCapability capability
  ) {
    LogicalField forgedField = mock(LogicalField.class);

    assertDefinitionFailure(
      () -> declaration.apply(null),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      capability
    );
    assertDefinitionFailure(
      () -> declaration.apply(Arrays.asList(LogicalField.NAME, null)),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      capability
    );
    assertDefinitionFailure(
      () -> declaration.apply(List.of(LogicalField.NAME, LogicalField.NAME)),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      capability
    );
    assertDefinitionFailure(
      () -> declaration.apply(List.of(forgedField)),
      "<unavailable>",
      AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
      capability
    );
  }

  private static Stream<String> invalidCanonicalKeys() {
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
        "taxonomic/fixture",
        "táxonomic",
        "a".repeat(64)
      )
    );
  }

  private static AdminCategorySystemDefinition definitionWithProfiles(
    AdminCategoryApiProfile apiProfile,
    AdminCategoryIdentifierProfile identifierProfile
  ) {
    AdminCategorySystemDefinition base = AdminCategoryRegistrationFixtures.completeDefinition();
    return definition(
      base.systemKey(),
      base.owningModule(),
      apiProfile,
      identifierProfile,
      base.operations(),
      base.hierarchy(),
      base.ordering(),
      base.lifecycle(),
      base.fieldsAndQueries(),
      base.operationalLimits()
    );
  }

  private static AdminCategorySystemDefinition definitionWith(
    OperationDeclaration operations,
    HierarchyDeclaration hierarchy,
    OrderingDeclaration ordering,
    LifecycleDeclaration lifecycle,
    FieldQueryDeclaration fieldsAndQueries,
    AdminCategorySystemDefinition.OperationalLimits operationalLimits
  ) {
    AdminCategorySystemDefinition base = AdminCategoryRegistrationFixtures.completeDefinition();
    return definition(
      base.systemKey(),
      base.owningModule(),
      base.apiProfile(),
      base.identifierProfile(),
      operations,
      hierarchy,
      ordering,
      lifecycle,
      fieldsAndQueries,
      operationalLimits
    );
  }

  private static AdminCategorySystemDefinition definition(
    String systemKey,
    String owningModule,
    AdminCategoryApiProfile apiProfile,
    AdminCategoryIdentifierProfile identifierProfile,
    OperationDeclaration operations,
    HierarchyDeclaration hierarchy,
    OrderingDeclaration ordering,
    LifecycleDeclaration lifecycle,
    FieldQueryDeclaration fieldsAndQueries,
    AdminCategorySystemDefinition.OperationalLimits operationalLimits
  ) {
    return new AdminCategorySystemDefinition(
      systemKey,
      owningModule,
      apiProfile,
      identifierProfile,
      operations,
      hierarchy,
      ordering,
      lifecycle,
      fieldsAndQueries,
      operationalLimits
    );
  }

  private static void assertDefinitionFailure(
    Executable executable,
    String safeSystemKey,
    AdminCategoryValidationCategory category,
    AdminCategoryValidationCapability capability
  ) {
    AdminCategoryRegistrationValidationException failure = assertThrows(
      AdminCategoryRegistrationValidationException.class,
      executable
    );

    assertThat(failure.diagnostic().safeSystemKey()).isEqualTo(safeSystemKey);
    assertThat(failure.diagnostic().category()).isSameAs(category);
    assertThat(failure.diagnostic().component()).contains(
      AdminCategoryValidationComponent.DEFINITION
    );
    assertThat(failure.diagnostic().capability()).isEqualTo(Optional.ofNullable(capability));
    assertThat(failure.diagnostic().policyReference()).isEmpty();
  }
}
