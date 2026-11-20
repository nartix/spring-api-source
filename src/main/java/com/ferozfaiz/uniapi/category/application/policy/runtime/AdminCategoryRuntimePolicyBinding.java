package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReference;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationCategory;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationComponent;
import com.ferozfaiz.uniapi.category.application.registration.internal.AdminCategoryRegistrationFailures;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public sealed interface AdminCategoryRuntimePolicyBinding
  permits
    AdminCategoryRuntimePolicyBinding.GeneralValidationPolicyBinding,
    AdminCategoryRuntimePolicyBinding.QueryPolicyBinding,
    AdminCategoryRuntimePolicyBinding.HierarchyPolicyBinding,
    AdminCategoryRuntimePolicyBinding.MutationValidationPolicyBinding,
    AdminCategoryRuntimePolicyBinding.OrderingPolicyBinding,
    AdminCategoryRuntimePolicyBinding.DeletionPolicyBinding,
    AdminCategoryRuntimePolicyBinding.LifecyclePolicyBinding
{
  AdminCategoryPolicyReference reference();
  AdminCategoryRuntimePolicy collaborator();

  @NamedInterface("registration")
  record GeneralValidationPolicyBinding(
    AdminCategoryPolicyReference reference,
    AdminCategoryGeneralValidationPolicy collaborator
  ) implements AdminCategoryRuntimePolicyBinding {
    public GeneralValidationPolicyBinding {
      validate(reference, collaborator, AdminCategoryPolicyObligation.GENERAL_VALIDATION);
    }
  }

  @NamedInterface("registration")
  record QueryPolicyBinding(
    AdminCategoryPolicyReference reference,
    AdminCategoryQueryPolicy collaborator
  ) implements AdminCategoryRuntimePolicyBinding {
    public QueryPolicyBinding {
      validate(reference, collaborator, AdminCategoryPolicyObligation.QUERY);
    }
  }

  @NamedInterface("registration")
  record HierarchyPolicyBinding(
    AdminCategoryPolicyReference reference,
    AdminCategoryHierarchyPolicy collaborator
  ) implements AdminCategoryRuntimePolicyBinding {
    public HierarchyPolicyBinding {
      validate(reference, collaborator, AdminCategoryPolicyObligation.HIERARCHY);
    }
  }

  @NamedInterface("registration")
  record MutationValidationPolicyBinding(
    AdminCategoryPolicyReference reference,
    AdminCategoryMutationValidationPolicy collaborator
  ) implements AdminCategoryRuntimePolicyBinding {
    public MutationValidationPolicyBinding {
      validate(reference, collaborator, AdminCategoryPolicyObligation.MUTATION_VALIDATION);
    }
  }

  @NamedInterface("registration")
  record OrderingPolicyBinding(
    AdminCategoryPolicyReference reference,
    AdminCategoryOrderingPolicy collaborator
  ) implements AdminCategoryRuntimePolicyBinding {
    public OrderingPolicyBinding {
      validate(reference, collaborator, AdminCategoryPolicyObligation.ORDERING);
    }
  }

  @NamedInterface("registration")
  record DeletionPolicyBinding(
    AdminCategoryPolicyReference reference,
    AdminCategoryDeletionPolicy collaborator
  ) implements AdminCategoryRuntimePolicyBinding {
    public DeletionPolicyBinding {
      validate(reference, collaborator, AdminCategoryPolicyObligation.DELETION);
    }
  }

  @NamedInterface("registration")
  record LifecyclePolicyBinding(
    AdminCategoryPolicyReference reference,
    AdminCategoryLifecyclePolicy collaborator
  ) implements AdminCategoryRuntimePolicyBinding {
    public LifecyclePolicyBinding {
      validate(reference, collaborator, AdminCategoryPolicyObligation.LIFECYCLE);
    }
  }

  private static void validate(
    AdminCategoryPolicyReference reference,
    AdminCategoryRuntimePolicy collaborator,
    AdminCategoryPolicyObligation expected
  ) {
    if (reference == null || collaborator == null || reference.obligation() != expected) {
      throw AdminCategoryRegistrationFailures.failure(
        "<unavailable>",
        AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
        AdminCategoryValidationComponent.POLICY_REFERENCES
      );
    }
    Objects.requireNonNull(collaborator);
  }
}
