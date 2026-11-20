package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistrationSource;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemProperties;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableOperations;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankOperations;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class DescriptorBackedAdminCategoryOnboardingConformanceTest {

  @Test
  void documentedDescriptorOnboardingProducesOneCompleteDirectPortRegistration() {
    String systemKey = "onboarding-descriptor";
    AdminCategoryJdbcProviderProperties providerProperties =
      new AdminCategoryJdbcProviderProperties();
    providerProperties.setEnabledSystems(List.of(systemKey));

    DescriptorDrivenAdminCategoryJdbcProviderFactory source = factory(
      providerProperties,
      registry(systemKey)
    );

    assertThat(source).isInstanceOf(AdminCategorySystemRegistrationSource.class);
    assertThat(source.registrations())
      .singleElement()
      .satisfies(registration -> {
        assertThat(registration.systemKey()).contains(systemKey);
        assertThat(registration.definition()).isPresent();
        var binding = registration.runtimeBinding().orElseThrow();
        assertThat(binding.getClass().getDeclaredFields())
          .extracting(java.lang.reflect.Field::getName)
          .containsExactlyInAnyOrder(
            "identifierCodec",
            "permissionMapping",
            "policyBindings",
            "ports"
          );
        var ports = binding.ports();
        Object adapter = ports.categoryRead().orElseThrow();
        assertThat(adapter).isExactlyInstanceOf(DescriptorDrivenAdminCategoryJdbcProvider.class);
        assertThat(
          List.of(
            ports.hierarchyRead().orElseThrow(),
            ports.basicMutation().orElseThrow(),
            ports.hierarchyMutation().orElseThrow(),
            ports.ordering().orElseThrow(),
            ports.lifecycle().orElseThrow()
          )
        ).allSatisfy(port -> assertThat(port).isSameAs(adapter));
      });
  }

  private static DescriptorDrivenAdminCategoryJdbcProviderFactory factory(
    AdminCategoryJdbcProviderProperties properties,
    CategorySystemRegistry registry
  ) {
    @SuppressWarnings("unchecked")
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    when(codec.identifierProfile()).thenReturn(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);
    AdminCategoryDefinitionValidator policies = new AdminCategoryDefinitionValidator();
    return new DescriptorDrivenAdminCategoryJdbcProviderFactory(
      properties,
      registry,
      mock(NamedParameterJdbcTemplate.class),
      mock(ClosureTableOperations.class),
      mock(SparseRankOperations.class),
      mock(PlatformTransactionManager.class),
      codec,
      policies,
      policies,
      policies,
      policies,
      policies,
      policies,
      policies
    );
  }

  private static CategorySystemRegistry registry(String systemKey) {
    CategorySystemProperties.SystemProperties system =
      new CategorySystemProperties.SystemProperties();
    system.setOwningModule("onboarding-domain");
    system.setNodeTable("onboarding_category");
    system.setClosureTable("onboarding_category_closure");
    system.getRanking().setEnabled(true);
    system.setFallbackOrder(
      List.of(
        fallback("name", "name", FallbackOrderDescriptor.Direction.ASC),
        fallback("id", "id", FallbackOrderDescriptor.Direction.ASC)
      )
    );
    CategorySystemProperties properties = new CategorySystemProperties();
    properties.setCategorySystems(Map.of(systemKey, system));
    return new CategorySystemRegistry(properties);
  }

  private static CategorySystemProperties.FallbackOrderProperties fallback(
    String property,
    String column,
    FallbackOrderDescriptor.Direction direction
  ) {
    CategorySystemProperties.FallbackOrderProperties fallback =
      new CategorySystemProperties.FallbackOrderProperties();
    fallback.setProperty(property);
    fallback.setColumnName(column);
    fallback.setDirection(direction);
    return fallback;
  }
}
