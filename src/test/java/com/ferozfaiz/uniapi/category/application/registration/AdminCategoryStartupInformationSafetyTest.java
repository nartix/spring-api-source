package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.ferozfaiz.uniapi.category.contract.InformationSafetyEvidenceLedger;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyEvidenceLedger.ActualEvidence;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyEvidenceLedger.ActualValue;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyEvidenceLedger.Surface;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyProbeCorpus;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyProbeCorpus.Probe;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
public class AdminCategoryStartupInformationSafetyTest {

  @AfterAll
  static void assertObservedInformationSafetyLedgerWhenAllProvingTestsFinish() {
    InformationSafetyEvidenceLedger.finishAndAssertObserved(
      InformationSafetyEvidenceLedger.ProvingTest.STARTUP
    );
  }

  @Test
  void retainsExactlyOneHundredOfOneHundredThirtySevenSafeDiagnostics() {
    BoundedRegistrationDiagnosticAccumulator accumulator =
      new BoundedRegistrationDiagnosticAccumulator();
    List<AdminCategoryValidationDiagnostic> ordered = new ArrayList<>();
    for (int index = 0; index < 137; index++) {
      ordered.add(
        new AdminCategoryValidationDiagnostic(
          "safe-system-" + String.format("%03d", index),
          AdminCategoryValidationCategory.MISSING_COMPONENT,
          Optional.of(AdminCategoryValidationComponent.RUNTIME_PORTS),
          Optional.empty(),
          Optional.empty()
        )
      );
    }
    List<AdminCategoryValidationDiagnostic> reverse = new ArrayList<>(ordered);
    Collections.reverse(reverse);
    reverse.forEach(accumulator::add);

    List<AdminCategoryValidationDiagnostic> retained = accumulator.retainedInOrder();
    AdminCategoryRegistrationAggregateValidationException failure =
      new AdminCategoryRegistrationAggregateValidationException(
        retained,
        accumulator.omittedCount()
      );

    assertThat(retained).hasSize(100).containsExactlyElementsOf(ordered.subList(0, 100));
    assertThat(retained).isUnmodifiable();
    assertThat(accumulator.omittedCount()).isEqualTo(37L);
    assertThat(failure.diagnostics()).containsExactlyElementsOf(retained).isUnmodifiable();
    assertThat(failure.omittedCount()).isEqualTo(37L);
    assertThat(failure.getMessage()).isEqualTo(expectedBoundedDiagnosticRendering());
    assertThat(
      instanceFieldNames(AdminCategoryValidationDiagnostic.class)
    ).containsExactlyInAnyOrder(
      "safeSystemKey",
      "category",
      "component",
      "capability",
      "policyObligation"
    );
    assertThat(
      instanceFieldNames(AdminCategoryRegistrationAggregateValidationException.class)
    ).containsExactlyInAnyOrder("diagnostics", "omittedCount");
    assertThat(failure.diagnostics()).allSatisfy(diagnostic -> {
      assertThat(diagnostic.safeSystemKey()).matches("safe-system-0\\d{2}");
      assertThat(diagnostic.category()).isEqualTo(
        AdminCategoryValidationCategory.MISSING_COMPONENT
      );
      assertThat(diagnostic.component()).contains(AdminCategoryValidationComponent.RUNTIME_PORTS);
      assertThat(diagnostic.capability()).isEmpty();
      assertThat(diagnostic.policyObligation()).isEmpty();
      assertThat(diagnostic.policyReference()).isEmpty();
    });
    assertThat(failure.getCause()).isNull();
    assertThat(failure.getSuppressed()).isEmpty();
    InformationSafetyProbeCorpus.assertNoLeaks(startupSurfaces(failure));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("probes")
  void realApplicationContextStartupFailureSanitizesLogConsoleAndCauseChain(
    Probe probe,
    CapturedOutput output
  ) {
    AtomicBoolean firstRegistryPublished = new AtomicBoolean();
    StructuredLogCapture.CapturedEvent startupLog;
    Throwable firstStartupFailure;
    try (
      StructuredLogCapture capture = StructuredLogCapture.attach(
        AnnotationConfigApplicationContext.class
      )
    ) {
      StartupAttempt firstAttempt = runFailingContext(probe, firstRegistryPublished);
      assertSelectedProbeSourceExecuted(probe, firstAttempt);
      firstStartupFailure = firstAttempt.startupFailure();
      List<StructuredLogCapture.CapturedEvent> capturedStartupEvents = capture.events();
      List<StructuredLogCapture.CapturedEvent> warnings = capturedStartupEvents
        .stream()
        .filter(event -> event.level() == ch.qos.logback.classic.Level.WARN)
        .toList();
      assertThat(warnings).hasSize(1);
      startupLog = warnings.getFirst();
      assertThat(capturedStartupEvents).allSatisfy(event -> {
        assertThat(event.loggerName()).isEqualTo(
          AnnotationConfigApplicationContext.class.getName()
        );
        assertThat(event.structuredFields()).isEmpty();
        assertThat(event.mdc()).isEmpty();
        assertThat(event.markers()).isEmpty();
        assertThat(event.attachedThrowable()).isEmpty();
        InformationSafetyProbeCorpus.assertNoLeaks(event.searchableSurfaces());
      });
    }

    assertThat(firstRegistryPublished).isFalse();
    AdminCategoryRegistrationValidationException typedFailure = findCause(
      firstStartupFailure,
      AdminCategoryRegistrationValidationException.class
    );
    assertThat(typedFailure.getMessage()).isEqualTo(
      "Admin category registration validation failed: systemKey=<unavailable>,category=invalid-collection,component=registration-source"
    );
    assertThat(typedFailure.diagnostic().safeSystemKey()).isEqualTo("<unavailable>");
    assertThat(typedFailure.diagnostic().category()).isEqualTo(
      AdminCategoryValidationCategory.INVALID_COLLECTION
    );
    assertThat(typedFailure.diagnostic().component()).contains(
      AdminCategoryValidationComponent.REGISTRATION_SOURCE
    );
    assertThat(typedFailure.diagnostic().capability()).isEmpty();
    assertThat(typedFailure.diagnostic().policyObligation()).isEmpty();
    assertThat(typedFailure.diagnostic().policyReference()).isEmpty();
    assertThat(typedFailure.getCause()).isNull();
    assertThat(typedFailure.getSuppressed()).isEmpty();
    InformationSafetyProbeCorpus.assertNoLeaks(throwableSurfaces(firstStartupFailure));

    assertThat(startupLog.level()).isEqualTo(ch.qos.logback.classic.Level.WARN);
    assertThat(startupLog.loggerName()).isEqualTo(
      AnnotationConfigApplicationContext.class.getName()
    );
    String expectedStartupWarning =
      "Exception encountered during context initialization - cancelling refresh attempt: " +
      "org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'adminCategorySystemRegistry' " +
      "defined in com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationConfiguration: " +
      "Failed to instantiate [com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry]: " +
      "Factory method 'adminCategorySystemRegistry' threw exception with message: " +
      typedFailure.getMessage();
    assertThat(startupLog.message()).isEqualTo(expectedStartupWarning);
    assertThat(startupLog.formattedMessage()).isEqualTo(expectedStartupWarning);
    assertThat(startupLog.arguments()).isEmpty();
    assertThat(startupLog.structuredFields()).isEmpty();
    assertThat(startupLog.mdc()).isEmpty();
    assertThat(startupLog.markers()).isEmpty();
    assertThat(startupLog.attachedThrowable()).isEmpty();
    assertThat(startupLog.transactionActive()).isFalse();
    InformationSafetyProbeCorpus.assertNoLeaks(startupLog.searchableSurfaces());

    int outputOffset = output.getAll().length();
    AtomicBoolean secondRegistryPublished = new AtomicBoolean();
    StartupAttempt secondAttempt = runFailingContext(probe, secondRegistryPublished);
    assertSelectedProbeSourceExecuted(probe, secondAttempt);
    Throwable secondStartupFailure = secondAttempt.startupFailure();
    String startupConsole = output.getAll().substring(outputOffset);

    assertThat(secondRegistryPublished).isFalse();
    assertThat(findCause(secondStartupFailure, AdminCategoryRegistrationValidationException.class))
      .extracting(Throwable::getMessage)
      .isEqualTo(typedFailure.getMessage());
    assertThat(startupConsole).contains(typedFailure.getMessage());
    InformationSafetyProbeCorpus.assertNoLeaks(startupConsole);
    InformationSafetyEvidenceLedger.assertCovered(
      probe,
      startupEvidence(
        typedFailure,
        firstStartupFailure,
        startupLog,
        startupConsole,
        firstRegistryPublished.get() || secondRegistryPublished.get()
      )
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("probes")
  void rawProviderAndCodecCausesBecomeCauseFreeTypedDiagnostics(Probe probe) {
    RuntimeException rawFailure = new IllegalStateException(
      probe.literal(),
      new IllegalArgumentException(probe.literal().toUpperCase(Locale.ROOT))
    );
    String systemKey = "safe-probe-system";
    AdminCategorySystemDefinition definition = AdminCategoryRegistrationFixtures.completeDefinition(
      systemKey,
      "safe-module"
    );
    @SuppressWarnings("unchecked")
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    AdminCategorySystemRegistration registration =
      AdminCategoryRegistrationFixtures.completeRegistration(definition, codec);
    doThrow(rawFailure).when(codec).identifierProfile();

    AdminCategoryRegistrationAggregateValidationException failure = catchThrowableOfType(
      () -> AdminCategoryRegistrationValidator.validate(List.of(registration)),
      AdminCategoryRegistrationAggregateValidationException.class
    );

    assertThat(failure).isNotNull();
    assertThat(failure.diagnostics())
      .extracting(AdminCategoryValidationDiagnostic::category)
      .containsExactly(AdminCategoryValidationCategory.PROFILE_CODEC_INCOMPATIBILITY);
    assertThat(failure.diagnostics()).allSatisfy(diagnostic -> {
      assertThat(diagnostic.safeSystemKey()).isEqualTo(systemKey);
      assertThat(diagnostic.policyReference()).isEmpty();
    });
    assertThat(failure.omittedCount()).isZero();
    assertThat(failure.getCause()).isNull();
    assertThat(failure.getSuppressed()).isEmpty();
    InformationSafetyProbeCorpus.assertNoLeaks(startupSurfaces(failure));
  }

  private static Stream<Probe> probes() {
    return InformationSafetyProbeCorpus.probes().stream();
  }

  private static ActualEvidence[] startupEvidence(
    AdminCategoryRegistrationValidationException typedFailure,
    Throwable startupFailure,
    StructuredLogCapture.CapturedEvent startupLog,
    String startupConsole,
    boolean registryPublished
  ) {
    AdminCategoryValidationDiagnostic diagnostic = typedFailure.diagnostic();
    String constructionPhase = "during registry construction before publication";
    String refreshPhase = "during failed refresh before registry publication";
    ActualValue construction = ActualValue.observedWhen(
      constructionPhase,
      !registryPublished,
      "startup registry was not published"
    );
    ActualValue refresh = ActualValue.observedWhen(
      refreshPhase,
      !registryPublished && !startupLog.transactionActive(),
      "failed refresh precedes registry publication and is outside a transaction"
    );
    ActualValue startupStatus = ActualValue.observed("startup / " + diagnostic.category().code());
    ActualValue refreshStatus = ActualValue.observedWhen(
      "startup / context refresh rejected",
      startupFailure != null && !registryPublished,
      "context refresh was rejected before registry publication"
    );
    ActualValue startupEventAbsent = ActualValue.structurallyAbsent(
      "a typed startup exception is structurally not a category log event",
      lacksNamedMember(typedFailure.getClass(), "event")
    );
    ActualValue startupLevelAbsent = ActualValue.structurallyAbsent(
      "an exception message has no log level",
      lacksNamedMember(Throwable.class, "level")
    );
    ActualValue logEventAbsent = ActualValue.structurallyAbsent(
      "the Spring lifecycle warning has no category-owned event field",
      startupLog.structuredFields().isEmpty()
    );
    ActualValue logLevel = ActualValue.observed(startupLog.level().toString());
    ActualValue logThrowableAbsent = ActualValue.observedWhen(
      "no attached throwable",
      startupLog.attachedThrowable().isEmpty(),
      "startup lifecycle log has no attached throwable"
    );

    return new ActualEvidence[] {
      new ActualEvidence(
        Surface.STARTUP_EXCEPTION_MESSAGE,
        startupStatus,
        startupEventAbsent,
        startupLevelAbsent,
        ActualValue.observedWhen(
          "systemKey,category,component",
          Stream.of("systemKey=", "category=", "component=").allMatch(
            typedFailure.getMessage()::contains
          ),
          "typed startup exception contains only the approved diagnostic fields"
        ),
        ActualValue.observedWhen(
          "cause-free and suppressed-free",
          typedFailure.getCause() == null && typedFailure.getSuppressed().length == 0,
          "typed startup exception discarded causes and suppressed failures"
        ),
        construction,
        throwableSurfaces(startupFailure)
      ),
      new ActualEvidence(
        Surface.STARTUP_DIAGNOSTIC_FIELDS,
        startupStatus,
        ActualValue.structurallyAbsent(
          "typed diagnostic fields are structurally not a category log event",
          lacksNamedMember(diagnostic.getClass(), "event")
        ),
        ActualValue.structurallyAbsent(
          "typed diagnostic fields have no log level",
          lacksNamedMember(diagnostic.getClass(), "level")
        ),
        ActualValue.observedWhen(
          "systemKey,category,component,capability,policyObligation",
          instanceFieldNames(AdminCategoryValidationDiagnostic.class).equals(
            List.of("capability", "category", "component", "policyObligation", "safeSystemKey")
          ),
          "typed diagnostic owns exactly the approved field inventory"
        ),
        ActualValue.observedWhen(
          "diagnostic object retains no throwable",
          Arrays.stream(AdminCategoryValidationDiagnostic.class.getDeclaredFields()).noneMatch(
            field -> Throwable.class.isAssignableFrom(field.getType())
          ),
          "typed diagnostic has no throwable field"
        ),
        construction,
        diagnosticSurfaces(diagnostic)
      ),
      new ActualEvidence(
        Surface.STARTUP_LOG_MESSAGE,
        refreshStatus,
        logEventAbsent,
        logLevel,
        ActualValue.observedWhen(
          "Spring context cancellation message",
          startupLog.message().startsWith("Exception encountered during context initialization"),
          "startup log message is the Spring context cancellation message"
        ),
        logThrowableAbsent,
        refresh,
        List.of(startupLog.message())
      ),
      new ActualEvidence(
        Surface.STARTUP_LOG_FORMATTED_MESSAGE,
        refreshStatus,
        logEventAbsent,
        logLevel,
        ActualValue.observedWhen(
          "formatted Spring context cancellation message plus sanitized exception",
          startupLog
            .formattedMessage()
            .startsWith("Exception encountered during context initialization") &&
            startupLog.formattedMessage().contains(typedFailure.getMessage()),
          "formatted startup log contains only the lifecycle message and sanitized diagnostic"
        ),
        logThrowableAbsent,
        refresh,
        List.of(startupLog.formattedMessage())
      ),
      new ActualEvidence(
        Surface.STARTUP_LOG_ARGUMENTS,
        refreshStatus,
        logEventAbsent,
        logLevel,
        ActualValue.observedWhen(
          "exactly no logger arguments",
          startupLog.arguments().isEmpty(),
          "startup log arguments are empty"
        ),
        logThrowableAbsent,
        refresh,
        startupLog.arguments()
      ),
      new ActualEvidence(
        Surface.STARTUP_LOG_KEY_VALUES,
        refreshStatus,
        logEventAbsent,
        logLevel,
        ActualValue.observedWhen(
          "exactly no key/value fields",
          startupLog.structuredFields().isEmpty(),
          "startup log key/value fields are empty"
        ),
        logThrowableAbsent,
        refresh,
        structuredFieldSurfaces(startupLog)
      ),
      new ActualEvidence(
        Surface.STARTUP_LOG_MDC,
        refreshStatus,
        logEventAbsent,
        logLevel,
        ActualValue.observedWhen(
          "exactly no MDC fields",
          startupLog.mdc().isEmpty(),
          "startup log MDC is empty"
        ),
        logThrowableAbsent,
        refresh,
        mapSurfaces(startupLog.mdc())
      ),
      new ActualEvidence(
        Surface.STARTUP_LOG_MARKERS,
        refreshStatus,
        logEventAbsent,
        logLevel,
        ActualValue.observedWhen(
          "exactly no markers",
          startupLog.markers().isEmpty(),
          "startup log markers are empty"
        ),
        logThrowableAbsent,
        refresh,
        markerSurfaces(startupLog.markers())
      ),
      new ActualEvidence(
        Surface.STARTUP_LOG_THROWABLE,
        refreshStatus,
        logEventAbsent,
        logLevel,
        ActualValue.observedWhen(
          "exactly no attached throwable fields",
          startupLog.attachedThrowable().isEmpty(),
          "startup log has no attached throwable fields"
        ),
        ActualValue.observedWhen(
          "absent; raw source cause was discarded before logging",
          startupLog.attachedThrowable().isEmpty(),
          "raw startup source cause is not attached to the lifecycle log"
        ),
        refresh,
        capturedThrowableSurfaces(startupLog.attachedThrowable().orElse(null))
      ),
      new ActualEvidence(
        Surface.STARTUP_CONSOLE_OUTPUT,
        refreshStatus,
        ActualValue.structurallyAbsent(
          "rendered console output has no category-owned event field",
          !startupConsole.contains("category.event")
        ),
        ActualValue.observedWhen(
          "WARN lifecycle output",
          startupConsole.contains("WARN"),
          "captured startup console contains the lifecycle WARN"
        ),
        ActualValue.observedWhen(
          "captured stdout and stderr as one complete transport surface",
          !startupConsole.isBlank(),
          "combined captured startup output is nonblank"
        ),
        ActualValue.structurallyAbsent(
          "rendered console text has no attached throwable object slot",
          lacksNamedMember(String.class, "throwable")
        ),
        refresh,
        List.of(startupConsole)
      ),
    };
  }

  private static List<String> diagnosticSurfaces(AdminCategoryValidationDiagnostic diagnostic) {
    List<String> surfaces = new ArrayList<>();
    surfaces.add(diagnostic.toString());
    surfaces.add(diagnostic.safeSystemKey());
    surfaces.add(diagnostic.category().code());
    diagnostic.component().ifPresent(value -> surfaces.add(value.code()));
    diagnostic.capability().ifPresent(value -> surfaces.add(value.code()));
    diagnostic.policyObligation().ifPresent(value -> surfaces.add(value.code()));
    return List.copyOf(surfaces);
  }

  private static List<String> structuredFieldSurfaces(StructuredLogCapture.CapturedEvent event) {
    List<String> surfaces = new ArrayList<>();
    event.structuredFields().forEach(field -> {
      surfaces.add(field.key());
      surfaces.add(field.value());
      surfaces.addAll(field.nestedValueSurfaces());
    });
    return List.copyOf(surfaces);
  }

  private static List<String> mapSurfaces(java.util.Map<String, String> values) {
    List<String> surfaces = new ArrayList<>();
    values.forEach((key, value) -> {
      surfaces.add(key);
      surfaces.add(value);
    });
    return List.copyOf(surfaces);
  }

  private static List<String> markerSurfaces(List<StructuredLogCapture.MarkerSnapshot> markers) {
    List<String> surfaces = new ArrayList<>();
    markers.forEach(marker -> addMarkerSurfaces(marker, surfaces));
    return List.copyOf(surfaces);
  }

  private static void addMarkerSurfaces(
    StructuredLogCapture.MarkerSnapshot marker,
    List<String> surfaces
  ) {
    surfaces.add(marker.name());
    surfaces.add(marker.renderedValue());
    marker.references().forEach(reference -> addMarkerSurfaces(reference, surfaces));
  }

  private static List<String> capturedThrowableSurfaces(
    StructuredLogCapture.ThrowableSnapshot throwable
  ) {
    if (throwable == null) {
      return List.of();
    }
    List<String> surfaces = new ArrayList<>();
    addCapturedThrowableSurfaces(throwable, surfaces);
    return List.copyOf(surfaces);
  }

  private static void addCapturedThrowableSurfaces(
    StructuredLogCapture.ThrowableSnapshot throwable,
    List<String> surfaces
  ) {
    surfaces.add(throwable.className());
    surfaces.add(throwable.message());
    surfaces.add(throwable.overridingMessage());
    surfaces.addAll(throwable.stackTrace());
    throwable.suppressed().forEach(value -> addCapturedThrowableSurfaces(value, surfaces));
    throwable.causeOptional().ifPresent(value -> addCapturedThrowableSurfaces(value, surfaces));
  }

  private static boolean lacksNamedMember(Class<?> type, String token) {
    String normalized = token.toLowerCase(Locale.ROOT);
    return Stream.concat(
      Arrays.stream(type.getDeclaredFields()).map(java.lang.reflect.Field::getName),
      Arrays.stream(type.getMethods()).map(java.lang.reflect.Method::getName)
    ).noneMatch(name -> name.toLowerCase(Locale.ROOT).contains(normalized));
  }

  private static StartupAttempt runFailingContext(Probe probe, AtomicBoolean registryPublished) {
    RuntimeException rawFailure = new IllegalStateException(
      probe.literal(),
      new IllegalArgumentException(probe.literal().toUpperCase(Locale.ROOT))
    );
    AtomicInteger sourceInvocationCount = new AtomicInteger();
    AtomicBoolean selectedRawFailureObserved = new AtomicBoolean();
    final Throwable[] startupFailure = new Throwable[1];
    new ApplicationContextRunner()
      .withUserConfiguration(AdminCategoryRegistrationConfiguration.class)
      .withBean(
        "probeRegistrationSource",
        AdminCategorySystemRegistrationSource.class,
        () -> () -> {
          sourceInvocationCount.incrementAndGet();
          try {
            throw rawFailure;
          } catch (RuntimeException thrownFailure) {
            selectedRawFailureObserved.set(thrownFailure == rawFailure);
            throw thrownFailure;
          }
        }
      )
      .withBean("registryPublicationProbe", BeanPostProcessor.class, () ->
        new BeanPostProcessor() {
          @Override
          public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof AdminCategorySystemRegistry) {
              registryPublished.set(true);
            }
            return bean;
          }
        }
      )
      .run(context -> {
        assertThat(context).hasFailed();
        startupFailure[0] = context.getStartupFailure();
      });
    assertThat(startupFailure[0]).isNotNull();
    return new StartupAttempt(
      startupFailure[0],
      rawFailure,
      sourceInvocationCount.get(),
      selectedRawFailureObserved.get()
    );
  }

  private static void assertSelectedProbeSourceExecuted(Probe probe, StartupAttempt attempt) {
    Throwable selectedCause = attempt.selectedRawFailure().getCause();
    boolean selectedProbeFailureMatches =
      probe.literal().equals(attempt.selectedRawFailure().getMessage()) &&
      selectedCause != null &&
      probe.literal().toUpperCase(Locale.ROOT).equals(selectedCause.getMessage());
    if (
      attempt.sourceInvocationCount() != 1 ||
      !attempt.selectedRawFailureObserved() ||
      !selectedProbeFailureMatches
    ) {
      throw new AssertionError(
        "Selected probe registration source did not execute exactly once for " + probe.id()
      );
    }
  }

  private record StartupAttempt(
    Throwable startupFailure,
    RuntimeException selectedRawFailure,
    int sourceInvocationCount,
    boolean selectedRawFailureObserved
  ) {}

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

  private static String expectedBoundedDiagnosticRendering() {
    String prefix =
      "Admin category registration validation failed with 100 retained diagnostic(s) and 37 omitted diagnostic(s): ";
    String diagnostics = java.util.stream.IntStream.range(0, 100)
      .mapToObj(
        index ->
          "systemKey=safe-system-" +
          String.format("%03d", index) +
          ",category=missing-component,component=runtime-ports"
      )
      .collect(java.util.stream.Collectors.joining("; "));
    return prefix + diagnostics;
  }

  private static List<String> startupSurfaces(
    AdminCategoryRegistrationAggregateValidationException failure
  ) {
    List<String> surfaces = new ArrayList<>();
    surfaces.add(failure.getMessage());
    surfaces.add(failure.toString());
    failure.diagnostics().forEach(diagnostic -> {
      surfaces.add(diagnostic.toString());
      surfaces.add(diagnostic.safeSystemKey());
      surfaces.add(diagnostic.category().code());
      diagnostic.component().ifPresent(component -> surfaces.add(component.code()));
      diagnostic.capability().ifPresent(capability -> surfaces.add(capability.code()));
      diagnostic.policyObligation().ifPresent(obligation -> surfaces.add(obligation.code()));
    });
    return List.copyOf(surfaces);
  }

  private static List<String> throwableSurfaces(Throwable failure) {
    List<String> surfaces = new ArrayList<>();
    addThrowableSurfaces(failure, surfaces, new IdentityHashMap<>());
    return List.copyOf(surfaces);
  }

  private static List<String> instanceFieldNames(Class<?> type) {
    return java.util.Arrays.stream(type.getDeclaredFields())
      .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
      .map(java.lang.reflect.Field::getName)
      .sorted()
      .toList();
  }

  private static void addThrowableSurfaces(
    Throwable failure,
    List<String> surfaces,
    IdentityHashMap<Throwable, Boolean> visited
  ) {
    if (failure == null || visited.put(failure, Boolean.TRUE) != null) {
      return;
    }
    surfaces.add(failure.getClass().getName());
    surfaces.add(String.valueOf(failure.getMessage()));
    surfaces.add(failure.toString());
    for (StackTraceElement element : failure.getStackTrace()) {
      surfaces.add(element.toString());
    }
    for (Throwable suppressed : failure.getSuppressed()) {
      addThrowableSurfaces(suppressed, surfaces, visited);
    }
    addThrowableSurfaces(failure.getCause(), surfaces, visited);
  }
}
