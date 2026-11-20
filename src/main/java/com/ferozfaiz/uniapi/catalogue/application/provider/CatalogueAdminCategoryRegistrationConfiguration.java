package com.ferozfaiz.uniapi.catalogue.application.provider;

import com.ferozfaiz.uniapi.catalogue.application.provider.policy.CatalogueAdminCategoryRuntimePolicies;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryDeletionPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryGeneralValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryHierarchyPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryLifecyclePolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryMutationValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryOrderingPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryQueryPolicy;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryApiProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchySemantics;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleState;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.PlacementMode;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.RootModel;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CatalogueAdminCategoryRegistrationConfiguration {

  private static final String SYSTEM_KEY = "catalogue";

  @Bean
  AdminCategorySystemRegistration catalogueAdminCategorySystemRegistration(
    CatalogueAdminCategoryProvider provider,
    AdminCategoryIdentifierCodec<Long> identifierCodec,
    AdminCategoryGeneralValidationPolicy generalValidation,
    AdminCategoryQueryPolicy query,
    AdminCategoryHierarchyPolicy hierarchy,
    AdminCategoryMutationValidationPolicy mutationValidation,
    AdminCategoryOrderingPolicy ordering,
    AdminCategoryDeletionPolicy deletion,
    AdminCategoryLifecyclePolicy lifecycle
  ) {
    AdminCategorySystemDefinition definition = definition();
    var policies = new CatalogueAdminCategoryRuntimePolicies(
      generalValidation,
      query,
      hierarchy,
      mutationValidation,
      ordering,
      deletion,
      lifecycle
    );
    var ports = new AdminCategoryRuntimePorts(
      Optional.of(provider),
      Optional.of(provider),
      Optional.of(provider),
      Optional.of(provider),
      Optional.of(provider),
      Optional.of(provider)
    );
    return AdminCategorySystemRegistration.of(
      definition,
      new AdminCategoryRuntimeBinding(identifierCodec, permissions(), policies.bindings(), ports)
    );
  }

  private static AdminCategorySystemDefinition definition() {
    return new AdminCategorySystemDefinition(
      SYSTEM_KEY,
      "catalogue",
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
        List.of(Operation.TREE, Operation.PARENT_OPTIONS, Operation.BREADCRUMB, Operation.MOVE)
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
        List.of()
      ),
      new LifecycleDeclaration(
        true,
        List.of(LifecycleState.ACTIVE, LifecycleState.INACTIVE),
        List.of(Operation.ACTIVATE, Operation.DEACTIVATE)
      ),
      new FieldQueryDeclaration(
        List.of(LogicalField.values()),
        List.of(LogicalField.NAME, LogicalField.SLUG),
        List.of(LogicalField.PARENT, LogicalField.SLUG, LogicalField.ACTIVE),
        List.of(
          LogicalField.IDENTIFIER,
          LogicalField.PARENT,
          LogicalField.NAME,
          LogicalField.SLUG,
          LogicalField.ACTIVE,
          LogicalField.CREATED_AT,
          LogicalField.UPDATED_AT,
          LogicalField.RANK
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

  private static AdminCategoryPermissionMapping permissions() {
    List<Binding> bindings = new ArrayList<>();
    for (Operation operation : Operation.values()) {
      String access = operation.ordinal() <= Operation.BREADCRUMB.ordinal() ? "read" : "write";
      bindings.add(new Binding(operation, "admin:" + SYSTEM_KEY + "-categories:" + access));
    }
    return new AdminCategoryPermissionMapping(bindings);
  }
}
