package com.ferozfaiz.uniapi.registrationfixture;

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
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryApiProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryLogicalPolicyId;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReference;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReferences;
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
import java.util.List;
import java.util.Optional;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class RegistrationFixtureConfiguration {

  @Bean
  AdminCategorySystemRegistration fixtureRegistration(
    AdminCategoryIdentifierCodec<Long> identifierCodec
  ) {
    NeutralDirectPortAdapter adapter = Mockito.mock(NeutralDirectPortAdapter.class);

    AdminCategorySystemDefinition definition = new AdminCategorySystemDefinition(
      "taxonomic-fixture",
      "merchandising",
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC,
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
      new OperationDeclaration(
        List.of(
          Operation.CAPABILITIES,
          Operation.LIST,
          Operation.TREE,
          Operation.PARENT_OPTIONS,
          Operation.GET,
          Operation.BREADCRUMB,
          Operation.CREATE,
          Operation.REPLACE,
          Operation.PATCH,
          Operation.DELETE,
          Operation.MOVE,
          Operation.REORDER,
          Operation.ACTIVATE,
          Operation.DEACTIVATE
        ),
        List.of(
          AdminCategoryPolicyObligation.GENERAL_VALIDATION,
          AdminCategoryPolicyObligation.QUERY,
          AdminCategoryPolicyObligation.HIERARCHY,
          AdminCategoryPolicyObligation.MUTATION_VALIDATION,
          AdminCategoryPolicyObligation.ORDERING,
          AdminCategoryPolicyObligation.DELETION,
          AdminCategoryPolicyObligation.LIFECYCLE
        )
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
          PlacementMode.BEFORE_ID,
          PlacementMode.AFTER_ID,
          PlacementMode.TARGET_INDEX,
          PlacementMode.APPEND
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
        List.of(
          LogicalField.IDENTIFIER,
          LogicalField.PARENT,
          LogicalField.NAME,
          LogicalField.SLUG,
          LogicalField.ACTIVE,
          LogicalField.CREATED_AT,
          LogicalField.UPDATED_AT,
          LogicalField.RANK,
          LogicalField.HIERARCHY_CONTEXT
        ),
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

    String read = "admin:taxonomic-fixture-categories:read";
    String write = "admin:taxonomic-fixture-categories:write";
    AdminCategoryPermissionMapping permissions = new AdminCategoryPermissionMapping(
      List.of(
        new Binding(Operation.CAPABILITIES, read),
        new Binding(Operation.LIST, read),
        new Binding(Operation.TREE, read),
        new Binding(Operation.PARENT_OPTIONS, read),
        new Binding(Operation.GET, read),
        new Binding(Operation.BREADCRUMB, read),
        new Binding(Operation.CREATE, write),
        new Binding(Operation.REPLACE, write),
        new Binding(Operation.PATCH, write),
        new Binding(Operation.DELETE, write),
        new Binding(Operation.MOVE, write),
        new Binding(Operation.REORDER, write),
        new Binding(Operation.ACTIVATE, write),
        new Binding(Operation.DEACTIVATE, write)
      )
    );
    AdminCategoryPolicyReferences policies = new AdminCategoryPolicyReferences(
      List.of(
        reference(AdminCategoryPolicyObligation.GENERAL_VALIDATION, "general-validation"),
        reference(AdminCategoryPolicyObligation.QUERY, "query"),
        reference(AdminCategoryPolicyObligation.HIERARCHY, "hierarchy"),
        reference(AdminCategoryPolicyObligation.MUTATION_VALIDATION, "mutation-validation"),
        reference(AdminCategoryPolicyObligation.ORDERING, "ordering"),
        reference(AdminCategoryPolicyObligation.DELETION, "deletion"),
        reference(AdminCategoryPolicyObligation.LIFECYCLE, "lifecycle")
      )
    );

    AdminCategoryRuntimePolicyBindings runtimePolicies = new AdminCategoryRuntimePolicyBindings(
      List.of(
        new GeneralValidationPolicyBinding(
          policies.find(AdminCategoryPolicyObligation.GENERAL_VALIDATION).orElseThrow(),
          Mockito.mock(AdminCategoryGeneralValidationPolicy.class)
        ),
        new QueryPolicyBinding(
          policies.find(AdminCategoryPolicyObligation.QUERY).orElseThrow(),
          Mockito.mock(AdminCategoryQueryPolicy.class)
        ),
        new HierarchyPolicyBinding(
          policies.find(AdminCategoryPolicyObligation.HIERARCHY).orElseThrow(),
          Mockito.mock(AdminCategoryHierarchyPolicy.class)
        ),
        new MutationValidationPolicyBinding(
          policies.find(AdminCategoryPolicyObligation.MUTATION_VALIDATION).orElseThrow(),
          Mockito.mock(AdminCategoryMutationValidationPolicy.class)
        ),
        new OrderingPolicyBinding(
          policies.find(AdminCategoryPolicyObligation.ORDERING).orElseThrow(),
          Mockito.mock(AdminCategoryOrderingPolicy.class)
        ),
        new DeletionPolicyBinding(
          policies.find(AdminCategoryPolicyObligation.DELETION).orElseThrow(),
          Mockito.mock(AdminCategoryDeletionPolicy.class)
        ),
        new LifecyclePolicyBinding(
          policies.find(AdminCategoryPolicyObligation.LIFECYCLE).orElseThrow(),
          Mockito.mock(AdminCategoryLifecyclePolicy.class)
        )
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
    return AdminCategorySystemRegistration.of(
      definition,
      new AdminCategoryRuntimeBinding(identifierCodec, permissions, runtimePolicies, ports)
    );
  }

  private static AdminCategoryPolicyReference reference(
    AdminCategoryPolicyObligation obligation,
    String policyKey
  ) {
    return new AdminCategoryPolicyReference(
      obligation,
      new AdminCategoryLogicalPolicyId("governance", policyKey)
    );
  }

  private abstract static class NeutralDirectPortAdapter
    implements
      AdminCategoryReadPort,
      AdminCategoryHierarchyReadPort,
      AdminCategoryBasicMutationPort,
      AdminCategoryHierarchyMutationPort,
      AdminCategoryOrderingPort,
      AdminCategoryLifecyclePort {}
}
