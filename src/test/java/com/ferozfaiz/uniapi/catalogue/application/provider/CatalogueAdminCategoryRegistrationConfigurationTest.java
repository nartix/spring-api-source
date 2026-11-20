package com.ferozfaiz.uniapi.catalogue.application.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchySemantics;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleState;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.PlacementMode;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.RootModel;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CatalogueAdminCategoryRegistrationConfigurationTest {

  @Test
  void contributesOneCompleteCatalogueRegistrationByDefault() {
    @SuppressWarnings("unchecked")
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    when(codec.identifierProfile()).thenReturn(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);
    CatalogueAdminCategoryProvider provider = mock(
      CatalogueAdminCategoryProvider.class,
      invocation -> {
        if ("systemKey".equals(invocation.getMethod().getName())) return "catalogue";
        return Answers.RETURNS_DEFAULTS.answer(invocation);
      }
    );

    new ApplicationContextRunner()
      .withUserConfiguration(CatalogueAdminCategoryRegistrationConfiguration.class)
      .withBean(CatalogueAdminCategoryProvider.class, () -> provider)
      .withBean(AdminCategoryIdentifierCodec.class, () -> codec)
      .withBean(AdminCategoryGeneralValidationPolicy.class, () ->
        mock(AdminCategoryGeneralValidationPolicy.class)
      )
      .withBean(AdminCategoryQueryPolicy.class, () -> mock(AdminCategoryQueryPolicy.class))
      .withBean(AdminCategoryHierarchyPolicy.class, () -> mock(AdminCategoryHierarchyPolicy.class))
      .withBean(AdminCategoryMutationValidationPolicy.class, () ->
        mock(AdminCategoryMutationValidationPolicy.class)
      )
      .withBean(AdminCategoryOrderingPolicy.class, () -> mock(AdminCategoryOrderingPolicy.class))
      .withBean(AdminCategoryDeletionPolicy.class, () -> mock(AdminCategoryDeletionPolicy.class))
      .withBean(AdminCategoryLifecyclePolicy.class, () -> mock(AdminCategoryLifecyclePolicy.class))
      .run(context -> {
        assertThat(context).hasNotFailed();
        assertThat(context).hasSingleBean(AdminCategorySystemRegistration.class);

        AdminCategorySystemRegistration registration = context.getBean(
          AdminCategorySystemRegistration.class
        );
        AdminCategorySystemDefinition definition = registration.definition().orElseThrow();
        var binding = registration.runtimeBinding().orElseThrow();

        assertThat(definition.systemKey()).isEqualTo("catalogue");
        assertThat(definition.owningModule()).isEqualTo("catalogue");
        assertThat(definition.apiProfile()).isSameAs(
          AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC
        );
        assertThat(definition.identifierProfile()).isSameAs(
          AdminCategoryIdentifierProfile.NUMERIC_LONG_V1
        );
        assertThat(definition.operations().supportedOperations()).containsExactly(
          Operation.values()
        );
        assertThat(definition.operations().requiredPolicyObligations()).containsExactly(
          AdminCategoryPolicyObligation.values()
        );
        assertThat(definition.hierarchy().enabled()).isTrue();
        assertThat(definition.hierarchy().semantics()).contains(
          HierarchySemantics.PARENT_CHILD_TREE
        );
        assertThat(definition.hierarchy().rootModel()).contains(RootModel.IMPLICIT_NULL_PARENT);
        assertThat(definition.hierarchy().supportedOperations()).containsExactly(
          Operation.TREE,
          Operation.PARENT_OPTIONS,
          Operation.BREADCRUMB,
          Operation.MOVE
        );
        assertThat(definition.ordering().orderingEnabled()).isTrue();
        assertThat(definition.ordering().rankingEnabled()).isTrue();
        assertThat(definition.ordering().rankExposed()).isTrue();
        assertThat(definition.ordering().reorderSupported()).isTrue();
        assertThat(definition.ordering().placementModes()).containsExactly(
          PlacementMode.APPEND,
          PlacementMode.BEFORE_ID,
          PlacementMode.AFTER_ID,
          PlacementMode.TARGET_INDEX
        );
        assertThat(definition.ordering().fallbackOrder()).isEmpty();
        assertThat(definition.lifecycle().enabled()).isTrue();
        assertThat(definition.lifecycle().states()).containsExactly(
          LifecycleState.ACTIVE,
          LifecycleState.INACTIVE
        );
        assertThat(definition.lifecycle().actions()).containsExactly(
          Operation.ACTIVATE,
          Operation.DEACTIVATE
        );
        assertThat(definition.fieldsAndQueries().fields()).containsExactly(LogicalField.values());
        assertThat(definition.fieldsAndQueries().searchableFields()).containsExactly(
          LogicalField.NAME,
          LogicalField.SLUG
        );
        assertThat(definition.fieldsAndQueries().allowedFilters()).containsExactly(
          LogicalField.PARENT,
          LogicalField.SLUG,
          LogicalField.ACTIVE
        );
        assertThat(definition.fieldsAndQueries().allowedSorts()).containsExactly(
          LogicalField.IDENTIFIER,
          LogicalField.PARENT,
          LogicalField.NAME,
          LogicalField.SLUG,
          LogicalField.ACTIVE,
          LogicalField.CREATED_AT,
          LogicalField.UPDATED_AT,
          LogicalField.RANK
        );
        assertThat(definition.operationalLimits())
          .usingRecursiveComparison()
          .isEqualTo(
            new AdminCategorySystemDefinition.OperationalLimits(
              java.util.Optional.of(1_000L),
              java.util.Optional.of(120L),
              java.util.Optional.of(4L),
              java.util.Optional.of(5L),
              java.util.Optional.of(10_000L),
              java.util.Optional.of(50L),
              java.util.Optional.of(25L),
              java.util.Optional.of((long) Integer.MAX_VALUE)
            )
          );

        assertThat(binding.identifierCodec()).isSameAs(codec);
        assertThat(binding.ports().populatedCapabilities()).hasSize(6);
        assertAll(
          () -> assertThat(binding.ports().categoryRead().orElseThrow()).isSameAs(provider),
          () -> assertThat(binding.ports().hierarchyRead().orElseThrow()).isSameAs(provider),
          () -> assertThat(binding.ports().basicMutation().orElseThrow()).isSameAs(provider),
          () -> assertThat(binding.ports().hierarchyMutation().orElseThrow()).isSameAs(provider),
          () -> assertThat(binding.ports().ordering().orElseThrow()).isSameAs(provider),
          () -> assertThat(binding.ports().lifecycle().orElseThrow()).isSameAs(provider),
          () ->
            assertThat(
              Arrays.stream(binding.getClass().getDeclaredFields()).map(Field::getName).toList()
            ).containsExactlyInAnyOrder(
              "identifierCodec",
              "permissionMapping",
              "policyBindings",
              "ports"
            ),
          () ->
            assertThat(
              Arrays.stream(binding.getClass().getDeclaredMethods()).map(Method::getName).toList()
            ).containsExactlyInAnyOrder(
              "identifierCodec",
              "permissionMapping",
              "policyBindings",
              "ports"
            )
        );
        assertThat(binding.policyBindings().values()).hasSize(7);
        assertThat(binding.policyBindings().values()).allSatisfy(policy ->
          assertThat(policy.reference().logicalId().moduleKey()).isEqualTo("catalogue")
        );
        for (Operation operation : Operation.values()) {
          String access = operation.ordinal() <= Operation.BREADCRUMB.ordinal() ? "read" : "write";
          assertThat(binding.permissionMapping().permissionFor(operation)).contains(
            "admin:catalogue-categories:" + access
          );
        }
      });
  }
}
