package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class AdminCategoryRegistrationContextTest {

  private final ApplicationContextRunner contextRunner =
    new ApplicationContextRunner().withUserConfiguration(
      AdminCategoryRegistrationConfiguration.class
    );

  @Test
  void zeroContributionsPublishAnImmutableEmptyRegistryAndOneCodec() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(AdminCategoryIdentifierCodec.class);
      assertThat(context).hasSingleBean(AdminCategorySystemRegistry.class);

      AdminCategorySystemRegistry registry = context.getBean(AdminCategorySystemRegistry.class);

      assertThat(registry.registrations()).isEmpty();
      assertThatThrownBy(() -> registry.registrations().clear()).isInstanceOf(
        UnsupportedOperationException.class
      );
    });
  }

  @Test
  void generatedSourcesAreSnapshottedIntoTheSameImmutableRegistry() {
    contextRunner.withUserConfiguration(GeneratedSourceConfiguration.class).run(context -> {
      assertThat(context).hasNotFailed();
      AdminCategorySystemRegistry registry = context.getBean(AdminCategorySystemRegistry.class);

      assertThat(registry.registrations())
        .extracting(registration -> registration.systemKey().orElseThrow())
        .containsExactly("generated-system");
    });
  }

  @Test
  void emptyGeneratedSourcePublishesTheSameEmptyRegistry() {
    contextRunner.withUserConfiguration(EmptyGeneratedSourceConfiguration.class).run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context.getBean(AdminCategorySystemRegistry.class).registrations()).isEmpty();
    });
  }

  @Test
  void nullGeneratedSourceOutputAbortsRefreshWithoutPublishingARegistry() {
    AtomicBoolean registryPublished = new AtomicBoolean();

    withRegistryPublicationProbe(contextRunner, registryPublished)
      .withUserConfiguration(NullGeneratedSourceConfiguration.class)
      .run(context -> {
        assertThat(context).hasFailed();
        AdminCategoryRegistrationValidationException failure = findCause(
          context.getStartupFailure(),
          AdminCategoryRegistrationValidationException.class
        );
        assertThat(failure.diagnostic().component()).contains(
          AdminCategoryValidationComponent.REGISTRATION_SOURCE
        );
        assertThat(registryPublished).isFalse();
      });
  }

  @Test
  void directAndGeneratedDuplicateKeysAbortRefreshWithoutChoosingAWinner() {
    AtomicBoolean registryPublished = new AtomicBoolean();

    withRegistryPublicationProbe(contextRunner, registryPublished)
      .withUserConfiguration(DirectGeneratedCollisionConfiguration.class)
      .run(context -> {
        assertThat(context).hasFailed();
        AdminCategoryRegistrationAggregateValidationException failure = findCause(
          context.getStartupFailure(),
          AdminCategoryRegistrationAggregateValidationException.class
        );
        assertThat(failure.diagnostics())
          .extracting(AdminCategoryValidationDiagnostic::category)
          .containsOnly(AdminCategoryValidationCategory.DUPLICATE_SYSTEM_KEY);
        assertThat(registryPublished).isFalse();
      });
  }

  @Test
  void registryRemainsProxyableForModulithObservability() {
    assertThat(Modifier.isFinal(AdminCategorySystemRegistry.class.getModifiers())).isFalse();
  }

  @Test
  void multipleIndividualBeansUseTheSameCategoryOwnedCodecSingleton() {
    contextRunner.withUserConfiguration(MultipleRegistrationConfiguration.class).run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(AdminCategoryIdentifierCodec.class);
      assertThat(context).hasSingleBean(AdminCategorySystemRegistry.class);
      assertThat(context.getBeansOfType(AdminCategorySystemRegistration.class)).hasSize(2);

      AdminCategoryIdentifierCodec<?> codec = context.getBean(AdminCategoryIdentifierCodec.class);
      AdminCategorySystemRegistration alpha = context.getBean(
        "alphaRegistration",
        AdminCategorySystemRegistration.class
      );
      AdminCategorySystemRegistration taxonomic = context.getBean(
        "taxonomicRegistration",
        AdminCategorySystemRegistration.class
      );
      AdminCategorySystemRegistry registry = context.getBean(AdminCategorySystemRegistry.class);

      assertThat(alpha.runtimeBinding().orElseThrow().identifierCodec()).isSameAs(codec);
      assertThat(taxonomic.runtimeBinding().orElseThrow().identifierCodec()).isSameAs(codec);
      assertThat(registry.findRegistration("alpha-system").orElseThrow()).isSameAs(alpha);
      assertThat(registry.findRegistration("taxonomic-fixture").orElseThrow()).isSameAs(taxonomic);
      assertThat(registry.registrations())
        .extracting(registration -> registration.systemKey().orElseThrow())
        .containsExactly("alpha-system", "taxonomic-fixture");
      assertThatThrownBy(() -> registry.registrations().clear()).isInstanceOf(
        UnsupportedOperationException.class
      );
    });
  }

  @Test
  void namedInterfaceFixtureUsesTheCategoryCodecForMultipleSystemsOwnedByOneModule() {
    contextRunner
      .withUserConfiguration(
        registrationFixtureConfiguration(),
        SameOwnerRegistrationConfiguration.class
      )
      .run(context -> {
        assertThat(context).hasNotFailed();
        assertThat(context).hasSingleBean(AdminCategoryIdentifierCodec.class);
        assertThat(context).hasSingleBean(AdminCategorySystemRegistry.class);
        assertThat(context.getBeansOfType(AdminCategorySystemRegistration.class)).hasSize(2);

        AdminCategoryIdentifierCodec<?> codec = context.getBean(AdminCategoryIdentifierCodec.class);
        AdminCategorySystemRegistration fixture = context.getBean(
          "fixtureRegistration",
          AdminCategorySystemRegistration.class
        );
        AdminCategorySystemRegistration companion = context.getBean(
          "sameOwnerRegistration",
          AdminCategorySystemRegistration.class
        );
        AdminCategorySystemRegistry registry = context.getBean(AdminCategorySystemRegistry.class);

        assertThat(fixture.definition().orElseThrow().owningModule()).isEqualTo("merchandising");
        assertThat(companion.definition().orElseThrow().owningModule()).isEqualTo("merchandising");
        assertThat(fixture.systemKey()).contains("taxonomic-fixture");
        assertThat(companion.systemKey()).contains("botanical-fixture");
        assertThat(fixture.runtimeBinding().orElseThrow().ports()).isNotSameAs(
          companion.runtimeBinding().orElseThrow().ports()
        );
        assertThat(fixture.runtimeBinding().orElseThrow().identifierCodec()).isSameAs(codec);
        assertThat(companion.runtimeBinding().orElseThrow().identifierCodec()).isSameAs(codec);
        assertThat(registry.findRegistration("taxonomic-fixture").orElseThrow()).isSameAs(fixture);
        assertThat(registry.findRegistration("botanical-fixture").orElseThrow()).isSameAs(
          companion
        );
        assertThat(registry.registrations())
          .extracting(registration -> registration.systemKey().orElseThrow())
          .containsExactly("botanical-fixture", "taxonomic-fixture");
      });
  }

  @Test
  void intrinsicContributionFailureAbortsRefreshWithoutChoosingWhichInvalidBeanWins() {
    AtomicBoolean registryPublished = new AtomicBoolean();
    withRegistryPublicationProbe(contextRunner, registryPublished)
      .withUserConfiguration(IntrinsicFailureConfiguration.class)
      .run(context -> {
        assertThat(context).hasFailed();
        AdminCategoryRegistrationValidationException failure = findCause(
          context.getStartupFailure(),
          AdminCategoryRegistrationValidationException.class
        );

        assertThat(failure.diagnostic()).isNotNull();
        assertThat(failure.diagnostic().safeSystemKey()).isNotNull();
        assertThat(registryPublished).isFalse();
      });
  }

  @Test
  void assemblyInvalidContributionAbortsRefreshWithoutPartialRegistryOrFeatureLog() {
    AtomicBoolean registryPublished = new AtomicBoolean();
    try (RegistrationLogCapture logs = RegistrationLogCapture.start()) {
      withRegistryPublicationProbe(contextRunner, registryPublished)
        .withUserConfiguration(AssemblyFailureConfiguration.class)
        .run(context -> {
          assertThat(context).hasFailed();
          AdminCategoryRegistrationAggregateValidationException failure = findCause(
            context.getStartupFailure(),
            AdminCategoryRegistrationAggregateValidationException.class
          );

          assertThat(failure.diagnostics())
            .singleElement()
            .satisfies(diagnostic -> {
              assertThat(diagnostic.category()).isSameAs(
                AdminCategoryValidationCategory.MISSING_COMPONENT
              );
            });
          assertThat(failure.diagnostics())
            .extracting(diagnostic -> diagnostic.component().orElseThrow())
            .containsExactly(AdminCategoryValidationComponent.REGISTRATION);
          assertThat(failure.omittedCount()).isZero();
          assertThat(registryPublished).isFalse();
          assertThat(logs.events()).isEmpty();
        });
    }
  }

  @Test
  void contextFailureRetainsOnlyTheFirstHundredOrderedOccurrencesAndExactOmittedCount() {
    AtomicBoolean registryPublished = new AtomicBoolean();
    ApplicationContextRunner runner = withRegistryPublicationProbe(
      contextRunner,
      registryPublished
    );
    for (int index = 0; index < 101; index++) {
      String beanName = "incompleteRegistration" + index;
      String systemKey = "system-" + String.format("%03d", index);
      runner = runner.withBean(beanName, AdminCategorySystemRegistration.class, () ->
        AdminCategoryRegistrationFixtures.incompleteRegistration(systemKey)
      );
    }

    runner.run(context -> {
      assertThat(context).hasFailed();
      AdminCategoryRegistrationAggregateValidationException failure = findCause(
        context.getStartupFailure(),
        AdminCategoryRegistrationAggregateValidationException.class
      );

      assertThat(failure.diagnostics()).hasSize(100);
      assertThat(failure.omittedCount()).isEqualTo(1L);
      assertThat(failure.diagnostics()).allSatisfy(diagnostic -> {
        assertThat(diagnostic.category()).isSameAs(
          AdminCategoryValidationCategory.MISSING_COMPONENT
        );
        assertThat(diagnostic.component()).contains(AdminCategoryValidationComponent.REGISTRATION);
      });
      assertThat(registryPublished).isFalse();
    });
  }

  private static ApplicationContextRunner withRegistryPublicationProbe(
    ApplicationContextRunner runner,
    AtomicBoolean registryPublished
  ) {
    return runner.withBean("registryPublicationProbe", BeanPostProcessor.class, () ->
      new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (bean instanceof AdminCategorySystemRegistry) {
            registryPublished.set(true);
          }
          return bean;
        }
      }
    );
  }

  private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
    Throwable candidate = failure;
    while (candidate != null) {
      if (type.isInstance(candidate)) {
        return type.cast(candidate);
      }
      candidate = candidate.getCause();
    }
    throw new AssertionError("Expected cause " + type.getName(), failure);
  }

  private static Class<?> registrationFixtureConfiguration() {
    try {
      return Class.forName(
        "com.ferozfaiz.uniapi.registrationfixture.RegistrationFixtureConfiguration"
      );
    } catch (ClassNotFoundException exception) {
      throw new AssertionError("Registration named-interface fixture is missing", exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MultipleRegistrationConfiguration {

    @Bean
    AdminCategorySystemRegistration alphaRegistration(
      AdminCategoryIdentifierCodec<Long> identifierCodec
    ) {
      return registration("alpha-system", identifierCodec);
    }

    @Bean
    AdminCategorySystemRegistration taxonomicRegistration(
      AdminCategoryIdentifierCodec<Long> identifierCodec
    ) {
      return registration("taxonomic-fixture", identifierCodec);
    }

    private static AdminCategorySystemRegistration registration(
      String systemKey,
      AdminCategoryIdentifierCodec<Long> identifierCodec
    ) {
      return AdminCategoryRegistrationContextTest.registration(systemKey, identifierCodec);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class SameOwnerRegistrationConfiguration {

    @Bean
    AdminCategorySystemRegistration sameOwnerRegistration(
      AdminCategoryIdentifierCodec<Long> identifierCodec
    ) {
      return registration("botanical-fixture", identifierCodec);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class GeneratedSourceConfiguration {

    @Bean
    AdminCategorySystemRegistrationSource generatedSource() {
      return () ->
        List.of(
          AdminCategoryRegistrationFixtures.completeRegistration(
            "generated-system",
            "merchandising"
          )
        );
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class EmptyGeneratedSourceConfiguration {

    @Bean
    AdminCategorySystemRegistrationSource emptyGeneratedSource() {
      return List::of;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class NullGeneratedSourceConfiguration {

    @Bean
    AdminCategorySystemRegistrationSource nullGeneratedSource() {
      return () -> null;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class DirectGeneratedCollisionConfiguration {

    @Bean
    AdminCategorySystemRegistration directCollisionRegistration() {
      return AdminCategoryRegistrationFixtures.completeRegistration(
        "collision-system",
        "merchandising"
      );
    }

    @Bean
    AdminCategorySystemRegistrationSource collisionGeneratedSource() {
      return () ->
        List.of(
          AdminCategoryRegistrationFixtures.completeRegistration(
            "collision-system",
            "merchandising"
          )
        );
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class IntrinsicFailureConfiguration {

    @Bean
    AdminCategorySystemRegistration invalidPolicyRegistration(
      AdminCategoryIdentifierCodec<Long> identifierCodec
    ) {
      AdminCategoryLogicalPolicyId.parse(" Invalid:query");
      return registration("invalid-policy-system", identifierCodec);
    }

    @Bean
    AdminCategorySystemRegistration invalidDefinitionRegistration(
      AdminCategoryIdentifierCodec<Long> identifierCodec
    ) {
      AdminCategoryRegistrationFixtures.completeDefinition(
        "Invalid-definition-system",
        "merchandising"
      );
      return registration("invalid-definition-system", identifierCodec);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AssemblyFailureConfiguration {

    @Bean
    AdminCategorySystemRegistration incompleteRegistration() {
      return AdminCategoryRegistrationFixtures.incompleteRegistration("taxonomic-fixture");
    }
  }

  private static AdminCategorySystemRegistration registration(
    String systemKey,
    AdminCategoryIdentifierCodec<Long> identifierCodec
  ) {
    AdminCategorySystemDefinition definition = AdminCategoryRegistrationFixtures.completeDefinition(
      systemKey,
      "merchandising"
    );
    AdminCategoryRuntimeBinding template = AdminCategoryRegistrationFixtures.completeRegistration(
      systemKey,
      "merchandising"
    )
      .runtimeBinding()
      .orElseThrow();
    return AdminCategorySystemRegistration.of(
      definition,
      runtimeBinding(
        identifierCodec,
        template.permissionMapping(),
        template.policyBindings(),
        template.ports()
      )
    );
  }

  private static AdminCategoryRuntimeBinding runtimeBinding(
    AdminCategoryIdentifierCodec<?> codec,
    AdminCategoryPermissionMapping permissions,
    AdminCategoryRuntimePolicyBindings policies,
    AdminCategoryRuntimePorts ports
  ) {
    try {
      for (var constructor : AdminCategoryRuntimeBinding.class.getConstructors()) {
        if (constructor.getParameterCount() == 4) {
          return (AdminCategoryRuntimeBinding) constructor.newInstance(
            codec,
            permissions,
            policies,
            ports
          );
        }
        if (constructor.getParameterCount() == 5) {
          return (AdminCategoryRuntimeBinding) constructor.newInstance(
            codec,
            permissions,
            policies,
            ports,
            Optional.empty()
          );
        }
      }
      throw new AssertionError("Expected the runtime-binding constructor");
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("Could not construct a runtime binding", exception);
    }
  }

  private static final class RegistrationLogCapture implements AutoCloseable {

    private static final String LOGGER_NAME =
      "com.ferozfaiz.uniapi.category.application.registration";

    private final Logger logger;
    private final Level originalLevel;
    private final ListAppender<ILoggingEvent> appender;

    private RegistrationLogCapture(
      Logger logger,
      Level originalLevel,
      ListAppender<ILoggingEvent> appender
    ) {
      this.logger = logger;
      this.originalLevel = originalLevel;
      this.appender = appender;
    }

    static RegistrationLogCapture start() {
      Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
      Level originalLevel = logger.getLevel();
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.setLevel(Level.TRACE);
      logger.addAppender(appender);
      return new RegistrationLogCapture(logger, originalLevel, appender);
    }

    List<ILoggingEvent> events() {
      return List.copyOf(appender.list);
    }

    @Override
    public void close() {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }
  }
}
