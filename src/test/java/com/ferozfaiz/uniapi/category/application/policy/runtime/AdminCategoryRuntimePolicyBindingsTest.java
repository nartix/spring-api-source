package com.ferozfaiz.uniapi.category.application.policy.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.DeletionPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.GeneralValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.HierarchyPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.LifecyclePolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.MutationValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.OrderingPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.QueryPolicyBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryLogicalPolicyId;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReference;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminCategoryRuntimePolicyBindingsTest {

  @Test
  void storesAllSevenTypedVariantsInDeterministicObligationOrder() {
    List<AdminCategoryRuntimePolicyBinding> reversed = List.of(
      new LifecyclePolicyBinding(
        reference(AdminCategoryPolicyObligation.LIFECYCLE),
        mock(AdminCategoryLifecyclePolicy.class)
      ),
      new DeletionPolicyBinding(
        reference(AdminCategoryPolicyObligation.DELETION),
        mock(AdminCategoryDeletionPolicy.class)
      ),
      new OrderingPolicyBinding(
        reference(AdminCategoryPolicyObligation.ORDERING),
        mock(AdminCategoryOrderingPolicy.class)
      ),
      new MutationValidationPolicyBinding(
        reference(AdminCategoryPolicyObligation.MUTATION_VALIDATION),
        mock(AdminCategoryMutationValidationPolicy.class)
      ),
      new HierarchyPolicyBinding(
        reference(AdminCategoryPolicyObligation.HIERARCHY),
        mock(AdminCategoryHierarchyPolicy.class)
      ),
      new QueryPolicyBinding(
        reference(AdminCategoryPolicyObligation.QUERY),
        mock(AdminCategoryQueryPolicy.class)
      ),
      new GeneralValidationPolicyBinding(
        reference(AdminCategoryPolicyObligation.GENERAL_VALIDATION),
        mock(AdminCategoryGeneralValidationPolicy.class)
      )
    );

    AdminCategoryRuntimePolicyBindings bindings = new AdminCategoryRuntimePolicyBindings(reversed);

    assertThat(bindings.values())
      .extracting(binding -> binding.reference().obligation())
      .containsExactly(AdminCategoryPolicyObligation.values());
    assertThat(bindings.find(reference(AdminCategoryPolicyObligation.QUERY))).isPresent();
  }

  @Test
  void rejectsDuplicateExactReferences() {
    AdminCategoryPolicyReference reference = reference(AdminCategoryPolicyObligation.QUERY);
    AdminCategoryQueryPolicy collaborator = mock(AdminCategoryQueryPolicy.class);

    assertThatThrownBy(() ->
      new AdminCategoryRuntimePolicyBindings(
        List.of(
          new QueryPolicyBinding(reference, collaborator),
          new QueryPolicyBinding(reference, collaborator)
        )
      )
    ).isInstanceOf(AdminCategoryRegistrationValidationException.class);
  }

  @Test
  void rejectsAReferenceWhoseObligationDoesNotMatchTheVariant() {
    assertThatThrownBy(() ->
      new QueryPolicyBinding(
        reference(AdminCategoryPolicyObligation.ORDERING),
        mock(AdminCategoryQueryPolicy.class)
      )
    ).isInstanceOf(AdminCategoryRegistrationValidationException.class);
  }

  @Test
  void acceptsOneTypeCompatibleCollaboratorForDistinctExactBindings() {
    AdminCategoryQueryPolicy collaborator = mock(AdminCategoryQueryPolicy.class);
    AdminCategoryPolicyReference alpha = new AdminCategoryPolicyReference(
      AdminCategoryPolicyObligation.QUERY,
      new AdminCategoryLogicalPolicyId("alpha-module", "query")
    );
    AdminCategoryPolicyReference omega = new AdminCategoryPolicyReference(
      AdminCategoryPolicyObligation.QUERY,
      new AdminCategoryLogicalPolicyId("omega-module", "query")
    );

    AdminCategoryRuntimePolicyBindings bindings = new AdminCategoryRuntimePolicyBindings(
      List.of(
        new QueryPolicyBinding(omega, collaborator),
        new QueryPolicyBinding(alpha, collaborator)
      )
    );

    assertThat(bindings.values())
      .extracting(AdminCategoryRuntimePolicyBinding::reference)
      .containsExactly(alpha, omega);
    assertThat(bindings.find(alpha).orElseThrow().collaborator()).isSameAs(collaborator);
    assertThat(bindings.find(omega).orElseThrow().collaborator()).isSameAs(collaborator);
  }

  private static AdminCategoryPolicyReference reference(AdminCategoryPolicyObligation obligation) {
    return new AdminCategoryPolicyReference(
      obligation,
      new AdminCategoryLogicalPolicyId("governance", obligation.code())
    );
  }
}
