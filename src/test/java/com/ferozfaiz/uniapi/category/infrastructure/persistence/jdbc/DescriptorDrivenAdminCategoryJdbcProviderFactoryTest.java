package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistrationSource;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationCategory;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationComponent;
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

class DescriptorDrivenAdminCategoryJdbcProviderFactoryTest {

  @Test
  void defaultsToDisabledProviderSource() {
    DescriptorDrivenAdminCategoryJdbcProviderFactory factory = factory(
      new AdminCategoryJdbcProviderProperties(),
      registry("descriptor-a")
    );

    assertThat(factory.registrations()).isEmpty();
  }

  @Test
  void createsCompleteRegistrationsOnlyForExplicitlyEnabledSystems() {
    AdminCategoryJdbcProviderProperties properties = new AdminCategoryJdbcProviderProperties();
    properties.setEnabledSystems(List.of("descriptor-a"));

    DescriptorDrivenAdminCategoryJdbcProviderFactory factory = factory(
      properties,
      registry("descriptor-a", "descriptor-b")
    );

    assertThat(factory.registrations())
      .extracting(registration -> registration.systemKey().orElseThrow())
      .containsExactly("descriptor-a");
    var registration = factory.registrations().getFirst();
    assertThat(registration.runtimeBinding()).isPresent();
    assertThat(registration.runtimeBinding().orElseThrow().ports().populatedCapabilities()).hasSize(
      6
    );
    assertThat(registration.runtimeBinding().orElseThrow().policyBindings().values())
      .hasSize(7)
      .allSatisfy(binding ->
        assertThat(binding.reference().logicalId().moduleKey()).isEqualTo("category-jdbc")
      );
    var ports = registration.runtimeBinding().orElseThrow().ports();
    Object provider = ports.categoryRead().orElseThrow();
    assertThat(provider).isInstanceOf(DescriptorDrivenAdminCategoryJdbcProvider.class);
    assertThat(
      List.of(
        ports.hierarchyRead().orElseThrow(),
        ports.basicMutation().orElseThrow(),
        ports.hierarchyMutation().orElseThrow(),
        ports.ordering().orElseThrow(),
        ports.lifecycle().orElseThrow()
      )
    ).allSatisfy(port -> assertThat(port).isSameAs(provider));
  }

  @Test
  void retainsGeneratedRegistrationSourceContract() {
    DescriptorDrivenAdminCategoryJdbcProviderFactory factory = factory(
      new AdminCategoryJdbcProviderProperties(),
      registry("descriptor-a")
    );

    assertThat(factory).isInstanceOf(AdminCategorySystemRegistrationSource.class);
    assertThat(((AdminCategorySystemRegistrationSource) factory).registrations()).isSameAs(
      factory.registrations()
    );
  }

