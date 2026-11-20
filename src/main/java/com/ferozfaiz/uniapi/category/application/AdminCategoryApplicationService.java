package com.ferozfaiz.uniapi.category.application;

import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.CategoryNode;
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
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicy;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminCategoryApplicationService {

  private static final Logger log = LoggerFactory.getLogger(AdminCategoryApplicationService.class);
  private static final Pattern SAFE_DIAGNOSTIC_SYSTEM_KEY = Pattern.compile(
    "[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*"
  );

  private final AdminCategorySystemRegistry registrations;

  public AdminCategoryApplicationService(AdminCategorySystemRegistry registrations) {
    this.registrations = registrations;
  }

  @Transactional(readOnly = true)
  public AdminCategorySystemDefinition definition(String systemKey, Operation operation) {
    return requireSupported(systemKey, operation).definition();
  }

  @Transactional(readOnly = true)
  public AdminCategorySystemDefinition capabilities(String systemKey) {
    return requireSupported(systemKey, Operation.CAPABILITIES).definition();
  }

  @Transactional(readOnly = true)
  public PageResult<CategoryNode> list(String systemKey, AdminCategoryListValidationInput input) {
    OperationContext context = requireSupported(systemKey, Operation.LIST);
    general(context).validate(
      new AdminCategoryGeneralValidationInput(context.definition(), Operation.LIST, null, null)
    );
    var query = query(context).validateList(withDefinition(context.definition(), input));
    return context.binding().ports().categoryRead().orElseThrow().list(query);
  }

  @Transactional(readOnly = true)
  public PageResult<CategoryNode> tree(String systemKey, AdminCategoryTreeValidationInput input) {
    OperationContext context = requireSupported(systemKey, Operation.TREE);
    general(context).validate(
      new AdminCategoryGeneralValidationInput(context.definition(), Operation.TREE, null, null)
    );
    hierarchy(context).validate(
      new AdminCategoryHierarchyValidationInput(
        context.definition(),
        Operation.TREE,
        null,
        null,
        null
      )
    );
    var query = query(context).validateTree(withDefinition(context.definition(), input));
    PageResult<CategoryNode> page = context
      .binding()
      .ports()
      .hierarchyRead()
      .orElseThrow()
      .tree(query);
    if (page.totalElements() > query.page().size()) {
      throw AdminCategoryErrors.treeTooLarge(page.totalElements(), query.page().size());
    }
    return page;
  }

  @Transactional(readOnly = true)
  public AdminCategoryModels.Category get(String systemKey, Long id) {
    return getNode(systemKey, id).category();
  }

  @Transactional(readOnly = true)
  public CategoryNode getNode(String systemKey, Long id) {
    OperationContext context = requireSupported(systemKey, Operation.GET);
    general(context).validate(
      new AdminCategoryGeneralValidationInput(context.definition(), Operation.GET, id, null)
    );
    return context.binding().ports().categoryRead().orElseThrow().getNode(id);
  }

  @Transactional(readOnly = true)
  public List<AdminCategoryModels.BreadcrumbItem> breadcrumb(String systemKey, Long id) {
    OperationContext context = requireSupported(systemKey, Operation.BREADCRUMB);
    general(context).validate(
      new AdminCategoryGeneralValidationInput(context.definition(), Operation.BREADCRUMB, id, null)
    );
    hierarchy(context).validate(
      new AdminCategoryHierarchyValidationInput(
        context.definition(),
        Operation.BREADCRUMB,
        id,
        null,
        null
      )
    );
    return context.binding().ports().hierarchyRead().orElseThrow().breadcrumb(id);
  }

  @Transactional(readOnly = true)
  public AdminCategoryModels.ParentOptionSlice parentOptions(
    String systemKey,
    AdminCategoryParentOptionsValidationInput input
  ) {
    OperationContext context = requireSupported(systemKey, Operation.PARENT_OPTIONS);
    general(context).validate(
      new AdminCategoryGeneralValidationInput(
        context.definition(),
        Operation.PARENT_OPTIONS,
        null,
        null
      )
    );
    hierarchy(context).validate(
      new AdminCategoryHierarchyValidationInput(
        context.definition(),
        Operation.PARENT_OPTIONS,
        null,
        null,
        null
      )
    );
    var query = query(context).validateParentOptions(withDefinition(context.definition(), input));
    return context.binding().ports().hierarchyRead().orElseThrow().parentOptions(query);
  }

  public AdminCategoryModels.Category create(
    String systemKey,
    AdminCategoryCreateValidationInput input
  ) {
    OperationContext context = requireSupported(systemKey, Operation.CREATE);
    AdminCategoryCreateValidationInput selected = withDefinition(context.definition(), input);
    general(context).validate(
      new AdminCategoryGeneralValidationInput(
        context.definition(),
        Operation.CREATE,
        null,
        selected.parentId()
      )
    );
    optionalHierarchy(context, Operation.CREATE, null, selected.parentId());
    optionalOrdering(context).ifPresent(policy ->
      policy.validatePlacement(
        new AdminCategoryPlacementValidationInput(
          context.definition(),
          Operation.CREATE,
          selected.parentId(),
          selected.beforeId(),
          selected.afterId(),
          selected.targetIndex()
        )
      )
    );
    var command = mutation(context).validateCreate(selected);
    AdminCategoryModels.Category category = context
      .binding()
      .ports()
      .basicMutation()
      .orElseThrow()
      .create(command);
    logMutationSuccess(context.systemKey(), "create", category.id());
    return category;
  }

  public AdminCategoryModels.Category update(
    String systemKey,
    Long id,
    AdminCategoryReplaceValidationInput input
  ) {
    OperationContext context = requireSupported(systemKey, Operation.REPLACE);
    AdminCategoryReplaceValidationInput selected = new AdminCategoryReplaceValidationInput(
      context.definition(),
      id,
      input.parentId(),
      input.name(),
      input.slug(),
      input.active()
    );
    general(context).validate(
      new AdminCategoryGeneralValidationInput(
        context.definition(),
        Operation.REPLACE,
        id,
        selected.parentId()
      )
    );
    optionalHierarchy(context, Operation.REPLACE, id, selected.parentId());
    var command = mutation(context).validateReplace(selected);
    AdminCategoryModels.Category category = context
      .binding()
      .ports()
      .basicMutation()
      .orElseThrow()
      .replace(id, command);
    logMutationSuccess(context.systemKey(), "update", category.id());
    return category;
  }

  public AdminCategoryModels.Category patch(
    String systemKey,
    Long id,
    AdminCategoryPatchValidationInput input
  ) {
    OperationContext context = requireSupported(systemKey, Operation.PATCH);
    AdminCategoryPatchValidationInput selected = new AdminCategoryPatchValidationInput(
      context.definition(),
      id,
      input.parentId(),
      input.parentIdPresent(),
      input.name(),
      input.namePresent(),
      input.slug(),
      input.slugPresent(),
      input.active(),
      input.activePresent()
    );
    general(context).validate(
      new AdminCategoryGeneralValidationInput(
        context.definition(),
        Operation.PATCH,
        id,
        selected.parentId()
      )
    );
    if (selected.parentIdPresent()) {
      optionalHierarchy(context, Operation.PATCH, id, selected.parentId());
    }
    var command = mutation(context).validatePatch(selected);
    AdminCategoryModels.Category category = context
      .binding()
      .ports()
      .basicMutation()
      .orElseThrow()
      .patch(id, command);
    logMutationSuccess(context.systemKey(), "patch", category.id());
    return category;
  }

  public void move(String systemKey, Long id, AdminCategoryPlacementValidationInput input) {
    OperationContext context = requireSupported(systemKey, Operation.MOVE);
    AdminCategoryPlacementValidationInput selected = withDefinition(
      context.definition(),
      Operation.MOVE,
      input
    );
    general(context).validate(
      new AdminCategoryGeneralValidationInput(
        context.definition(),
        Operation.MOVE,
        id,
        selected.parentId()
      )
    );
    hierarchy(context).validate(
      new AdminCategoryHierarchyValidationInput(
        context.definition(),
        Operation.MOVE,
        id,
        selected.parentId(),
        null
      )
    );
    var target = ordering(context).validatePlacement(selected);
    context
      .binding()
      .ports()
      .hierarchyMutation()
      .orElseThrow()
      .move(
        id,
        new com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand(
          selected.parentId(),
          target
        )
      );
    logMutationSuccess(context.systemKey(), "move", id);
  }

  public AdminCategoryModels.Category reorder(
    String systemKey,
    Long id,
    AdminCategoryPlacementValidationInput input
  ) {
    OperationContext context = requireSupported(systemKey, Operation.REORDER);
    AdminCategoryPlacementValidationInput selected = withDefinition(
      context.definition(),
      Operation.REORDER,
      input
    );
    general(context).validate(
      new AdminCategoryGeneralValidationInput(
        context.definition(),
        Operation.REORDER,
        id,
        selected.parentId()
      )
    );
    var target = ordering(context).validatePlacement(selected);
    AdminCategoryModels.Category category = context
      .binding()
      .ports()
      .ordering()
      .orElseThrow()
      .reorder(
        id,
        new com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand(
          selected.parentId(),
          target
        )
      );
    logMutationSuccess(context.systemKey(), "reorder", category.id());
    return category;
  }

  public void activate(String systemKey, Long id) {
    lifecycle(systemKey, id, Operation.ACTIVATE);
  }

  public void deactivate(String systemKey, Long id) {
    lifecycle(systemKey, id, Operation.DEACTIVATE);
  }

  public void delete(String systemKey, Long id) {
    OperationContext context = requireSupported(systemKey, Operation.DELETE);
    general(context).validate(
      new AdminCategoryGeneralValidationInput(context.definition(), Operation.DELETE, id, null)
    );
    deletion(context).validateDelete(
      new AdminCategoryDeletionValidationInput(context.definition(), id)
    );
    context.binding().ports().basicMutation().orElseThrow().delete(id);
    logMutationSuccess(context.systemKey(), "delete", id);
  }

  private void lifecycle(String systemKey, Long id, Operation operation) {
    OperationContext context = requireSupported(systemKey, operation);
    general(context).validate(
      new AdminCategoryGeneralValidationInput(context.definition(), operation, id, null)
    );
    lifecycle(context).validateLifecycle(
      new AdminCategoryLifecycleValidationInput(context.definition(), operation, id)
    );
    if (operation == Operation.ACTIVATE) {
      context.binding().ports().lifecycle().orElseThrow().activate(id);
    } else {
      context.binding().ports().lifecycle().orElseThrow().deactivate(id);
    }
    logMutationSuccess(context.systemKey(), operation.code(), id);
  }

  private OperationContext requireSupported(String rawSystemKey, Operation operation) {
    String systemKey = rawSystemKey == null ? "" : rawSystemKey.trim();
    AdminCategorySystemRegistration registration;
    try {
      registration = registrations
        .findRegistration(systemKey)
        .orElseThrow(() -> unknown(systemKey));
    } catch (AdminCategoryRegistrationValidationException failure) {
      throw unknown(systemKey);
    }
    AdminCategorySystemDefinition definition = registration.definition().orElseThrow();
    if (!definition.operations().supportedOperations().contains(operation)) {
      log
        .atWarn()
        .addKeyValue("category.event", "capability.unsupported")
        .addKeyValue("systemKey", systemKey)
        .addKeyValue("operation", operation.code())
        .addKeyValue("outcome", "failed")
        .addKeyValue("errorCode", "CATEGORY_CAPABILITY_UNSUPPORTED")
        .log("admin category capability unsupported");
      throw AdminCategoryErrors.capabilityUnsupported(operation.code(), systemKey);
    }
    return new OperationContext(
      systemKey,
      registration,
      definition,
      registration.runtimeBinding().orElseThrow()
    );
  }

  private static RuntimeException unknown(String systemKey) {
    String safeSystemKey = safeDiagnosticSystemKey(systemKey);
    log
      .atWarn()
      .addKeyValue("category.event", "system.unknown")
      .addKeyValue("systemKey", safeSystemKey)
      .addKeyValue("outcome", "failed")
      .addKeyValue("errorCode", "UNKNOWN_CATEGORY_SYSTEM")
      .log("unknown category system");
    return AdminCategoryErrors.unknownSystem(safeSystemKey);
  }

  private static String safeDiagnosticSystemKey(String systemKey) {
    return systemKey != null &&
      systemKey.length() <= 63 &&
      SAFE_DIAGNOSTIC_SYSTEM_KEY.matcher(systemKey).matches()
      ? systemKey
      : "<unavailable>";
  }

  private static AdminCategoryRuntimePolicy policy(
    OperationContext context,
    AdminCategoryPolicyObligation obligation
  ) {
    return optionalPolicy(context, obligation).orElseThrow();
  }

  private static java.util.Optional<AdminCategoryRuntimePolicy> optionalPolicy(
    OperationContext context,
    AdminCategoryPolicyObligation obligation
  ) {
    return context
      .binding()
      .policyBindings()
      .values()
      .stream()
      .filter(binding -> binding.reference().obligation() == obligation)
      .findFirst()
      .map(binding -> binding.collaborator());
  }

  private static AdminCategoryGeneralValidationPolicy general(OperationContext context) {
    return (AdminCategoryGeneralValidationPolicy) policy(
      context,
      AdminCategoryPolicyObligation.GENERAL_VALIDATION
    );
  }

  private static AdminCategoryQueryPolicy query(OperationContext context) {
    return (AdminCategoryQueryPolicy) policy(context, AdminCategoryPolicyObligation.QUERY);
  }

  private static AdminCategoryHierarchyPolicy hierarchy(OperationContext context) {
    return (AdminCategoryHierarchyPolicy) policy(context, AdminCategoryPolicyObligation.HIERARCHY);
  }

  private static AdminCategoryMutationValidationPolicy mutation(OperationContext context) {
    return (AdminCategoryMutationValidationPolicy) policy(
      context,
      AdminCategoryPolicyObligation.MUTATION_VALIDATION
    );
  }

  private static AdminCategoryOrderingPolicy ordering(OperationContext context) {
    return (AdminCategoryOrderingPolicy) policy(context, AdminCategoryPolicyObligation.ORDERING);
  }

  private static java.util.Optional<AdminCategoryOrderingPolicy> optionalOrdering(
    OperationContext context
  ) {
    return optionalPolicy(context, AdminCategoryPolicyObligation.ORDERING).map(
      AdminCategoryOrderingPolicy.class::cast
    );
  }

  private static AdminCategoryDeletionPolicy deletion(OperationContext context) {
    return (AdminCategoryDeletionPolicy) policy(context, AdminCategoryPolicyObligation.DELETION);
  }

  private static AdminCategoryLifecyclePolicy lifecycle(OperationContext context) {
    return (AdminCategoryLifecyclePolicy) policy(context, AdminCategoryPolicyObligation.LIFECYCLE);
  }

  private static void optionalHierarchy(
    OperationContext context,
    Operation operation,
    Long id,
    Long parentId
  ) {
    optionalPolicy(context, AdminCategoryPolicyObligation.HIERARCHY)
      .map(AdminCategoryHierarchyPolicy.class::cast)
      .ifPresent(policy ->
        policy.validate(
          new AdminCategoryHierarchyValidationInput(
            context.definition(),
            operation,
            id,
            parentId,
            null
          )
        )
      );
  }

  private static AdminCategoryListValidationInput withDefinition(
    AdminCategorySystemDefinition definition,
    AdminCategoryListValidationInput input
  ) {
    return new AdminCategoryListValidationInput(
      definition,
      input.page(),
      input.size(),
      input.parentId(),
      input.active(),
      input.q(),
      input.slug(),
      input.sort(),
      input.direction()
    );
  }

  private static AdminCategoryTreeValidationInput withDefinition(
    AdminCategorySystemDefinition definition,
    AdminCategoryTreeValidationInput input
  ) {
    return new AdminCategoryTreeValidationInput(
      definition,
      input.page(),
      input.size(),
      input.sort(),
      input.direction()
    );
  }

  private static AdminCategoryParentOptionsValidationInput withDefinition(
    AdminCategorySystemDefinition definition,
    AdminCategoryParentOptionsValidationInput input
  ) {
    return new AdminCategoryParentOptionsValidationInput(
      definition,
      input.q(),
      input.limit(),
      input.cursor(),
      input.includeIds(),
      input.excludeSubtreeId(),
      input.includeInactive()
    );
  }

  private static AdminCategoryCreateValidationInput withDefinition(
    AdminCategorySystemDefinition definition,
    AdminCategoryCreateValidationInput input
  ) {
    return new AdminCategoryCreateValidationInput(
      definition,
      input.parentId(),
      input.beforeId(),
      input.afterId(),
      input.targetIndex(),
      input.name(),
      input.slug(),
      input.active()
    );
  }

  private static AdminCategoryPlacementValidationInput withDefinition(
    AdminCategorySystemDefinition definition,
    Operation operation,
    AdminCategoryPlacementValidationInput input
  ) {
    return new AdminCategoryPlacementValidationInput(
      definition,
      operation,
      input.parentId(),
      input.beforeId(),
      input.afterId(),
      input.targetIndex()
    );
  }

  private static void logMutationSuccess(String systemKey, String operation, Long categoryId) {
    log
      .atInfo()
      .addKeyValue("category.event", "category." + operation + ".succeeded")
      .addKeyValue("systemKey", systemKey)
      .addKeyValue("operation", operation)
      .addKeyValue("outcome", "succeeded")
      .addKeyValue("categoryId", categoryId)
      .log("admin category mutation succeeded");
  }

  private record OperationContext(
    String systemKey,
    AdminCategorySystemRegistration registration,
    AdminCategorySystemDefinition definition,
    AdminCategoryRuntimeBinding binding
  ) {}
}
