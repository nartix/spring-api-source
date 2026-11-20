package com.ferozfaiz.uniapi.registrationfixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.UniapiApplication;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOperationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistrationSource;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.NamedInterface;
import org.springframework.modulith.core.ApplicationModules;

class RegistrationNamedInterfaceCompileTest {

  @Test
  void registrationFixtureCompilesAgainstOnlyTheExactRegistrationBoundary() {
    Set<Class<?>> compiledContract = Set.of(
      AdminCategoryOperationPort.class,
      AdminCategoryReadPort.class,
      AdminCategoryHierarchyReadPort.class,
      AdminCategoryBasicMutationPort.class,
      AdminCategoryHierarchyMutationPort.class,
      AdminCategoryOrderingPort.class,
      AdminCategoryLifecyclePort.class,
      AdminCategorySystemDefinition.class,
      AdminCategorySystemRegistration.class,
      AdminCategoryRuntimeBinding.class,
      AdminCategoryRuntimePorts.class,
      AdminCategoryRuntimePolicyBindings.class,
      AdminCategorySystemRegistrationSource.class
    );

    assertThat(compiledContract).hasSize(13);
    assertThat(
      compiledContract
        .stream()
        .filter(type -> type.getPackageName().endsWith(".provider"))
        .map(type -> type.getAnnotation(NamedInterface.class))
    )
      .doesNotContainNull()
      .allSatisfy(annotation -> assertThat(annotation.value()).containsExactly("registration"));

    ApplicationModule fixtureModule = RegistrationNamedInterfaceCompileTest.class
      .getPackage()
      .getAnnotation(ApplicationModule.class);
    assertThat(fixtureModule).isNotNull();
    assertThat(fixtureModule.allowedDependencies()).containsExactly("category :: registration");

    var category = ApplicationModules.of(UniapiApplication.class)
      .getModuleByName("category")
      .orElseThrow();
    assertThat(category.getNamedInterfaces().getByName("registration")).isPresent();
    assertThat(category.getNamedInterfaces().getByName("application")).isEmpty();
    assertThat(category.getNamedInterfaces().getByName("provider")).isEmpty();
  }
}
