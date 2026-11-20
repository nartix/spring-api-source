package com.ferozfaiz.uniapi.category.application.registration;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleState;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalOrder;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.PlacementMode;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.SortDirection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class AdminCategoryRegistrationValidator {

  private static final Set<Operation> HIERARCHY_OPERATIONS = Set.of(
    Operation.TREE,
    Operation.BREADCRUMB,
    Operation.PARENT_OPTIONS,
    Operation.MOVE
  );
  private static final Set<Operation> HIERARCHY_READ_OPERATIONS = Set.of(
    Operation.TREE,
    Operation.BREADCRUMB,
    Operation.PARENT_OPTIONS
  );
  private static final Set<LogicalField> CORE_FIELDS = Set.of(
    LogicalField.IDENTIFIER,
    LogicalField.NAME,
    LogicalField.SLUG,
    LogicalField.ACTIVE,
    LogicalField.CREATED_AT,
    LogicalField.UPDATED_AT
  );

  private AdminCategoryRegistrationValidator() {}

  static void validate(List<AdminCategorySystemRegistration> snapshot) {
    if (snapshot == null) {
      throw single(
        "<unavailable>",
        AdminCategoryValidationCategory.INVALID_COLLECTION,
        AdminCategoryValidationComponent.REGISTRATION,
        null,
        null
      );
    }

    var diagnostics = new BoundedRegistrationDiagnosticAccumulator();
    var facts = new ArrayList<CandidateFacts>(snapshot.size());
    var registrationsByKey = new HashMap<String, List<CandidateFacts>>();
    var registrationsByPort = new IdentityHashMap<Object, List<PortOwnership>>();

    for (AdminCategorySystemRegistration registration : snapshot) {
      if (registration == null) {
        diagnostics.add(
          diagnostic(
            "<unavailable>",
            AdminCategoryValidationCategory.INVALID_COLLECTION,
            AdminCategoryValidationComponent.REGISTRATION,
            null,
            null
          )
        );
        continue;
      }

      String safeKey = registration.systemKey().orElse("<unavailable>");
      var fact = new CandidateFacts(registration, safeKey);
      facts.add(fact);

      missingComponents(registration, safeKey, diagnostics);
      registration
        .definition()
        .ifPresent(definition ->
          registrationsByKey
            .computeIfAbsent(definition.systemKey(), ignored -> new ArrayList<>())
            .add(fact)
        );
      registration.runtimeBinding().ifPresent(binding -> {
        var ports = binding.ports();
        List.of(
          ports.categoryRead(),
          ports.hierarchyRead(),
          ports.basicMutation(),
          ports.hierarchyMutation(),
          ports.ordering(),
          ports.lifecycle()
        ).forEach(slot ->
          slot.ifPresent(port ->
            registrationsByPort
              .computeIfAbsent(port, ignored -> new ArrayList<>())
              .add(new PortOwnership(fact))
          )
        );
      });
    }

    for (List<CandidateFacts> duplicateGroup : registrationsByKey.values()) {
      if (duplicateGroup.size() > 1) {
        for (CandidateFacts fact : duplicateGroup) {
          diagnostics.add(
            diagnostic(
              fact.safeSystemKey(),
              AdminCategoryValidationCategory.DUPLICATE_SYSTEM_KEY,
              AdminCategoryValidationComponent.REGISTRATION,
              null,
              null
            )
          );
        }
      }
    }

    for (List<PortOwnership> duplicateGroup : registrationsByPort.values()) {
      var participatingCandidates = new IdentityHashMap<CandidateFacts, Boolean>();
      duplicateGroup.forEach(ownership ->
        participatingCandidates.put(ownership.candidate(), Boolean.TRUE)
      );
      if (participatingCandidates.size() > 1) {
        for (PortOwnership ownership : duplicateGroup) {
          CandidateFacts fact = ownership.candidate();
          diagnostics.add(
            diagnostic(
              fact.safeSystemKey(),
              AdminCategoryValidationCategory.DUPLICATE_RUNTIME_PORT_OWNERSHIP,
              AdminCategoryValidationComponent.RUNTIME_PORTS,
              null,
              null
            )
          );
        }
      }
    }

    for (CandidateFacts fact : facts) {
      validateCandidate(fact, diagnostics);
    }

    if (!diagnostics.isEmpty()) {
      throw new AdminCategoryRegistrationAggregateValidationException(
        diagnostics.retainedInOrder(),
        diagnostics.omittedCount()
      );
    }
  }

  private static void missingComponents(
    AdminCategorySystemRegistration registration,
    String safeKey,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    if (registration.definition().isEmpty()) {
      addMissing(safeKey, AdminCategoryValidationComponent.DEFINITION, diagnostics);
    }
    if (registration.runtimeBinding().isEmpty()) {
      addMissing(safeKey, AdminCategoryValidationComponent.REGISTRATION, diagnostics);
    }
  }

  private static void addMissing(
    String safeKey,
    AdminCategoryValidationComponent component,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    diagnostics.add(
      diagnostic(safeKey, AdminCategoryValidationCategory.MISSING_COMPONENT, component, null, null)
    );
  }

  private static void validateCandidate(
    CandidateFacts fact,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    var registration = fact.registration();
    if (registration.definition().isEmpty()) {
      return;
    }
    var definition = registration.definition().orElseThrow();

    registration.runtimeBinding().ifPresent(binding -> {
      validateCodec(definition, binding.identifierCodec(), diagnostics);
      validateRuntimePorts(definition, binding.ports(), diagnostics);
    });

    DefinitionCoherence coherence = validateDefinition(definition, diagnostics);
    PolicyRequirements policyRequirements = requiredPolicyObligations(definition, coherence);
    compareDeclaredObligations(definition, policyRequirements, diagnostics);
    registration.runtimeBinding().ifPresent(binding -> {
      validatePermissions(definition, binding.permissionMapping(), diagnostics);
      AdminCategoryPolicyReferences references = new AdminCategoryPolicyReferences(
        binding
          .policyBindings()
          .values()
          .stream()
          .map(policyBinding -> policyBinding.reference())
          .toList()
      );
      validatePolicyReferences(definition.systemKey(), policyRequirements, references, diagnostics);
    });
  }

  private static void validateRuntimePorts(
    AdminCategorySystemDefinition definition,
    AdminCategoryRuntimePorts ports,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    EnumSet<AdminCategoryProviderCapability> required = EnumSet.noneOf(
      AdminCategoryProviderCapability.class
    );
    for (Operation operation : definition.operations().supportedOperations()) {
      AdminCategoryProviderCapability.forOperation(operation).ifPresent(required::add);
    }
    Set<AdminCategoryProviderCapability> populated = ports.populatedCapabilities();
    for (AdminCategoryProviderCapability capability : AdminCategoryProviderCapability.values()) {
      if (required.contains(capability) && !populated.contains(capability)) {
        diagnostics.add(
          diagnostic(
            definition.systemKey(),
            AdminCategoryValidationCategory.MISSING_COLLABORATOR,
            AdminCategoryValidationComponent.RUNTIME_PORTS,
            null,
            null
          )
        );
      } else if (!required.contains(capability) && populated.contains(capability)) {
        diagnostics.add(
          diagnostic(
            definition.systemKey(),
            AdminCategoryValidationCategory.UNEXPECTED_COLLABORATOR,
            AdminCategoryValidationComponent.RUNTIME_PORTS,
            null,
            null
          )
        );
      }
    }
  }

  private static void validateCodec(
    AdminCategorySystemDefinition definition,
    AdminCategoryIdentifierCodec<?> codec,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    AdminCategoryIdentifierProfile profile;
    try {
      profile = codec.identifierProfile();
    } catch (RuntimeException exception) {
      profile = null;
    }
    if (profile != definition.identifierProfile()) {
      diagnostics.add(
        diagnostic(
          definition.systemKey(),
          AdminCategoryValidationCategory.PROFILE_CODEC_INCOMPATIBILITY,
          AdminCategoryValidationComponent.IDENTIFIER_CODEC,
          null,
          null
        )
      );
    }
  }

  private static DefinitionCoherence validateDefinition(
    AdminCategorySystemDefinition definition,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    String key = definition.systemKey();
    var operations = definition.operations().supportedOperations();
    var fields = definition.fieldsAndQueries();
    var hierarchy = definition.hierarchy();
    var ordering = definition.ordering();
    var lifecycle = definition.lifecycle();

    boolean fieldsCoherent = validateFields(key, fields, hierarchy, ordering, diagnostics);
    boolean hierarchyCoherent = validateHierarchy(
      key,
      hierarchy,
      operations,
      definition.apiProfile(),
      definition.operationalLimits(),
      diagnostics
    );
    boolean orderingCoherent = validateOrdering(key, ordering, fields, operations, diagnostics);
    boolean lifecycleCoherent = validateLifecycle(key, lifecycle, operations, diagnostics);
    boolean searchReferencesCoherent = fields
      .searchableFields()
      .stream()
      .allMatch(fields.fields()::contains);
    boolean filterReferencesCoherent = fields
      .allowedFilters()
      .stream()
      .allMatch(fields.fields()::contains);
    boolean sortReferencesCoherent = fields
      .allowedSorts()
      .stream()
      .allMatch(fields.fields()::contains);
    boolean queryGroup =
      operations.contains(Operation.LIST) ||
      (searchReferencesCoherent && !fields.searchableFields().isEmpty()) ||
      (filterReferencesCoherent && !fields.allowedFilters().isEmpty()) ||
      (sortReferencesCoherent && !fields.allowedSorts().isEmpty());
    boolean queryOracleEvaluable =
      queryGroup ||
      (searchReferencesCoherent && filterReferencesCoherent && sortReferencesCoherent);
    boolean hierarchyReadGroup =
      hierarchyCoherent &&
      hierarchy.enabled() &&
      HIERARCHY_READ_OPERATIONS.stream().anyMatch(
        operation ->
          operations.contains(operation) && hierarchy.supportedOperations().contains(operation)
      );
    boolean moveGroup =
      hierarchyCoherent &&
      hierarchy.enabled() &&
      operations.contains(Operation.MOVE) &&
      hierarchy.supportedOperations().contains(Operation.MOVE);

    validateLimits(
      key,
      definition.operationalLimits(),
      queryGroup,
      queryOracleEvaluable,
      hierarchyReadGroup,
      moveGroup,
      hierarchyCoherent,
      diagnostics
    );
    return new DefinitionCoherence(
      fieldsCoherent,
      hierarchyCoherent,
      orderingCoherent,
      lifecycleCoherent,
      queryGroup,
      queryOracleEvaluable,
      hierarchyReadGroup,
      moveGroup
    );
  }

  private static boolean validateFields(
    String key,
    FieldQueryDeclaration fields,
    HierarchyDeclaration hierarchy,
    OrderingDeclaration ordering,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    boolean coherent = true;
    for (LogicalField coreField : CORE_FIELDS) {
      if (!fields.fields().contains(coreField)) {
        coherent = false;
        addDefinitionIssue(
          key,
          AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
          null,
          diagnostics
        );
      }
    }
    for (LogicalField hierarchyField : Set.of(
      LogicalField.PARENT,
      LogicalField.HIERARCHY_CONTEXT
    )) {
      if (fields.fields().contains(hierarchyField) != hierarchy.enabled()) {
        coherent = false;
        addDefinitionIssue(
          key,
          AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
          AdminCategoryValidationCapability.HIERARCHY,
          diagnostics
        );
      }
    }
    boolean rankRequired = ordering.rankingEnabled() || ordering.rankExposed();
    if (fields.fields().contains(LogicalField.RANK) != rankRequired) {
      coherent = false;
      addDefinitionIssue(
        key,
        AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
        AdminCategoryValidationCapability.RANKING,
        diagnostics
      );
    }
    coherent &= validateReferencedFields(
      key,
      fields.searchableFields(),
      fields.fields(),
      AdminCategoryValidationCapability.SEARCH,
      diagnostics
    );
    coherent &= validateReferencedFields(
      key,
      fields.allowedFilters(),
      fields.fields(),
      AdminCategoryValidationCapability.FILTER,
      diagnostics
    );
    coherent &= validateReferencedFields(
      key,
      fields.allowedSorts(),
      fields.fields(),
      AdminCategoryValidationCapability.SORT,
      diagnostics
    );
    return coherent;
  }

  private static boolean validateReferencedFields(
    String key,
    Set<LogicalField> references,
    Set<LogicalField> fields,
    AdminCategoryValidationCapability capability,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    boolean coherent = true;
    for (LogicalField reference : references) {
      if (!fields.contains(reference)) {
        coherent = false;
        addDefinitionIssue(
          key,
          AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
          capability,
          diagnostics
        );
      }
    }
    return coherent;
  }

  private static boolean validateHierarchy(
    String key,
    HierarchyDeclaration hierarchy,
    Set<Operation> operations,
    AdminCategoryApiProfile profile,
    OperationalLimits limits,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    boolean coherent = true;
    if (hierarchy.enabled()) {
      if (hierarchy.semantics().isEmpty()) {
        coherent = false;
        addHierarchyIssue(key, AdminCategoryValidationCapability.HIERARCHY, diagnostics);
      }
      if (
        hierarchy.rootModel().isEmpty() || hierarchy.rootModel().orElse(null) != profile.rootModel()
      ) {
        coherent = false;
        addHierarchyIssue(key, AdminCategoryValidationCapability.HIERARCHY, diagnostics);
      }
      for (Operation operation : HIERARCHY_OPERATIONS) {
        if (operations.contains(operation) != hierarchy.supportedOperations().contains(operation)) {
          coherent = false;
          addHierarchyIssue(key, capability(operation), diagnostics);
        }
      }
    } else {
      if (hierarchy.semantics().isPresent()) {
        coherent = false;
        addHierarchyIssue(key, AdminCategoryValidationCapability.HIERARCHY, diagnostics);
      }
      if (hierarchy.rootModel().isPresent()) {
        coherent = false;
        addHierarchyIssue(key, AdminCategoryValidationCapability.HIERARCHY, diagnostics);
      }
      if (limits.treeSize().isPresent()) {
        coherent = false;
        addHierarchyIssue(key, AdminCategoryValidationCapability.TREE, diagnostics);
      }
      if (limits.parentOptionPageSize().isPresent()) {
        coherent = false;
        addHierarchyIssue(key, AdminCategoryValidationCapability.PARENT_OPTIONS, diagnostics);
      }
      if (limits.includeIdCount().isPresent()) {
        coherent = false;
        addHierarchyIssue(key, AdminCategoryValidationCapability.PARENT_OPTIONS, diagnostics);
      }
      if (limits.hierarchyDepth().isPresent()) {
        coherent = false;
        addHierarchyIssue(key, AdminCategoryValidationCapability.HIERARCHY, diagnostics);
      }
      for (Operation operation : hierarchy.supportedOperations()) {
        coherent = false;
        addHierarchyIssue(key, capability(operation), diagnostics);
      }
      for (Operation operation : HIERARCHY_OPERATIONS) {
        if (operations.contains(operation)) {
          coherent = false;
          addHierarchyIssue(key, capability(operation), diagnostics);
        }
      }
    }
    return coherent;
  }

  private static void addHierarchyIssue(
    String key,
    AdminCategoryValidationCapability capability,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    addDefinitionIssue(
      key,
      AdminCategoryValidationCategory.INVALID_HIERARCHY,
      capability,
      diagnostics
    );
  }

  private static boolean validateOrdering(
    String key,
    OrderingDeclaration ordering,
    FieldQueryDeclaration fields,
    Set<Operation> operations,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    boolean coherent = true;
    boolean reorderDeclared = operations.contains(Operation.REORDER);
    if (reorderDeclared != ordering.reorderSupported()) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.REORDER, diagnostics);
    }
    if (ordering.rankingEnabled() && !ordering.orderingEnabled()) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.RANKING, diagnostics);
    }
    if (ordering.rankExposed() && !ordering.rankingEnabled()) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.RANKING, diagnostics);
    }
    boolean manualPlacement = ordering
      .placementModes()
      .stream()
      .anyMatch(mode -> mode != PlacementMode.APPEND);
    if (manualPlacement && !ordering.rankingEnabled()) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.ORDERING, diagnostics);
    }
    if (ordering.rankingEnabled() && ordering.placementModes().isEmpty()) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.RANKING, diagnostics);
    }
    if (
      !ordering.orderingEnabled() &&
      (ordering.rankingEnabled() ||
        ordering.rankExposed() ||
        ordering.reorderSupported() ||
        !ordering.placementModes().isEmpty() ||
        !ordering.fallbackOrder().isEmpty())
    ) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.ORDERING, diagnostics);
    }
    if (!ordering.orderingEnabled() && ordering.reorderSupported()) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.REORDER, diagnostics);
    }
    if (!ordering.orderingEnabled() && (ordering.rankingEnabled() || ordering.rankExposed())) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.RANKING, diagnostics);
    }
    if (
      ordering.orderingEnabled() &&
      !ordering.fallbackOrder().isEmpty() &&
      ordering
        .fallbackOrder()
        .stream()
        .noneMatch(order -> order.field() == LogicalField.IDENTIFIER)
    ) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.SORT, diagnostics);
    }
    Map<LogicalField, Integer> fallbackOccurrences = new HashMap<>();
    for (LogicalOrder order : ordering.fallbackOrder()) {
      fallbackOccurrences.merge(order.field(), 1, Integer::sum);
    }
    for (LogicalOrder order : ordering.fallbackOrder()) {
      if (fallbackOccurrences.get(order.field()) > 1) {
        coherent = false;
        addOrderingIssue(key, AdminCategoryValidationCapability.SORT, diagnostics);
      }
      if (!fields.fields().contains(order.field())) {
        coherent = false;
        addDefinitionIssue(
          key,
          AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
          AdminCategoryValidationCapability.SORT,
          diagnostics
        );
      } else if (!fields.allowedSorts().contains(order.field())) {
        coherent = false;
        addOrderingIssue(key, AdminCategoryValidationCapability.SORT, diagnostics);
      }
      if (
        order.field() == LogicalField.RANK &&
        (!ordering.rankingEnabled() || order.direction() != SortDirection.ASC)
      ) {
        coherent = false;
        addOrderingIssue(key, AdminCategoryValidationCapability.RANKING, diagnostics);
      }
    }
    if (ordering.rankExposed() && !fields.allowedSorts().contains(LogicalField.RANK)) {
      coherent = false;
      addOrderingIssue(key, AdminCategoryValidationCapability.RANKING, diagnostics);
    }
    return coherent;
  }

  private static void addOrderingIssue(
    String key,
    AdminCategoryValidationCapability capability,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    addDefinitionIssue(
      key,
      AdminCategoryValidationCategory.INVALID_ORDERING_RANKING,
      capability,
      diagnostics
    );
  }

  private static boolean validateLifecycle(
    String key,
    LifecycleDeclaration lifecycle,
    Set<Operation> operations,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    boolean coherent = true;
    if (lifecycle.enabled()) {
      for (LifecycleState state : LifecycleState.values()) {
        if (!lifecycle.states().contains(state)) {
          coherent = false;
          addLifecycleIssue(key, AdminCategoryValidationCapability.LIFECYCLE, diagnostics);
        }
      }
      for (Operation action : Set.of(Operation.ACTIVATE, Operation.DEACTIVATE)) {
        if (operations.contains(action) != lifecycle.actions().contains(action)) {
          coherent = false;
          addLifecycleIssue(key, capability(action), diagnostics);
        }
      }
    } else {
      if (!lifecycle.states().isEmpty()) {
        coherent = false;
        addLifecycleIssue(key, AdminCategoryValidationCapability.LIFECYCLE, diagnostics);
      }
      for (Operation action : lifecycle.actions()) {
        coherent = false;
        addLifecycleIssue(key, capability(action), diagnostics);
      }
      for (Operation action : Set.of(Operation.ACTIVATE, Operation.DEACTIVATE)) {
        if (operations.contains(action)) {
          coherent = false;
          addLifecycleIssue(key, capability(action), diagnostics);
        }
      }
    }
    return coherent;
  }

  private static void addLifecycleIssue(
    String key,
    AdminCategoryValidationCapability capability,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    addDefinitionIssue(
      key,
      AdminCategoryValidationCategory.INVALID_LIFECYCLE,
      capability,
      diagnostics
    );
  }

  private static void validateLimits(
    String key,
    OperationalLimits limits,
    boolean queryGroup,
    boolean queryLimitsComparable,
    boolean hierarchyReadGroup,
    boolean moveGroup,
    boolean hierarchyLimitsComparable,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    if (queryLimitsComparable) {
      validateLimit(
        key,
        limits.pageSize(),
        queryGroup,
        AdminCategoryValidationCapability.LIST,
        diagnostics
      );
      validateLimit(
        key,
        limits.searchInputLength(),
        queryGroup,
        AdminCategoryValidationCapability.SEARCH,
        diagnostics
      );
      validateLimit(
        key,
        limits.filterSelections(),
        queryGroup,
        AdminCategoryValidationCapability.FILTER,
        diagnostics
      );
      validateLimit(
        key,
        limits.sortSelections(),
        queryGroup,
        AdminCategoryValidationCapability.SORT,
        diagnostics
      );
    }
    if (hierarchyLimitsComparable) {
      validateLimit(
        key,
        limits.treeSize(),
        hierarchyReadGroup,
        AdminCategoryValidationCapability.TREE,
        diagnostics
      );
      validateLimit(
        key,
        limits.parentOptionPageSize(),
        hierarchyReadGroup,
        AdminCategoryValidationCapability.PARENT_OPTIONS,
        diagnostics
      );
      validateLimit(
        key,
        limits.includeIdCount(),
        hierarchyReadGroup,
        AdminCategoryValidationCapability.PARENT_OPTIONS,
        diagnostics
      );
      validateLimit(
        key,
        limits.hierarchyDepth(),
        hierarchyReadGroup || moveGroup,
        AdminCategoryValidationCapability.HIERARCHY,
        diagnostics
      );
    }
  }

  private static void validateLimit(
    String key,
    Optional<Long> value,
    boolean required,
    AdminCategoryValidationCapability capability,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    if (value.isPresent() != required) {
      addDefinitionIssue(
        key,
        AdminCategoryValidationCategory.INVALID_LIMIT,
        capability,
        diagnostics
      );
    }
  }

  private static PolicyRequirements requiredPolicyObligations(
    AdminCategorySystemDefinition definition,
    DefinitionCoherence coherence
  ) {
    var required = EnumSet.of(AdminCategoryPolicyObligation.GENERAL_VALIDATION);
    var evaluable = EnumSet.of(
      AdminCategoryPolicyObligation.GENERAL_VALIDATION,
      AdminCategoryPolicyObligation.DELETION
    );
    var operations = definition.operations().supportedOperations();
    if (coherence.queryOracleEvaluable()) {
      evaluable.add(AdminCategoryPolicyObligation.QUERY);
    }
    if (coherence.queryGroup()) {
      required.add(AdminCategoryPolicyObligation.QUERY);
    }
    if (coherence.hierarchyCoherent()) {
      evaluable.add(AdminCategoryPolicyObligation.HIERARCHY);
      if (coherence.hierarchyReadGroup() || coherence.moveGroup()) {
        required.add(AdminCategoryPolicyObligation.HIERARCHY);
      }
    }
    boolean independentlyRequiresMutation = operations
      .stream()
      .anyMatch(Set.of(Operation.CREATE, Operation.REPLACE, Operation.PATCH)::contains);
    if (independentlyRequiresMutation || coherence.hierarchyCoherent()) {
      evaluable.add(AdminCategoryPolicyObligation.MUTATION_VALIDATION);
    }
    if (independentlyRequiresMutation || coherence.moveGroup()) {
      required.add(AdminCategoryPolicyObligation.MUTATION_VALIDATION);
    }
    var ordering = definition.ordering();
    boolean manualPlacement = ordering
      .placementModes()
      .stream()
      .anyMatch(mode -> mode != PlacementMode.APPEND);
    if (coherence.orderingCoherent()) {
      evaluable.add(AdminCategoryPolicyObligation.ORDERING);
      if (
        operations.contains(Operation.REORDER) ||
        ordering.rankingEnabled() ||
        ordering.rankExposed() ||
        manualPlacement
      ) {
        required.add(AdminCategoryPolicyObligation.ORDERING);
      }
    }
    if (operations.contains(Operation.DELETE)) {
      required.add(AdminCategoryPolicyObligation.DELETION);
    }
    if (coherence.lifecycleCoherent()) {
      evaluable.add(AdminCategoryPolicyObligation.LIFECYCLE);
      if (operations.contains(Operation.ACTIVATE) || operations.contains(Operation.DEACTIVATE)) {
        required.add(AdminCategoryPolicyObligation.LIFECYCLE);
      }
    }
    return new PolicyRequirements(Set.copyOf(required), Set.copyOf(evaluable));
  }

  private static void compareDeclaredObligations(
    AdminCategorySystemDefinition definition,
    PolicyRequirements requirements,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    Set<AdminCategoryPolicyObligation> declared = definition
      .operations()
      .requiredPolicyObligations();
    for (AdminCategoryPolicyObligation obligation : AdminCategoryPolicyObligation.values()) {
      if (
        requirements.evaluable().contains(obligation) &&
        requirements.required().contains(obligation) != declared.contains(obligation)
      ) {
        addDefinitionIssue(
          definition.systemKey(),
          AdminCategoryValidationCategory.INVALID_OPERATION_DECLARATION,
          null,
          diagnostics
        );
      }
    }
  }

  private static void validatePermissions(
    AdminCategorySystemDefinition definition,
    AdminCategoryPermissionMapping mapping,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    Set<Operation> declared = definition.operations().supportedOperations();
    for (Operation operation : declared) {
      if (mapping.permissionFor(operation).isEmpty()) {
        diagnostics.add(
          diagnostic(
            definition.systemKey(),
            AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING,
            AdminCategoryValidationComponent.PERMISSION_MAPPING,
            capability(operation),
            null
          )
        );
      }
    }
    for (Binding binding : mapping.bindings()) {
      if (!declared.contains(binding.operation())) {
        diagnostics.add(
          diagnostic(
            definition.systemKey(),
            AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING,
            AdminCategoryValidationComponent.PERMISSION_MAPPING,
            capability(binding.operation()),
            null
          )
        );
      }
    }
  }

  private static void validatePolicyReferences(
    String key,
    PolicyRequirements requirements,
    AdminCategoryPolicyReferences references,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    for (AdminCategoryPolicyObligation obligation : requirements.required()) {
      if (references.find(obligation).isEmpty()) {
        diagnostics.add(
          diagnostic(
            key,
            AdminCategoryValidationCategory.MISSING_COLLABORATOR,
            AdminCategoryValidationComponent.POLICY_REFERENCES,
            null,
            null
          )
        );
      }
    }
    for (AdminCategoryPolicyReference reference : references.references()) {
      if (
        requirements.evaluable().contains(reference.obligation()) &&
        !requirements.required().contains(reference.obligation())
      ) {
        diagnostics.add(
          diagnostic(
            key,
            AdminCategoryValidationCategory.UNEXPECTED_COLLABORATOR,
            AdminCategoryValidationComponent.POLICY_REFERENCES,
            null,
            reference
          )
        );
      }
    }
  }

  private static void addDefinitionIssue(
    String key,
    AdminCategoryValidationCategory category,
    AdminCategoryValidationCapability capability,
    BoundedRegistrationDiagnosticAccumulator diagnostics
  ) {
    diagnostics.add(
      diagnostic(key, category, AdminCategoryValidationComponent.DEFINITION, capability, null)
    );
  }

  private static AdminCategoryValidationDiagnostic diagnostic(
    String safeKey,
    AdminCategoryValidationCategory category,
    AdminCategoryValidationComponent component,
    AdminCategoryValidationCapability capability,
    AdminCategoryPolicyReference policyReference
  ) {
    return new AdminCategoryValidationDiagnostic(
      safeKey,
      category,
      Optional.ofNullable(component),
      Optional.ofNullable(capability),
      Optional.ofNullable(policyReference)
    );
  }

  private static AdminCategoryRegistrationValidationException single(
    String safeKey,
    AdminCategoryValidationCategory category,
    AdminCategoryValidationComponent component,
    AdminCategoryValidationCapability capability,
    AdminCategoryPolicyReference policyReference
  ) {
    return new AdminCategoryRegistrationValidationException(
      diagnostic(safeKey, category, component, capability, policyReference)
    );
  }

  private static AdminCategoryValidationCapability capability(Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> AdminCategoryValidationCapability.CAPABILITIES;
      case LIST -> AdminCategoryValidationCapability.LIST;
      case TREE -> AdminCategoryValidationCapability.TREE;
      case PARENT_OPTIONS -> AdminCategoryValidationCapability.PARENT_OPTIONS;
      case GET -> AdminCategoryValidationCapability.GET;
      case BREADCRUMB -> AdminCategoryValidationCapability.BREADCRUMB;
      case CREATE -> AdminCategoryValidationCapability.CREATE;
      case REPLACE -> AdminCategoryValidationCapability.REPLACE;
      case PATCH -> AdminCategoryValidationCapability.PATCH;
      case DELETE -> AdminCategoryValidationCapability.DELETE;
      case MOVE -> AdminCategoryValidationCapability.MOVE;
      case REORDER -> AdminCategoryValidationCapability.REORDER;
      case ACTIVATE -> AdminCategoryValidationCapability.ACTIVATE;
      case DEACTIVATE -> AdminCategoryValidationCapability.DEACTIVATE;
    };
  }

  private record CandidateFacts(
    AdminCategorySystemRegistration registration,
    String safeSystemKey
  ) {}

  private record PortOwnership(CandidateFacts candidate) {}

  private record DefinitionCoherence(
    boolean fieldsCoherent,
    boolean hierarchyCoherent,
    boolean orderingCoherent,
    boolean lifecycleCoherent,
    boolean queryGroup,
    boolean queryOracleEvaluable,
    boolean hierarchyReadGroup,
    boolean moveGroup
  ) {}

  private record PolicyRequirements(
    Set<AdminCategoryPolicyObligation> required,
    Set<AdminCategoryPolicyObligation> evaluable
  ) {}
}
