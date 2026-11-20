package com.ferozfaiz.uniapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@AutoConfigureObservability
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
  properties = { "spring.jpa.hibernate.ddl-auto=validate", "management.tracing.enabled=true" }
)
class UniapiApplicationTests {

  private final AdminCategorySystemRegistry registry;

  @Autowired
  UniapiApplicationTests(AdminCategorySystemRegistry registry) {
    this.registry = registry;
  }

  @Test
  void contextLoadsWithModuleObservability() {
    assertThat(AopUtils.isAopProxy(registry)).isTrue();
    assertThat(registry.registrations())
      .extracting(registration -> registration.systemKey().orElseThrow())
      .containsExactly("catalogue");
    assertThatThrownBy(() -> registry.registrations().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
  }
}
