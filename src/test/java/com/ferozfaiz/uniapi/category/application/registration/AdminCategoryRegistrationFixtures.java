package com.ferozfaiz.uniapi.category.application.registration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryDeletionPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryGeneralValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryHierarchyPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryLifecyclePolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryMutationValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryOrderingPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryQueryPolicy;
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
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AdminCategoryRegistrationFixtures {

  static final String DEFAULT_SYSTEM_KEY = "taxonomic-fixture";
  static final String DEFAULT_OWNING_MODULE = "merchandising";

  private static final List<Operation> READ_OPERATIONS = List.of(
    Operation.CAPABILITIES,
    Operation.LIST,
    Operation.TREE,
    Operation.PARENT_OPTIONS,
    Operation.GET,
    Operation.BREADCRUMB
  );

  private AdminCategoryRegistrationFixtures() {}

  static AdminCategorySystemDefinition completeDefinition() {
    return completeDefinition(DEFAULT_SYSTEM_KEY, DEFAULT_OWNING_MODULE);
  }

  static AdminCategorySystemDefinition completeDefinition(String systemKey, String owningModule) {
    return new AdminCategorySystemDefinition(
      systemKey,
      owningModule,
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC,
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
      new OperationDeclaration(
        List.of(Operation.values()),
        List.of(AdminCategoryPolicyObligation.values())
      ),
      new HierarchyDeclaration(
        true,
        Optional.of(HierarchySemantics.PARENT_CHILD_TREE),
        Optional.of(RootModel.IMPLICIT_NULL_PARENT),
        List.of(Operation.TREE, Operation.BREADCRUMB, Operation.PARENT_OPTIONS, Operation.MOVE)
      ),
      new OrderingDeclaration(
        true,
        true,
        true,
        true,
        List.of(
          PlacementMode.APPEND,
          PlacementMode.BEFORE_ID,
          PlacementMode.AFTER_ID,
          PlacementMode.TARGET_INDEX
        ),
        List.of(
          new LogicalOrder(LogicalField.NAME, SortDirection.ASC),
          new LogicalOrder(LogicalField.IDENTIFIER, SortDirection.ASC)
        )
      ),
      new LifecycleDeclaration(
        true,
        List.of(LifecycleState.ACTIVE, LifecycleState.INACTIVE),
        List.of(Operation.ACTIVATE, Operation.DEACTIVATE)
      ),
      new FieldQueryDeclaration(
        List.of(LogicalField.values()),
        List.of(LogicalField.NAME, LogicalField.SLUG),
        List.of(LogicalField.PARENT, LogicalField.ACTIVE, LogicalField.SLUG),
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
      ),
      new OperationalLimits(
        Optional.of(1_000L),
        Optional.of(120L),
        Optional.of(4L),
        Optional.of(5L),
        Optional.of(10_000L),
        Optional.of(50L),
        Optional.of(25L),
        Optional.of(100L)
      )
    );
  }

  public static AdminCategorySystemDefinition completeDefinitionWithOperations(
    Operation... operations
  ) {
    AdminCategorySystemDefinition complete = completeDefinition();
    return new AdminCategorySystemDefinition(
      complete.systemKey(),
      complete.owningModule(),
      complete.apiProfile(),
      complete.identifierProfile(),
      new OperationDeclaration(
        List.of(operations),
        complete.operations().requiredPolicyObligations()
      ),
      complete.hierarchy(),
      complete.ordering(),
      complete.lifecycle(),
      complete.fieldsAndQueries(),
      complete.operationalLimits()
    );
  }

  static AdminCategoryPermissionMapping completePermissionMapping(String systemKey) {
    String readPermission = "admin:" + systemKey + "-categories:read";
    String writePermission = "admin:" + systemKey + "-categories:write";
    List<Binding> bindings = new ArrayList<>();
    for (Operation operation : Operation.values()) {
      bindings.add(
        new Binding(
          operation,
          READ_OPERATIONS.contains(operation) ? readPermission : writePermission
        )
      );
    }
    return new AdminCategoryPermissionMapping(bindings);
  }

  static AdminCategoryPolicyReferences completePolicyReferences() {
    return new AdminCategoryPolicyReferences(
      List.of(AdminCategoryPolicyObligation.values())
        .stream()
        .map(obligation -> policyReference(obligation, "governance", obligation.code()))
        .toList()
    );
  }

  static AdminCategoryRuntimePolicyBindings completeRuntimePolicyBindings() {
    return runtimePolicyBindings(completePolicyReferences());
  }

  static AdminCategoryRuntimePolicyBindings runtimePolicyBindings(
    AdminCategoryPolicyReferences references
  ) {
    List<
      com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding
    > bindings = new ArrayList<>();
    references.references().forEach(reference -> {
      switch (reference.obligation()) {
        case GENERAL_VALIDATION -> bindings.add(
          new GeneralValidationPolicyBinding(
            reference,
            mock(AdminCategoryGeneralValidationPolicy.class)
          )
        );
        case QUERY -> bindings.add(
          new QueryPolicyBinding(reference, mock(AdminCategoryQueryPolicy.class))
        );
        case HIERARCHY -> bindings.add(
          new HierarchyPolicyBinding(reference, mock(AdminCategoryHierarchyPolicy.class))
        );
        case MUTATION_VALIDATION -> bindings.add(
          new MutationValidationPolicyBinding(
            reference,
            mock(AdminCategoryMutationValidationPolicy.class)
          )
        );
        case ORDERING -> bindings.add(
          new OrderingPolicyBinding(reference, mock(AdminCategoryOrderingPolicy.class))
        );
        case DELETION -> bindings.add(
          new DeletionPolicyBinding(reference, mock(AdminCategoryDeletionPolicy.class))
        );
        case LIFECYCLE -> bindings.add(
          new LifecyclePolicyBinding(reference, mock(AdminCategoryLifecyclePolicy.class))
        );
      }
    });
    return new AdminCategoryRuntimePolicyBindings(bindings);
  }

  static AdminCategoryPolicyReference policyReference(
    AdminCategoryPolicyObligation obligation,
    String moduleKey,
    String policyKey
  ) {
    return new AdminCategoryPolicyReference(
      obligation,
      new AdminCategoryLogicalPolicyId(moduleKey, policyKey)
    );
  }

  static AdminCategoryIdentifierCodec<Long> numericCodec() {
    return new StandardNumericV1AdminCategoryIdentifierCodec();
  }

  @SuppressWarnings("unchecked")
  static AdminCategoryIdentifierCodec<Long> codec(AdminCategoryIdentifierProfile profile) {
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    when(codec.identifierProfile()).thenReturn(profile);
    return codec;
  }

  static AdminCategorySystemRegistration completeRegistration() {
    return completeRegistration(DEFAULT_SYSTEM_KEY, DEFAULT_OWNING_MODULE);
  }

  static AdminCategorySystemRegistration completeRegistration(
    String systemKey,
    String owningModule
  ) {
    return completeRegistration(completeDefinition(systemKey, owningModule), numericCodec());
  }

  static AdminCategorySystemRegistration completeRegistration(
    AdminCategorySystemDefinition definition,
    AdminCategoryIdentifierCodec<?> identifierCodec
  ) {
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
    AdminCategoryRuntimePorts ports = new AdminCategoryRuntimePorts(
      Optional.of(adapter),
      Optional.of((AdminCategoryHierarchyReadPort) adapter),
      Optional.of((AdminCategoryBasicMutationPort) adapter),
      Optional.of((AdminCategoryHierarchyMutationPort) adapter),
      Optional.of((AdminCategoryOrderingPort) adapter),
      Optional.of((AdminCategoryLifecyclePort) adapter)
    );
    AdminCategoryRuntimeBinding binding = new AdminCategoryRuntimeBinding(
      identifierCodec,
      completePermissionMapping(definition.systemKey()),
      completeRuntimePolicyBindings(),
      ports
    );
    return AdminCategorySystemRegistration.of(definition, binding);
  }

  static AdminCategorySystemRegistration incompleteRegistration(String systemKey) {
    AdminCategorySystemDefinition definition = completeDefinition(systemKey, DEFAULT_OWNING_MODULE);
    return new AdminCategorySystemRegistration(Optional.of(definition), Optional.empty());
  }
}
