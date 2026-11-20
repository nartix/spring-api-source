package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.category.api.mapper.AdminCategoryApiMapper;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.GeneralValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdminCategoryCapabilitiesOnlyRegistrationTest {

  private static final String SYSTEM_KEY = "capabilities-only";
  private static final String READ_PERMISSION = "admin:" + SYSTEM_KEY + "-categories:read";
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void validatesPublishesAndProjectsCapabilitiesWithNoRuntimePort() throws Exception {
    AdminCategorySystemDefinition definition = capabilitiesOnlyDefinition();
    AdminCategoryIdentifierCodec<Long> codec = new StandardNumericV1AdminCategoryIdentifierCodec();
    AdminCategoryPermissionMapping permissions = new AdminCategoryPermissionMapping(
      List.of(new Binding(Operation.CAPABILITIES, READ_PERMISSION))
    );
    AtomicInteger policyCalls = new AtomicInteger();
    AdminCategoryPolicyReference generalValidationReference = new AdminCategoryPolicyReference(
      AdminCategoryPolicyObligation.GENERAL_VALIDATION,
      new AdminCategoryLogicalPolicyId("contract-tests", "general-validation")
    );
    AdminCategoryRuntimePolicyBindings policies = new AdminCategoryRuntimePolicyBindings(
      List.of(
        new GeneralValidationPolicyBinding(generalValidationReference, input ->
          policyCalls.incrementAndGet()
        )
      )
    );
    AdminCategoryRuntimePorts ports = AdminCategoryRuntimePorts.empty();
    AdminCategoryRuntimeBinding binding = new AdminCategoryRuntimeBinding(
      codec,
      permissions,
      policies,
      ports
    );
    AdminCategorySystemRegistration registration = AdminCategorySystemRegistration.of(
      definition,
      binding
    );

    assertThatCode(() ->
      AdminCategoryRegistrationValidator.validate(List.of(registration))
    ).doesNotThrowAnyException();

    AdminCategorySystemRegistry registry = new AdminCategorySystemRegistry(List.of(registration));
    AdminCategoryApplicationService service = new AdminCategoryApplicationService(registry);

    assertThat(registry.registrations()).containsExactly(registration);
    assertThat(registry.findRegistration(SYSTEM_KEY)).containsSame(registration);
    assertThat(registration.runtimeBinding()).containsSame(binding);
    assertThat(binding.identifierCodec()).isSameAs(codec);
    assertThat(codec.identifierProfile()).isSameAs(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);
    assertThat(binding.permissionMapping().bindings()).containsExactly(
      new Binding(Operation.CAPABILITIES, READ_PERMISSION)
    );
    assertThat(binding.policyBindings().values())
      .singleElement()
      .extracting(policy -> policy.reference())
      .isEqualTo(generalValidationReference);
    assertThat(definition.operations().requiredPolicyObligations()).containsExactly(
      AdminCategoryPolicyObligation.GENERAL_VALIDATION
    );
    assertAllPortSlotsEmpty(ports);

    var first = AdminCategoryApiMapper.toCapabilityResponse(service.capabilities(SYSTEM_KEY));
    var second = AdminCategoryApiMapper.toCapabilityResponse(service.capabilities(SYSTEM_KEY));

    assertThat(first).isEqualTo(second);
    assertThat(JSON.writeValueAsBytes(first)).containsExactly(JSON.writeValueAsBytes(second));
    assertThat(first.supportedOperations())
      .containsExactly(Operation.CAPABILITIES.code())
      .doesNotHaveDuplicates();
    assertThat(first.identifierProfile()).isEqualTo("NUMERIC_LONG_V1");
    assertThat(first.rankingEnabled()).isFalse();
    assertThat(first.placementModes()).isEmpty();
    assertThat(first.sortRankExposed()).isFalse();
    assertThat(first.fallbackOrder()).isEmpty();
    assertThat(first.fields()).containsExactly(
      "identifier",
      "name",
      "slug",
      "active",
      "created-at",
      "updated-at"
    );
    assertThat(first.searchableFields()).isEmpty();
    assertThat(first.allowedFilters()).isEmpty();
    assertThat(first.allowedSorts()).isEmpty();
    assertThat(first.hierarchy().enabled()).isFalse();
    assertThat(first.hierarchy().readOperations()).isEmpty();
    assertThat(first.hierarchy().mutationOperations()).isEmpty();
    assertThat(first.hierarchy().rootModel()).isNull();
    assertThat(first.limits()).hasAllNullFieldsOrProperties();
    assertThat(first.lifecycle().enabled()).isFalse();
    assertThat(first.lifecycle().states()).isEmpty();
    assertThat(first.lifecycle().actions()).isEmpty();
    assertThat(first.ordering().orderingEnabled()).isFalse();
    assertThat(first.ordering().rankingEnabled()).isFalse();
    assertThat(first.ordering().rankExposed()).isFalse();
    assertThat(first.ordering().reorderSupported()).isFalse();
    assertThat(first.ordering().placementModes()).isEmpty();
    assertThat(first.ordering().fallbackOrder()).isEmpty();
    assertThat(policyCalls).hasValue(0);
    assertAllPortSlotsEmpty(ports);
  }

  private static AdminCategorySystemDefinition capabilitiesOnlyDefinition() {
    return new AdminCategorySystemDefinition(
      SYSTEM_KEY,
      "contract-tests",
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC,
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
      new OperationDeclaration(
        List.of(Operation.CAPABILITIES),
        List.of(AdminCategoryPolicyObligation.GENERAL_VALIDATION)
      ),
      new HierarchyDeclaration(false, Optional.empty(), Optional.empty(), List.of()),
      new OrderingDeclaration(false, false, false, false, List.of(), List.of()),
      new LifecycleDeclaration(false, List.of(), List.of()),
      new FieldQueryDeclaration(
        List.of(
          LogicalField.IDENTIFIER,
          LogicalField.NAME,
          LogicalField.SLUG,
          LogicalField.ACTIVE,
          LogicalField.CREATED_AT,
          LogicalField.UPDATED_AT
        ),
        List.of(),
        List.of(),
        List.of()
      ),
      new OperationalLimits(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
      )
    );
  }

  private static void assertAllPortSlotsEmpty(AdminCategoryRuntimePorts ports) {
    assertThat(ports.populatedCapabilities()).isEmpty();
    assertThat(ports.portFor(Operation.CAPABILITIES)).isEmpty();
    assertThat(ports.categoryRead()).isEmpty();
    assertThat(ports.hierarchyRead()).isEmpty();
    assertThat(ports.basicMutation()).isEmpty();
    assertThat(ports.hierarchyMutation()).isEmpty();
    assertThat(ports.ordering()).isEmpty();
    assertThat(ports.lifecycle()).isEmpty();
  }
}
