package com.ferozfaiz.uniapi.category.application.registration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record AdminCategorySystemDefinition(
  String systemKey,
  String owningModule,
  AdminCategoryApiProfile apiProfile,
  AdminCategoryIdentifierProfile identifierProfile,
  OperationDeclaration operations,
  HierarchyDeclaration hierarchy,
  OrderingDeclaration ordering,
  LifecycleDeclaration lifecycle,
  FieldQueryDeclaration fieldsAndQueries,
  OperationalLimits operationalLimits
) {
  private static final Pattern CANONICAL_KEY = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

  public AdminCategorySystemDefinition {
    if (!isCanonicalKey(systemKey)) {
      throw failure(
        AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
        AdminCategoryValidationCategory.INVALID_SYSTEM_KEY,
        Optional.empty()
      );
    }
    if (!isCanonicalKey(owningModule)) {
      throw failure(
        systemKey,
        AdminCategoryValidationCategory.INVALID_OWNING_MODULE,
        Optional.empty()
      );
    }
    if (!isKnownApiProfile(apiProfile) || !isKnownIdentifierProfile(identifierProfile)) {
      throw failure(systemKey, AdminCategoryValidationCategory.INVALID_PROFILE, Optional.empty());
    }
    if (identifierProfile != apiProfile.identifierProfile()) {
      throw failure(systemKey, AdminCategoryValidationCategory.INVALID_PROFILE, Optional.empty());
    }
    if (operations == null) {
      throw failure(
        systemKey,
        AdminCategoryValidationCategory.INVALID_OPERATION_DECLARATION,
        Optional.empty()
      );
    }
    for (Operation operation : operations.supportedOperations()) {
      if (!isKnownOperation(operation)) {
        throw failure(
          systemKey,
          AdminCategoryValidationCategory.INVALID_OPERATION_DECLARATION,
          Optional.empty()
        );
      }
      if (!apiProfile.supportedOperations().contains(operation)) {
        throw failure(
          systemKey,
          AdminCategoryValidationCategory.INVALID_OPERATION_DECLARATION,
          Optional.of(capability(operation))
        );
      }
    }
    if (hierarchy == null) {
      throw failure(
        systemKey,
        AdminCategoryValidationCategory.INVALID_HIERARCHY,
        Optional.of(AdminCategoryValidationCapability.HIERARCHY)
      );
    }
    if (ordering == null) {
      throw failure(
        systemKey,
        AdminCategoryValidationCategory.INVALID_ORDERING_RANKING,
        Optional.of(AdminCategoryValidationCapability.ORDERING)
      );
    }
    if (lifecycle == null) {
      throw failure(
        systemKey,
        AdminCategoryValidationCategory.INVALID_LIFECYCLE,
        Optional.of(AdminCategoryValidationCapability.LIFECYCLE)
      );
    }
    if (fieldsAndQueries == null) {
      throw failure(
        systemKey,
        AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
        Optional.empty()
      );
    }
    if (operationalLimits == null) {
      throw failure(systemKey, AdminCategoryValidationCategory.INVALID_LIMIT, Optional.empty());
    }
    validateMaxima(systemKey, apiProfile.hardMaxima(), operationalLimits);
  }

  public enum Operation {
    CAPABILITIES("capabilities"),
    LIST("list"),
    TREE("tree"),
    PARENT_OPTIONS("parent-options"),
    GET("get"),
    BREADCRUMB("breadcrumb"),
    CREATE("create"),
    REPLACE("replace"),
    PATCH("patch"),
    DELETE("delete"),
    MOVE("move"),
    REORDER("reorder"),
    ACTIVATE("activate"),
    DEACTIVATE("deactivate");

    private final String code;

    Operation(String code) {
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  public record OperationDeclaration(
    Set<Operation> supportedOperations,
    Set<AdminCategoryPolicyObligation> requiredPolicyObligations
  ) {
    public OperationDeclaration {
      supportedOperations = copyDeclaredOperations(supportedOperations);
      requiredPolicyObligations = copyPolicyObligations(requiredPolicyObligations);
    }

    public OperationDeclaration(
      Collection<Operation> supportedOperations,
      Collection<AdminCategoryPolicyObligation> requiredPolicyObligations
    ) {
      this(
        copyDeclaredOperations(supportedOperations),
        copyPolicyObligations(requiredPolicyObligations)
      );
    }
  }

  public enum LogicalField {
    IDENTIFIER("identifier"),
    PARENT("parent"),
    NAME("name"),
    SLUG("slug"),
    ACTIVE("active"),
    CREATED_AT("created-at"),
    UPDATED_AT("updated-at"),
    RANK("rank"),
    HIERARCHY_CONTEXT("hierarchy-context");

    private final String code;

    LogicalField(String code) {
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  public record FieldQueryDeclaration(
    Set<LogicalField> fields,
    Set<LogicalField> searchableFields,
    Set<LogicalField> allowedFilters,
    Set<LogicalField> allowedSorts
  ) {
    public FieldQueryDeclaration {
      fields = copyLogicalFields(fields, Optional.empty());
      searchableFields = copyLogicalFields(
        searchableFields,
        Optional.of(AdminCategoryValidationCapability.SEARCH)
      );
      allowedFilters = copyLogicalFields(
        allowedFilters,
        Optional.of(AdminCategoryValidationCapability.FILTER)
      );
      allowedSorts = copyLogicalFields(
        allowedSorts,
        Optional.of(AdminCategoryValidationCapability.SORT)
      );
    }

    public FieldQueryDeclaration(
      Collection<LogicalField> fields,
      Collection<LogicalField> searchableFields,
      Collection<LogicalField> allowedFilters,
      Collection<LogicalField> allowedSorts
    ) {
      this(
        copyLogicalFields(fields, Optional.empty()),
        copyLogicalFields(searchableFields, Optional.of(AdminCategoryValidationCapability.SEARCH)),
        copyLogicalFields(allowedFilters, Optional.of(AdminCategoryValidationCapability.FILTER)),
        copyLogicalFields(allowedSorts, Optional.of(AdminCategoryValidationCapability.SORT))
      );
    }
  }

  public enum HierarchySemantics {
    PARENT_CHILD_TREE,
  }

  public enum RootModel {
    IMPLICIT_NULL_PARENT,
  }

  public record HierarchyDeclaration(
    boolean enabled,
    Optional<HierarchySemantics> semantics,
    Optional<RootModel> rootModel,
    Set<Operation> supportedOperations
  ) {
    public HierarchyDeclaration {
      if (semantics == null || rootModel == null) {
        throw collectionFailure(Optional.of(AdminCategoryValidationCapability.HIERARCHY));
      }
      if (semantics.filter(value -> !isKnownHierarchySemantics(value)).isPresent()) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_HIERARCHY,
          Optional.of(AdminCategoryValidationCapability.HIERARCHY)
        );
      }
      if (rootModel.filter(value -> !isKnownRootModel(value)).isPresent()) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_HIERARCHY,
          Optional.of(AdminCategoryValidationCapability.HIERARCHY)
        );
      }
      supportedOperations = copyHierarchyOperations(supportedOperations);
    }

    public HierarchyDeclaration(
      boolean enabled,
      Optional<HierarchySemantics> semantics,
      Optional<RootModel> rootModel,
      Collection<Operation> supportedOperations
    ) {
      this(enabled, semantics, rootModel, copyHierarchyOperations(supportedOperations));
    }
  }

  public enum PlacementMode {
    APPEND,
    BEFORE_ID,
    AFTER_ID,
    TARGET_INDEX,
  }

  public enum SortDirection {
    ASC,
    DESC,
  }

  public record LogicalOrder(LogicalField field, SortDirection direction) {
    public LogicalOrder {
      if (field == null || direction == null) {
        throw collectionFailure(Optional.of(AdminCategoryValidationCapability.SORT));
      }
      if (!isKnownLogicalField(field)) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
          Optional.of(AdminCategoryValidationCapability.SORT)
        );
      }
      if (!isKnownSortDirection(direction)) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_ORDERING_RANKING,
          Optional.of(AdminCategoryValidationCapability.SORT)
        );
      }
    }
  }

  public record OrderingDeclaration(
    boolean orderingEnabled,
    boolean rankingEnabled,
    boolean rankExposed,
    boolean reorderSupported,
    Set<PlacementMode> placementModes,
    List<LogicalOrder> fallbackOrder
  ) {
    public OrderingDeclaration {
      placementModes = copyPlacementModes(placementModes);
      fallbackOrder = copyFallbackOrder(fallbackOrder);
    }

    public OrderingDeclaration(
      boolean orderingEnabled,
      boolean rankingEnabled,
      boolean rankExposed,
      boolean reorderSupported,
      Collection<PlacementMode> placementModes,
      Collection<LogicalOrder> fallbackOrder
    ) {
      this(
        orderingEnabled,
        rankingEnabled,
        rankExposed,
        reorderSupported,
        copyPlacementModes(placementModes),
        copyFallbackOrder(fallbackOrder)
      );
    }
  }

  public enum LifecycleState {
    ACTIVE,
    INACTIVE,
  }

  public record LifecycleDeclaration(
    boolean enabled,
    Set<LifecycleState> states,
    Set<Operation> actions
  ) {
    public LifecycleDeclaration {
      states = copyLifecycleStates(states);
      actions = copyLifecycleActions(actions);
    }

    public LifecycleDeclaration(
      boolean enabled,
      Collection<LifecycleState> states,
      Collection<Operation> actions
    ) {
      this(enabled, copyLifecycleStates(states), copyLifecycleActions(actions));
    }
  }

  public record OperationalLimits(
    Optional<Long> pageSize,
    Optional<Long> searchInputLength,
    Optional<Long> filterSelections,
    Optional<Long> sortSelections,
    Optional<Long> treeSize,
    Optional<Long> parentOptionPageSize,
    Optional<Long> includeIdCount,
    Optional<Long> hierarchyDepth
  ) {
    public OperationalLimits {
      pageSize = validateLimit(pageSize, AdminCategoryValidationCapability.LIST);
      searchInputLength = validateLimit(
        searchInputLength,
        AdminCategoryValidationCapability.SEARCH
      );
      filterSelections = validateLimit(filterSelections, AdminCategoryValidationCapability.FILTER);
      sortSelections = validateLimit(sortSelections, AdminCategoryValidationCapability.SORT);
      treeSize = validateLimit(treeSize, AdminCategoryValidationCapability.TREE);
      parentOptionPageSize = validateLimit(
        parentOptionPageSize,
        AdminCategoryValidationCapability.PARENT_OPTIONS
      );
      includeIdCount = validateLimit(
        includeIdCount,
        AdminCategoryValidationCapability.PARENT_OPTIONS
      );
      hierarchyDepth = validateLimit(hierarchyDepth, AdminCategoryValidationCapability.HIERARCHY);
    }
  }

  private static Set<Operation> copyDeclaredOperations(Collection<Operation> operations) {
    if (operations == null) {
      throw collectionFailure(Optional.empty());
    }
    LinkedHashSet<Operation> copy = new LinkedHashSet<>();
    for (Operation operation : operations) {
      if (operation == null) {
        throw collectionFailure(Optional.empty());
      }
      if (!copy.add(operation)) {
        throw collectionFailure(
          isKnownOperation(operation) ? Optional.of(capability(operation)) : Optional.empty()
        );
      }
    }
    return Collections.unmodifiableSet(copy);
  }

  private static Set<AdminCategoryPolicyObligation> copyPolicyObligations(
    Collection<AdminCategoryPolicyObligation> obligations
  ) {
    if (obligations == null) {
      throw collectionFailure(Optional.empty());
    }
    LinkedHashSet<AdminCategoryPolicyObligation> copy = new LinkedHashSet<>();
    for (AdminCategoryPolicyObligation obligation : obligations) {
      if (obligation == null) {
        throw collectionFailure(Optional.empty());
      }
      if (!isKnownPolicyObligation(obligation)) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_OPERATION_DECLARATION,
          Optional.empty()
        );
      }
      if (!copy.add(obligation)) {
        throw collectionFailure(Optional.empty());
      }
    }
    return Collections.unmodifiableSet(copy);
  }

  private static Set<LogicalField> copyLogicalFields(
    Collection<LogicalField> fields,
    Optional<AdminCategoryValidationCapability> capability
  ) {
    if (fields == null) {
      throw collectionFailure(capability);
    }
    LinkedHashSet<LogicalField> copy = new LinkedHashSet<>();
    for (LogicalField field : fields) {
      if (field == null) {
        throw collectionFailure(capability);
      }
      if (!isKnownLogicalField(field)) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_LOGICAL_REFERENCE,
          capability
        );
      }
      if (!copy.add(field)) {
        throw collectionFailure(capability);
      }
    }
    return Collections.unmodifiableSet(copy);
  }

  private static Set<Operation> copyHierarchyOperations(Collection<Operation> operations) {
    if (operations == null) {
      throw collectionFailure(Optional.of(AdminCategoryValidationCapability.HIERARCHY));
    }
    LinkedHashSet<Operation> copy = new LinkedHashSet<>();
    for (Operation operation : operations) {
      if (operation == null) {
        throw collectionFailure(Optional.of(AdminCategoryValidationCapability.HIERARCHY));
      }
      if (!isKnownOperation(operation)) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_HIERARCHY,
          Optional.empty()
        );
      }
      if (!isHierarchyOperation(operation)) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_HIERARCHY,
          Optional.of(capability(operation))
        );
      }
      if (!copy.add(operation)) {
        throw collectionFailure(Optional.of(capability(operation)));
      }
    }
    return Collections.unmodifiableSet(copy);
  }

  private static Set<PlacementMode> copyPlacementModes(Collection<PlacementMode> placementModes) {
    if (placementModes == null) {
      throw collectionFailure(Optional.of(AdminCategoryValidationCapability.ORDERING));
    }
    LinkedHashSet<PlacementMode> copy = new LinkedHashSet<>();
    for (PlacementMode placementMode : placementModes) {
      if (placementMode == null) {
        throw collectionFailure(Optional.of(AdminCategoryValidationCapability.ORDERING));
      }
      if (!isKnownPlacementMode(placementMode)) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_ORDERING_RANKING,
          Optional.of(AdminCategoryValidationCapability.ORDERING)
        );
      }
      if (!copy.add(placementMode)) {
        throw collectionFailure(Optional.of(AdminCategoryValidationCapability.ORDERING));
      }
    }
    return Collections.unmodifiableSet(copy);
  }

  private static List<LogicalOrder> copyFallbackOrder(Collection<LogicalOrder> fallbackOrder) {
    if (fallbackOrder == null) {
      throw collectionFailure(Optional.of(AdminCategoryValidationCapability.ORDERING));
    }
    List<LogicalOrder> copy = new ArrayList<>(fallbackOrder.size());
    Set<LogicalOrder> seen = new HashSet<>();
    for (LogicalOrder order : fallbackOrder) {
      if (order == null || !seen.add(order)) {
        throw collectionFailure(Optional.of(AdminCategoryValidationCapability.ORDERING));
      }
      copy.add(order);
    }
    return List.copyOf(copy);
  }

  private static Set<LifecycleState> copyLifecycleStates(Collection<LifecycleState> states) {
    if (states == null) {
      throw collectionFailure(Optional.of(AdminCategoryValidationCapability.LIFECYCLE));
    }
    LinkedHashSet<LifecycleState> copy = new LinkedHashSet<>();
    for (LifecycleState state : states) {
      if (state == null) {
        throw collectionFailure(Optional.of(AdminCategoryValidationCapability.LIFECYCLE));
      }
      if (!isKnownLifecycleState(state)) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_LIFECYCLE,
          Optional.of(AdminCategoryValidationCapability.LIFECYCLE)
        );
      }
      if (!copy.add(state)) {
        throw collectionFailure(Optional.of(AdminCategoryValidationCapability.LIFECYCLE));
      }
    }
    return Collections.unmodifiableSet(copy);
  }

  private static Set<Operation> copyLifecycleActions(Collection<Operation> actions) {
    if (actions == null) {
      throw collectionFailure(Optional.of(AdminCategoryValidationCapability.LIFECYCLE));
    }
    LinkedHashSet<Operation> copy = new LinkedHashSet<>();
    for (Operation action : actions) {
      if (action == null) {
        throw collectionFailure(Optional.of(AdminCategoryValidationCapability.LIFECYCLE));
      }
      if (!isKnownOperation(action)) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_LIFECYCLE,
          Optional.empty()
        );
      }
      if (action != Operation.ACTIVATE && action != Operation.DEACTIVATE) {
        throw failure(
          AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
          AdminCategoryValidationCategory.INVALID_LIFECYCLE,
          Optional.of(capability(action))
        );
      }
      if (!copy.add(action)) {
        throw collectionFailure(Optional.of(capability(action)));
      }
    }
    return Collections.unmodifiableSet(copy);
  }

  private static Optional<Long> validateLimit(
    Optional<Long> limit,
    AdminCategoryValidationCapability capability
  ) {
    if (limit == null || limit.filter(value -> value <= 0).isPresent()) {
      throw failure(
        AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
        AdminCategoryValidationCategory.INVALID_LIMIT,
        Optional.of(capability)
      );
    }
    return limit;
  }

  private static void validateMaxima(
    String systemKey,
    AdminCategoryApiProfile.HardMaxima maxima,
    OperationalLimits limits
  ) {
    validateMaximum(
      systemKey,
      limits.pageSize(),
      maxima.pageSize(),
      AdminCategoryValidationCapability.LIST
    );
    validateMaximum(
      systemKey,
      limits.searchInputLength(),
      maxima.searchInputLength(),
      AdminCategoryValidationCapability.SEARCH
    );
    validateMaximum(
      systemKey,
      limits.filterSelections(),
      maxima.filterSelections(),
      AdminCategoryValidationCapability.FILTER
    );
    validateMaximum(
      systemKey,
      limits.sortSelections(),
      maxima.sortSelections(),
      AdminCategoryValidationCapability.SORT
    );
    validateMaximum(
      systemKey,
      limits.treeSize(),
      maxima.treeSize(),
      AdminCategoryValidationCapability.TREE
    );
    validateMaximum(
      systemKey,
      limits.parentOptionPageSize(),
      maxima.parentOptionPageSize(),
      AdminCategoryValidationCapability.PARENT_OPTIONS
    );
    validateMaximum(
      systemKey,
      limits.includeIdCount(),
      maxima.includeIdCount(),
      AdminCategoryValidationCapability.PARENT_OPTIONS
    );
    validateMaximum(
      systemKey,
      limits.hierarchyDepth(),
      maxima.hierarchyDepth(),
      AdminCategoryValidationCapability.HIERARCHY
    );
  }

  private static void validateMaximum(
    String systemKey,
    Optional<Long> value,
    long maximum,
    AdminCategoryValidationCapability capability
  ) {
    if (value.filter(limit -> limit > maximum).isPresent()) {
      throw failure(
        systemKey,
        AdminCategoryValidationCategory.INVALID_LIMIT,
        Optional.of(capability)
      );
    }
  }

  private static boolean isCanonicalKey(String value) {
    return value != null && value.length() <= 63 && CANONICAL_KEY.matcher(value).matches();
  }

  private static boolean isKnownApiProfile(AdminCategoryApiProfile value) {
    for (AdminCategoryApiProfile known : AdminCategoryApiProfile.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownIdentifierProfile(AdminCategoryIdentifierProfile value) {
    for (AdminCategoryIdentifierProfile known : AdminCategoryIdentifierProfile.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownOperation(Operation value) {
    for (Operation known : Operation.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownPolicyObligation(AdminCategoryPolicyObligation value) {
    for (AdminCategoryPolicyObligation known : AdminCategoryPolicyObligation.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownLogicalField(LogicalField value) {
    for (LogicalField known : LogicalField.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownHierarchySemantics(HierarchySemantics value) {
    for (HierarchySemantics known : HierarchySemantics.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownRootModel(RootModel value) {
    for (RootModel known : RootModel.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownPlacementMode(PlacementMode value) {
    for (PlacementMode known : PlacementMode.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownSortDirection(SortDirection value) {
    for (SortDirection known : SortDirection.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownLifecycleState(LifecycleState value) {
    for (LifecycleState known : LifecycleState.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isHierarchyOperation(Operation operation) {
    return (
      operation == Operation.TREE ||
      operation == Operation.BREADCRUMB ||
      operation == Operation.PARENT_OPTIONS ||
      operation == Operation.MOVE
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

  private static AdminCategoryRegistrationValidationException collectionFailure(
    Optional<AdminCategoryValidationCapability> capability
  ) {
    return failure(
      AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      capability
    );
  }

  private static AdminCategoryRegistrationValidationException failure(
    String safeSystemKey,
    AdminCategoryValidationCategory category,
    Optional<AdminCategoryValidationCapability> capability
  ) {
    return AdminCategoryRegistrationDiagnostics.single(
      AdminCategoryRegistrationDiagnostics.diagnostic(
        safeSystemKey,
        category,
        Optional.of(AdminCategoryValidationComponent.DEFINITION),
        capability,
        Optional.empty()
      )
    );
  }
}
