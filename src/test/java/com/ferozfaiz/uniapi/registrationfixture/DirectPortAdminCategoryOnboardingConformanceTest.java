package com.ferozfaiz.uniapi.registrationfixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirectPortAdminCategoryOnboardingConformanceTest {

  @Test
  void documentedDomainOwnedOnboardingUsesOneNeutralDirectPortAdapter() {
    @SuppressWarnings("unchecked")
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    when(codec.identifierProfile()).thenReturn(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);

    var registration = new RegistrationFixtureConfiguration().fixtureRegistration(codec);

    assertThat(registration.definition()).isPresent();
    assertThat(registration.systemKey()).contains("taxonomic-fixture");
    assertThat(registration.getClass().getDeclaredFields())
      .extracting(java.lang.reflect.Field::getName)
      .containsExactlyInAnyOrder("definition", "runtimeBinding");
    var binding = registration.runtimeBinding().orElseThrow();
    assertThat(binding.getClass().getDeclaredFields())
      .extracting(java.lang.reflect.Field::getName)
      .containsExactlyInAnyOrder("identifierCodec", "permissionMapping", "policyBindings", "ports");
    var ports = binding.ports();
    Object adapter = ports.categoryRead().orElseThrow();
    assertThat(adapter.getClass().getPackageName()).isEqualTo(
      DirectPortAdminCategoryOnboardingConformanceTest.class.getPackageName()
    );
    assertThat(
      List.of(
        ports.hierarchyRead().orElseThrow(),
        ports.basicMutation().orElseThrow(),
        ports.hierarchyMutation().orElseThrow(),
        ports.ordering().orElseThrow(),
        ports.lifecycle().orElseThrow()
      )
    ).allSatisfy(port -> assertThat(port).isSameAs(adapter));
  }
}
