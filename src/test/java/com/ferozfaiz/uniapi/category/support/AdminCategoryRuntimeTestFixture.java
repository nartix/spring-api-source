package com.ferozfaiz.uniapi.category.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.DeletionPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.GeneralValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.HierarchyPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.LifecyclePolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.MutationValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.OrderingPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.QueryPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryApiProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryLogicalPolicyId;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReference;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchySemantics;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleState;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalOrder;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.PlacementMode;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.RootModel;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.SortDirection;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AdminCategoryRuntimeTestFixture {

  private AdminCategoryRuntimeTestFixture() {}

  public static AdminCategorySystemDefinition completeDefinition() {
    return completeDefinition("catalogue", Set.of(Operation.values()));
  }

  public static AdminCategorySystemDefinition completeDefinition(
    String systemKey,
    Set<Operation> operations
  ) {
    boolean hierarchyEnabled = operations
      .stream()
      .anyMatch(operation ->
        Set.of(
          Operation.TREE,
          Operation.PARENT_OPTIONS,
          Operation.BREADCRUMB,
          Operation.MOVE
        ).contains(operation)
      );
    boolean orderingEnabled = operations.contains(Operation.REORDER);
    boolean lifecycleEnabled =
      operations.contains(Operation.ACTIVATE) || operations.contains(Operation.DEACTIVATE);
    return new AdminCategorySystemDefinition(
      systemKey,
      "catalogue",
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC,
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
      new OperationDeclaration(operations, Set.of(AdminCategoryPolicyObligation.values())),
      new HierarchyDeclaration(
        hierarchyEnabled,
        hierarchyEnabled ? Optional.of(HierarchySemantics.PARENT_CHILD_TREE) : Optional.empty(),
        hierarchyEnabled ? Optional.of(RootModel.IMPLICIT_NULL_PARENT) : Optional.empty(),
        hierarchyEnabled
          ? operations
              .stream()
              .filter(operation ->
                Set.of(
                  Operation.TREE,
                  Operation.PARENT_OPTIONS,
                  Operation.BREADCRUMB,
                  Operation.MOVE
                ).contains(operation)
              )
              .collect(java.util.stream.Collectors.toUnmodifiableSet())
          : Set.of()
      ),
      new OrderingDeclaration(
        orderingEnabled,
        orderingEnabled,
        orderingEnabled,
        orderingEnabled,
        orderingEnabled
          ? Set.of(
              PlacementMode.APPEND,
              PlacementMode.BEFORE_ID,
              PlacementMode.AFTER_ID,
              PlacementMode.TARGET_INDEX
            )
          : Set.of(),
        List.of(
          new LogicalOrder(LogicalField.NAME, SortDirection.ASC),
          new LogicalOrder(LogicalField.IDENTIFIER, SortDirection.ASC)
        )
      ),
      new LifecycleDeclaration(
        lifecycleEnabled,
        lifecycleEnabled ? Set.of(LifecycleState.ACTIVE, LifecycleState.INACTIVE) : Set.of(),
        lifecycleEnabled
          ? operations
              .stream()
              .filter(
                operation -> operation == Operation.ACTIVATE || operation == Operation.DEACTIVATE
              )
              .collect(java.util.stream.Collectors.toUnmodifiableSet())
          : Set.of()
      ),
      new FieldQueryDeclaration(
        hierarchyEnabled || orderingEnabled
          ? Set.of(LogicalField.values())
          : Set.of(
              LogicalField.IDENTIFIER,
              LogicalField.NAME,
              LogicalField.SLUG,
              LogicalField.ACTIVE,
              LogicalField.CREATED_AT,
              LogicalField.UPDATED_AT
            ),
        Set.of(LogicalField.NAME, LogicalField.SLUG),
        hierarchyEnabled
          ? Set.of(LogicalField.PARENT, LogicalField.SLUG, LogicalField.ACTIVE)
          : Set.of(LogicalField.SLUG, LogicalField.ACTIVE),
        hierarchyEnabled || orderingEnabled
          ? Set.of(
              LogicalField.IDENTIFIER,
              LogicalField.PARENT,
              LogicalField.NAME,
              LogicalField.SLUG,
              LogicalField.ACTIVE,
              LogicalField.CREATED_AT,
              LogicalField.UPDATED_AT,
              LogicalField.RANK
            )
          : Set.of(
              LogicalField.IDENTIFIER,
              LogicalField.NAME,
              LogicalField.SLUG,
              LogicalField.ACTIVE,
              LogicalField.CREATED_AT,
              LogicalField.UPDATED_AT
            )
      ),
      new OperationalLimits(
        Optional.of(1_000L),
        Optional.of(120L),
        Optional.of(4L),
        Optional.of(5L),
        Optional.of(10_000L),
        Optional.of(50L),
        Optional.of(25L),
        Optional.of((long) Integer.MAX_VALUE)
      )
    );
  }

  public static Harness harness() {
    return harness(completeDefinition());
  }

  public static Harness harness(AdminCategorySystemDefinition definition) {
    var ports = new Ports(
      mock(AdminCategoryReadPort.class),
      mock(AdminCategoryHierarchyReadPort.class),
      mock(AdminCategoryBasicMutationPort.class),
      mock(AdminCategoryHierarchyMutationPort.class),
      mock(AdminCategoryOrderingPort.class),
      mock(AdminCategoryLifecyclePort.class)
    );
    var policy = new AdminCategoryDefinitionValidator();
    var references = new java.util.EnumMap<
      AdminCategoryPolicyObligation,
      AdminCategoryPolicyReference
    >(AdminCategoryPolicyObligation.class);
    for (AdminCategoryPolicyObligation obligation : AdminCategoryPolicyObligation.values()) {
      references.put(
        obligation,
        new AdminCategoryPolicyReference(
          obligation,
          new AdminCategoryLogicalPolicyId("test", obligation.code())
        )
      );
    }
    var policyBindings = new AdminCategoryRuntimePolicyBindings(
      List.of(
        new GeneralValidationPolicyBinding(
          references.get(AdminCategoryPolicyObligation.GENERAL_VALIDATION),
          policy
        ),
        new QueryPolicyBinding(references.get(AdminCategoryPolicyObligation.QUERY), policy),
        new HierarchyPolicyBinding(references.get(AdminCategoryPolicyObligation.HIERARCHY), policy),
        new MutationValidationPolicyBinding(
          references.get(AdminCategoryPolicyObligation.MUTATION_VALIDATION),
          policy
        ),
        new OrderingPolicyBinding(references.get(AdminCategoryPolicyObligation.ORDERING), policy),
        new DeletionPolicyBinding(references.get(AdminCategoryPolicyObligation.DELETION), policy),
        new LifecyclePolicyBinding(references.get(AdminCategoryPolicyObligation.LIFECYCLE), policy)
      )
    );
    var permissionBindings = new ArrayList<Binding>();
    for (Operation operation : definition.operations().supportedOperations()) {
      permissionBindings.add(
        new Binding(
          operation,
          "admin:" +
            definition.systemKey() +
            "-categories:" +
            (operation.ordinal() <= Operation.BREADCRUMB.ordinal() ? "read" : "write")
        )
      );
    }
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    when(codec.identifierProfile()).thenReturn(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);
    var binding = new AdminCategoryRuntimeBinding(
      codec,
      new AdminCategoryPermissionMapping(permissionBindings),
      policyBindings,
      new AdminCategoryRuntimePorts(
        Optional.of(ports.categoryRead()),
        Optional.of(ports.hierarchyRead()),
        Optional.of(ports.basicMutation()),
        Optional.of(ports.hierarchyMutation()),
        Optional.of(ports.ordering()),
        Optional.of(ports.lifecycle())
      )
    );
    var registration = AdminCategorySystemRegistration.of(definition, binding);
    var registry = mock(AdminCategorySystemRegistry.class);
    when(registry.findRegistration(definition.systemKey())).thenReturn(Optional.of(registration));
    return new Harness(
      definition,
      registration,
      registry,
      new AdminCategoryApplicationService(registry),
      ports
    );
  }

  public record Ports(
    AdminCategoryReadPort categoryRead,
    AdminCategoryHierarchyReadPort hierarchyRead,
    AdminCategoryBasicMutationPort basicMutation,
    AdminCategoryHierarchyMutationPort hierarchyMutation,
    AdminCategoryOrderingPort ordering,
    AdminCategoryLifecyclePort lifecycle
  ) {}

  public record Harness(
    AdminCategorySystemDefinition definition,
    AdminCategorySystemRegistration registration,
    AdminCategorySystemRegistry registry,
    AdminCategoryApplicationService service,
    Ports ports
  ) {}
}
