package com.ferozfaiz.uniapi.category.application.policy;

import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryDeletionPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryGeneralValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryHierarchyPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryLifecyclePolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryMutationValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryOrderingPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryDeletionValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryGeneralValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryHierarchyValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryLifecycleValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryQueryPolicy;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.PlacementMode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdminCategoryDefinitionValidator
  implements
    AdminCategoryGeneralValidationPolicy,
    AdminCategoryQueryPolicy,
    AdminCategoryHierarchyPolicy,
    AdminCategoryMutationValidationPolicy,
    AdminCategoryOrderingPolicy,
    AdminCategoryDeletionPolicy,
    AdminCategoryLifecyclePolicy
{

  private static final java.util.regex.Pattern SLUG = java.util.regex.Pattern.compile(
    "^[a-z0-9]+(-[a-z0-9]+)*$"
  );

  @Override
  public void validate(AdminCategoryGeneralValidationInput input) {
    positive(input.resourceId(), "category ID");
    positive(input.parentId(), "parent ID");
  }

  @Override
  public AdminCategoryListQuery validateList(AdminCategoryListValidationInput input) {
    AdminCategorySystemDefinition definition = input.definition();
    AdminCategoryParentScope parentScope = parentScope(input.parentId());
    if (parentScope.isSiblingScoped() && !definition.hierarchy().enabled()) {
      throw invalid("category.api.list.parent-id.format", "parent scope is not supported");
    }
    String q = blankToNull(input.q());
    long searchMax = limit(definition.operationalLimits().searchInputLength(), 120L);
    if (q != null && q.length() > searchMax) {
      throw invalid(
        "category.api.list.search.size",
        "search must be at most " + searchMax,
        searchMax
      );
    }
    String slug = blankToNull(input.slug());
    if (slug != null && !SLUG.matcher(slug).matches()) {
      throw invalid(
        "category.api.list.slug.format",
        "slug may contain lowercase letters, numbers, and hyphens"
      );
    }
    requireFilter(definition, LogicalField.ACTIVE, input.active() != null);
    requireFilter(definition, LogicalField.SLUG, slug != null);
    int page = input.page() == null ? 0 : input.page();
    int size = input.size() == null ? 20 : input.size();
    if (page < 0) {
      throw invalid("category.api.list.page.min", "page must be zero or greater");
    }
    long maxSize = limit(definition.operationalLimits().pageSize(), 1_000L);
    if (size < 1) {
      throw invalid("category.api.list.size.min", "size must be at least 1");
    }
    if (size > maxSize) {
      throw invalid("category.api.list.size.max", "size must be at most " + maxSize, maxSize);
    }
    return new AdminCategoryListQuery(
      parentScope,
      new AdminCategoryFilters(input.active(), q, slug),
      new PageCriteria(page, size),
      sort(definition, input.sort(), input.direction(), parentScope, false)
    );
  }

  @Override
  public AdminCategoryTreeQuery validateTree(AdminCategoryTreeValidationInput input) {
    requireHierarchy(input.definition(), Operation.TREE);
    int page = input.page() == null ? 0 : input.page();
    int size =
      input.size() == null
        ? Math.toIntExact(limit(input.definition().operationalLimits().treeSize(), 10_000L))
        : input.size();
    long max = limit(input.definition().operationalLimits().treeSize(), 10_000L);
    if (page < 0) {
      throw invalid("category.api.list.page.min", "page must be zero or greater");
    }
    if (size < 1) {
      throw invalid("category.api.list.size.min", "size must be at least 1");
    }
    if (size > max) {
      throw com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.treeTooLarge(
        size,
        max
      );
    }
    return new AdminCategoryTreeQuery(
      new PageCriteria(page, size),
      sort(
        input.definition(),
        input.sort(),
        input.direction(),
        AdminCategoryParentScope.roots(),
        true
      )
    );
  }

  @Override
  public AdminCategoryParentOptionsQuery validateParentOptions(
    AdminCategoryParentOptionsValidationInput input
  ) {
    requireHierarchy(input.definition(), Operation.PARENT_OPTIONS);
    int limit =
      input.limit() == null ? AdminCategoryParentOptionsPolicy.DEFAULT_LIMIT : input.limit();
    long maximum = limit(
      input.definition().operationalLimits().parentOptionPageSize(),
      AdminCategoryParentOptionsPolicy.MAX_LIMIT
    );
    if (limit < 1) {
      throw invalid("category.api.parent-options.limit.min", "limit must be at least 1", 1);
    }
    if (limit > maximum) {
      throw invalid(
        "category.api.parent-options.limit.max",
        "limit must be at most " + maximum,
        maximum
      );
    }
    long includeMaximum = limit(
      input.definition().operationalLimits().includeIdCount(),
      AdminCategoryParentOptionsPolicy.MAX_INCLUDE_IDS
    );
    if (input.includeIds().size() > includeMaximum) {
      throw invalid(
        "category.api.parent-options.include-ids.max",
        "includeIds must contain at most " + includeMaximum + " values",
        includeMaximum
      );
    }
    return new AdminCategoryParentOptionsQuery(
      AdminCategoryParentOptionsPolicy.normalizeSearch(input.q()),
      limit,
      AdminCategoryParentOptionsPolicy.blankToNull(input.cursor()),
      AdminCategoryParentOptionsPolicy.normalizeIncludeIds(input.includeIds()),
      AdminCategoryParentOptionsPolicy.normalizeExcludeSubtreeId(input.excludeSubtreeId()),
      AdminCategoryParentOptionsPolicy.resolveIncludeInactive(input.includeInactive())
    );
  }

  @Override
  public void validate(AdminCategoryHierarchyValidationInput input) {
    switch (input.operation()) {
      case TREE, PARENT_OPTIONS, BREADCRUMB, MOVE -> requireHierarchy(
        input.definition(),
        input.operation()
      );
      case CREATE, REPLACE, PATCH -> {
        if (!input.definition().hierarchy().enabled()) {
          throw invalid(
            "category.api.category.hierarchy.unsupported",
            "hierarchy operation is not supported"
          );
        }
      }
      default -> throw invalid(
        "category.api.category.hierarchy.unsupported",
        "hierarchy operation is not supported"
      );
    }
    positive(input.resourceId(), "category ID");
    positive(input.parentId(), "parent ID");
    positive(input.excludeSubtreeId(), "excluded subtree ID");
  }

  @Override
  public AdminCategoryCreateCommand validateCreate(AdminCategoryCreateValidationInput input) {
    validateText(input.definition(), input.name(), input.slug(), true, true);
    return new AdminCategoryCreateCommand(
      input.parentId(),
      input.name(),
      input.slug(),
      Boolean.TRUE.equals(input.active()),
      validatePlacement(
        new AdminCategoryPlacementValidationInput(
          input.definition(),
          Operation.CREATE,
          input.parentId(),
          input.beforeId(),
          input.afterId(),
          input.targetIndex()
        )
      )
    );
  }

  @Override
  public AdminCategoryUpdateCommand validateReplace(AdminCategoryReplaceValidationInput input) {
    positive(input.id(), "category ID");
    validateText(input.definition(), input.name(), input.slug(), true, true);
    return new AdminCategoryUpdateCommand(
      input.parentId(),
      input.name(),
      input.slug(),
      Boolean.TRUE.equals(input.active())
    );
  }

  @Override
  public AdminCategoryPatchCommand validatePatch(AdminCategoryPatchValidationInput input) {
    positive(input.id(), "category ID");
    validateText(
      input.definition(),
      input.name(),
      input.slug(),
      input.namePresent(),
      input.slugPresent()
    );
    return new AdminCategoryPatchCommand(
      input.parentId(),
      input.parentIdPresent(),
      input.name(),
      input.namePresent(),
      input.slug(),
      input.slugPresent(),
      input.active(),
      input.activePresent()
    );
  }

  @Override
  public SparseOrderTarget validatePlacement(AdminCategoryPlacementValidationInput input) {
    int supplied =
      (input.beforeId() == null ? 0 : 1) +
      (input.afterId() == null ? 0 : 1) +
      (input.targetIndex() == null ? 0 : 1);
    if (supplied > 1) {
      throw invalid("category.api.category.placement.conflict", "specify only one placement field");
    }
    if (input.targetIndex() != null && input.targetIndex() < 0) {
      throw invalid(
        "category.api.category.placement.target-index.bounds",
        "targetIndex must be zero or greater",
        0
      );
    }
    PlacementMode mode =
      input.beforeId() != null
        ? PlacementMode.BEFORE_ID
        : input.afterId() != null
          ? PlacementMode.AFTER_ID
          : input.targetIndex() != null
            ? PlacementMode.TARGET_INDEX
            : PlacementMode.APPEND;
    if (!input.definition().ordering().placementModes().contains(mode)) {
      throw com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.invalidPlacementTarget();
    }
    return SparseOrderTarget.from(input.beforeId(), input.afterId(), input.targetIndex());
  }

  @Override
  public void validateDelete(AdminCategoryDeletionValidationInput input) {
    positive(input.id(), "category ID");
  }

  @Override
  public void validateLifecycle(AdminCategoryLifecycleValidationInput input) {
    positive(input.id(), "category ID");
    if (
      !input.definition().lifecycle().enabled() ||
      !input.definition().lifecycle().actions().contains(input.operation())
    ) {
      throw invalid(
        "category.api.category.lifecycle.unsupported",
        "lifecycle action is not supported"
      );
    }
  }

  private static void validateText(
    AdminCategorySystemDefinition definition,
    String name,
    String slug,
    boolean namePresent,
    boolean slugPresent
  ) {
    if (namePresent) {
      if (
        !definition.fieldsAndQueries().fields().contains(LogicalField.NAME) ||
        name == null ||
        name.isBlank()
      ) {
        throw invalid("category.api.category.name.required", "category name is required");
      }
      if (name.length() > 255) {
        throw invalid(
          "category.api.category.name.size",
          "category name must be at most 255 characters",
          255
        );
      }
    }
    if (slugPresent) {
      if (
        !definition.fieldsAndQueries().fields().contains(LogicalField.SLUG) ||
        slug == null ||
        !SLUG.matcher(slug).matches()
      ) {
        throw invalid(
          "category.api.slug.pattern",
          "slug may contain lowercase letters, numbers, and hyphens"
        );
      }
    }
  }

  private static void requireHierarchy(
    AdminCategorySystemDefinition definition,
    Operation operation
  ) {
    if (
      !definition.hierarchy().enabled() ||
      !definition.hierarchy().supportedOperations().contains(operation)
    ) {
      throw invalid(
        "category.api.category.hierarchy.unsupported",
        "hierarchy operation is not supported"
      );
    }
  }

  private static void requireFilter(
    AdminCategorySystemDefinition definition,
    LogicalField field,
    boolean present
  ) {
    if (present && !definition.fieldsAndQueries().allowedFilters().contains(field)) {
      throw invalid("category.api.list.filter.unsupported", "filter is not supported");
    }
  }

  private static AdminCategoryParentScope parentScope(String raw) {
    if (raw == null) {
      return AdminCategoryParentScope.all();
    }
    String value = raw.trim();
    if (!value.matches("[0-9]+")) {
      throw invalid(
        "category.api.list.parent-id.format",
        "parentId must be omitted, 0, or a positive integer"
      );
    }
    try {
      long id = Long.parseLong(value);
      return id == 0 ? AdminCategoryParentScope.roots() : AdminCategoryParentScope.children(id);
    } catch (RuntimeException failure) {
      throw invalid(
        "category.api.list.parent-id.format",
        "parentId must be omitted, 0, or a positive integer"
      );
    }
  }

  private static SortCriteria sort(
    AdminCategorySystemDefinition definition,
    List<String> raw,
    String fallbackDirection,
    AdminCategoryParentScope scope,
    boolean tree
  ) {
    List<String> values =
      raw == null
        ? List.of()
        : raw
            .stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .toList();
    List<SortOrder> orders = values.isEmpty()
      ? defaultSort(definition, scope)
      : parseSort(definition, values, fallbackDirection);
    long max = tree ? 1L : limit(definition.operationalLimits().sortSelections(), 5L);
    if (orders.size() > max) {
      throw invalid(
        "category.api.list.sort.max",
        "sort may contain at most " + max + " orders",
        max
      );
    }
    Set<String> seen = new HashSet<>();
    if (orders.stream().anyMatch(order -> !seen.add(order.property()))) {
      throw invalid("category.api.list.sort.duplicate", "sort must not repeat the same property");
    }
    if (orders.stream().noneMatch(order -> "id".equals(order.property()))) {
      orders = new ArrayList<>(orders);
      orders.add(new SortOrder("id", SortDirection.ASC));
    }
    return new SortCriteria(orders);
  }

  private static List<SortOrder> parseSort(
    AdminCategorySystemDefinition definition,
    List<String> values,
    String fallbackDirection
  ) {
    List<SortOrder> result = new ArrayList<>();
    if (values.stream().anyMatch(value -> value.contains(","))) {
      for (String value : values) {
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
          throw invalid("category.api.list.sort.format", "sort must use field,direction format");
        }
        result.add(sortOrder(definition, parts[0], parts[1]));
      }
      return result;
    }
    if (values.size() % 2 == 0 && directionsAtOddIndexes(values)) {
      for (int index = 0; index < values.size(); index += 2) {
        result.add(sortOrder(definition, values.get(index), values.get(index + 1)));
      }
      return result;
    }
    String direction =
      fallbackDirection == null || fallbackDirection.isBlank() ? "asc" : fallbackDirection;
    for (String value : values) {
      result.add(sortOrder(definition, value, direction));
    }
    return result;
  }

  private static SortOrder sortOrder(
    AdminCategorySystemDefinition definition,
    String property,
    String direction
  ) {
    LogicalField field = logicalField(property.trim());
    if (!definition.fieldsAndQueries().allowedSorts().contains(field)) {
      throw invalid("category.api.list.sort.allowed", "sort field is not supported");
    }
    SortDirection resolved;
    try {
      resolved = SortDirection.from(direction.trim());
    } catch (RuntimeException failure) {
      throw invalid("category.api.list.sort.direction", "sort direction must be asc or desc");
    }
    if (field == LogicalField.RANK && resolved != SortDirection.ASC) {
      throw invalid(
        "category.api.list.sort.sort-rank.unsupported",
        "sortRank requires ascending order"
      );
    }
    return new SortOrder(publicProperty(field), resolved);
  }

  private static List<SortOrder> defaultSort(
    AdminCategorySystemDefinition definition,
    AdminCategoryParentScope scope
  ) {
    if (
      scope.isSiblingScoped() &&
      definition.ordering().rankingEnabled() &&
      definition.ordering().rankExposed()
    ) {
      return List.of(new SortOrder("sortRank", SortDirection.ASC));
    }
    if (scope.isSiblingScoped() && !definition.ordering().fallbackOrder().isEmpty()) {
      return definition
        .ordering()
        .fallbackOrder()
        .stream()
        .map(order ->
          new SortOrder(
            publicProperty(order.field()),
            SortDirection.valueOf(order.direction().name())
          )
        )
        .toList();
    }
    return List.of(new SortOrder("updatedAt", SortDirection.DESC));
  }

  private static boolean directionsAtOddIndexes(List<String> values) {
    for (int index = 1; index < values.size(); index += 2) {
      if (!values.get(index).matches("(?i)asc|desc")) {
        return false;
      }
    }
    return true;
  }

  private static LogicalField logicalField(String property) {
    return switch (property) {
      case "id" -> LogicalField.IDENTIFIER;
      case "parentId" -> LogicalField.PARENT;
      case "name" -> LogicalField.NAME;
      case "slug" -> LogicalField.SLUG;
      case "active" -> LogicalField.ACTIVE;
      case "createdAt" -> LogicalField.CREATED_AT;
      case "updatedAt" -> LogicalField.UPDATED_AT;
      case "sortRank" -> LogicalField.RANK;
      default -> throw invalid("category.api.list.sort.allowed", "sort field is not supported");
    };
  }

  private static String publicProperty(LogicalField field) {
    return switch (field) {
      case IDENTIFIER -> "id";
      case PARENT -> "parentId";
      case NAME -> "name";
      case SLUG -> "slug";
      case ACTIVE -> "active";
      case CREATED_AT -> "createdAt";
      case UPDATED_AT -> "updatedAt";
      case RANK -> "sortRank";
      case HIERARCHY_CONTEXT -> throw invalid(
        "category.api.list.sort.allowed",
        "sort field is not supported"
      );
    };
  }

  private static long limit(java.util.Optional<Long> value, long fallback) {
    return value.orElse(fallback);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static void positive(Long value, String label) {
    if (value != null && value <= 0) {
      throw invalid("category.api.category.id.positive", label + " must be positive");
    }
  }

  private static InvalidRequestException invalid(String key, String message, Object... arguments) {
    return new InvalidRequestException(key, message, arguments);
  }
}
