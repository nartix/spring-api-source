package com.ferozfaiz.uniapi.catalogue.application.provider.policy;

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
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryLogicalPolicyId;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReference;
import java.util.List;
import java.util.Objects;

public final class CatalogueAdminCategoryRuntimePolicies {

  private final AdminCategoryGeneralValidationPolicy generalValidation;
  private final AdminCategoryQueryPolicy query;
  private final AdminCategoryHierarchyPolicy hierarchy;
  private final AdminCategoryMutationValidationPolicy mutationValidation;
  private final AdminCategoryOrderingPolicy ordering;
  private final AdminCategoryDeletionPolicy deletion;
  private final AdminCategoryLifecyclePolicy lifecycle;

  public CatalogueAdminCategoryRuntimePolicies(
    AdminCategoryGeneralValidationPolicy generalValidation,
    AdminCategoryQueryPolicy query,
    AdminCategoryHierarchyPolicy hierarchy,
    AdminCategoryMutationValidationPolicy mutationValidation,
    AdminCategoryOrderingPolicy ordering,
    AdminCategoryDeletionPolicy deletion,
    AdminCategoryLifecyclePolicy lifecycle
  ) {
    this.generalValidation = Objects.requireNonNull(generalValidation);
    this.query = Objects.requireNonNull(query);
    this.hierarchy = Objects.requireNonNull(hierarchy);
    this.mutationValidation = Objects.requireNonNull(mutationValidation);
    this.ordering = Objects.requireNonNull(ordering);
    this.deletion = Objects.requireNonNull(deletion);
    this.lifecycle = Objects.requireNonNull(lifecycle);
  }

  public AdminCategoryRuntimePolicyBindings bindings() {
    return new AdminCategoryRuntimePolicyBindings(
      List.of(
        new GeneralValidationPolicyBinding(
          reference(AdminCategoryPolicyObligation.GENERAL_VALIDATION),
          generalValidation
        ),
        new QueryPolicyBinding(reference(AdminCategoryPolicyObligation.QUERY), query),
        new HierarchyPolicyBinding(reference(AdminCategoryPolicyObligation.HIERARCHY), hierarchy),
        new MutationValidationPolicyBinding(
          reference(AdminCategoryPolicyObligation.MUTATION_VALIDATION),
          mutationValidation
        ),
        new OrderingPolicyBinding(reference(AdminCategoryPolicyObligation.ORDERING), ordering),
        new DeletionPolicyBinding(reference(AdminCategoryPolicyObligation.DELETION), deletion),
        new LifecyclePolicyBinding(reference(AdminCategoryPolicyObligation.LIFECYCLE), lifecycle)
      )
    );
  }

  private static AdminCategoryPolicyReference reference(AdminCategoryPolicyObligation obligation) {
    return new AdminCategoryPolicyReference(
      obligation,
      new AdminCategoryLogicalPolicyId("catalogue", obligation.code())
    );
  }
}