  @Test
  void createsOneImmutableRegistrationPerEnabledSystemInConfiguredOrder() {
    AdminCategoryJdbcProviderProperties properties = new AdminCategoryJdbcProviderProperties();
    properties.setEnabledSystems(List.of("descriptor-b", "descriptor-a"));

    DescriptorDrivenAdminCategoryJdbcProviderFactory factory = factory(
      properties,
      registry("descriptor-a", "descriptor-b")
    );

    assertThat(factory.registrations())
      .extracting(registration -> registration.systemKey().orElseThrow())
      .containsExactly("descriptor-b", "descriptor-a");
    assertThatThrownBy(() -> factory.registrations().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
  }

  @Test
  void rejectsDuplicateEnabledKeysAndMissingDescriptors() {
    AdminCategoryJdbcProviderProperties duplicate = new AdminCategoryJdbcProviderProperties();
    duplicate.setEnabledSystems(List.of("descriptor-a", " descriptor-a "));
    assertThatThrownBy(() -> factory(duplicate, registry("descriptor-a")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Duplicate descriptor-backed category system");

    AdminCategoryJdbcProviderProperties missing = new AdminCategoryJdbcProviderProperties();
    missing.setEnabledSystems(List.of("descriptor-missing"));
    assertThatThrownBy(() -> factory(missing, registry("descriptor-a"))).isInstanceOfSatisfying(
      AdminCategoryRegistrationValidationException.class,
      failure -> {
        assertThat(failure.diagnostic().safeSystemKey()).isEqualTo("descriptor-missing");
        assertThat(failure.diagnostic().category()).isEqualTo(
          AdminCategoryValidationCategory.MISSING_COMPONENT
        );
        assertThat(failure.diagnostic().component()).contains(
          AdminCategoryValidationComponent.REGISTRATION_SOURCE
        );
        assertThat(failure.getCause()).isNull();
      }
    );
  }

  @Test
  void emitsCatalogueAsAnOrdinaryGeneratedCandidateForGenericCollisionValidation() {
    AdminCategoryJdbcProviderProperties catalogue = new AdminCategoryJdbcProviderProperties();
    catalogue.setEnabledSystems(List.of("catalogue"));
    assertThat(factory(catalogue, registry("catalogue")).registrations())
      .extracting(registration -> registration.systemKey().orElseThrow())
      .containsExactly("catalogue");
  }

  @Test
  void rejectsInvalidDescriptorConfiguration() {
    AdminCategoryJdbcProviderProperties invalid = new AdminCategoryJdbcProviderProperties();
    invalid.setEnabledSystems(List.of("descriptor-a"));
    AdminCategoryJdbcProviderProperties.SystemProperties invalidSystem =
      new AdminCategoryJdbcProviderProperties.SystemProperties();
    invalidSystem.setSlugUniqueness(
      AdminCategoryJdbcProviderProperties.SlugUniqueness.SYSTEM_SCOPED
    );
    invalid.setSystems(Map.of("descriptor-a", invalidSystem));

    assertThatThrownBy(() -> factory(invalid, registry("descriptor-a"))).isInstanceOfSatisfying(
      AdminCategoryRegistrationValidationException.class,
      failure -> {
        assertThat(failure.diagnostic().safeSystemKey()).isEqualTo("descriptor-a");
        assertThat(failure.diagnostic().category()).isEqualTo(
          AdminCategoryValidationCategory.INVALID_COLLECTION
        );
        assertThat(failure.diagnostic().component()).contains(
          AdminCategoryValidationComponent.REGISTRATION_SOURCE
        );
        assertThat(failure.getCause()).isNull();
      }
    );
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

  private static CategorySystemRegistry registry(String... systemKeys) {
    CategorySystemProperties properties = new CategorySystemProperties();
    Map<String, CategorySystemProperties.SystemProperties> systems =
      new java.util.LinkedHashMap<>();
    for (String systemKey : systemKeys) {
      systems.put(systemKey, system(systemKey));
    }
    properties.setCategorySystems(systems);
    return new CategorySystemRegistry(properties);
  }

  private static CategorySystemProperties.SystemProperties system(String systemKey) {
    CategorySystemProperties.SystemProperties system =
      new CategorySystemProperties.SystemProperties();
    system.setOwningModule("category");
    system.setNodeTable(systemKey.replace("-", "_") + "_category");
    system.setClosureTable(systemKey.replace("-", "_") + "_category_closure");
    system.getRanking().setEnabled(true);
    system.setFallbackOrder(
      List.of(
        fallback("name", "name", FallbackOrderDescriptor.Direction.ASC),
        fallback("id", "id", FallbackOrderDescriptor.Direction.ASC)
      )
    );
    return system;
  }

  private static CategorySystemProperties.FallbackOrderProperties fallback(
    String property,
    String columnName,
    FallbackOrderDescriptor.Direction direction
  ) {
    CategorySystemProperties.FallbackOrderProperties fallback =
      new CategorySystemProperties.FallbackOrderProperties();
    fallback.setProperty(property);
    fallback.setColumnName(columnName);
    fallback.setDirection(direction);
    return fallback;
  }
}
