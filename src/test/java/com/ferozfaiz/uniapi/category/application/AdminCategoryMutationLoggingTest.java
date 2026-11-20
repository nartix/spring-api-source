package com.ferozfaiz.uniapi.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminCategoryMutationLoggingTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @ParameterizedTest(name = "{0} logs its success lifecycle event inside the transaction")
  @MethodSource("mutations")
  void successfulMutationsLogSafeStructuredLifecycleFields(
    String operation,
    MutationInvocation mutation
  ) {
    var fixture = configuredFixture();
    try (LogCapture logs = LogCapture.attach(AdminCategoryApplicationService.class)) {
      MDC.put("request.id", "request-123");
      TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());

      transaction.executeWithoutResult(ignored -> mutation.invoke(fixture));

      ILoggingEvent event = logs.singleEvent();
      assertThat(event.getLevel()).isEqualTo(Level.INFO);
      assertThat(event.getFormattedMessage()).isEqualTo("admin category mutation succeeded");
      assertThat(event.getMDCPropertyMap()).containsExactly(Map.entry("request.id", "request-123"));
      assertThat(event.getMDCPropertyMap().get("request.id")).matches("[A-Za-z0-9._-]{1,128}");
      assertThat(keyValues(event)).containsExactlyInAnyOrderEntriesOf(
        Map.of(
          "category.event",
          "category." + operation + ".succeeded",
          "systemKey",
          "catalogue",
          "operation",
          operation,
          "outcome",
          "succeeded",
          "categoryId",
          "10"
        )
      );
      assertThat(logs.singleEventWasLoggedInTransaction()).isTrue();
      assertSafe(event);
    }
  }

  @Test
  void replaceSuccessFreezesUpdateTokenAfterPortReturnAndBeforeOuterCommit() {
    var fixture = configuredFixture();
    List<String> timeline = new ArrayList<>();
    when(
      fixture
        .ports()
        .basicMutation()
        .replace(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())
    ).thenAnswer(invocation -> {
      timeline.add("selected-port.update.return");
      return categoryResult();
    });
    TestTransactionManager transactionManager = new TestTransactionManager(timeline);

    try (
      LogCapture logs = LogCapture.attach(
        AdminCategoryApplicationService.class,
        timeline,
        "generic.update.log"
      )
    ) {
      new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
        fixture.service().update("catalogue", 10L, replace(fixture))
      );

      assertThat(timeline).containsExactly(
        "selected-port.update.return",
        "generic.update.log",
        "outer.commit"
      );
      assertThat(transactionManager.commits).isEqualTo(1);
      assertThat(transactionManager.rollbacks).isZero();
      assertThat(keyValues(logs.singleEvent())).containsExactlyInAnyOrderEntriesOf(
        Map.of(
          "category.event",
          "category.update.succeeded",
          "systemKey",
          "catalogue",
          "operation",
          "update",
          "outcome",
          "succeeded",
          "categoryId",
          "10"
        )
      );
    }
  }

  @Test
  void selectedPortFailureRollsBackWithoutGenericSuccessEvent() {
    var fixture = configuredFixture();
    List<String> timeline = new ArrayList<>();
    when(
      fixture
        .ports()
        .basicMutation()
        .replace(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())
    ).thenAnswer(invocation -> {
      timeline.add("selected-port.update.failed");
      throw new IllegalStateException("probe raw cause message");
    });
    TestTransactionManager transactionManager = new TestTransactionManager(timeline);

    try (LogCapture logs = LogCapture.attach(AdminCategoryApplicationService.class)) {
      assertThatThrownBy(() ->
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
          fixture.service().update("catalogue", 10L, replace(fixture))
        )
      )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("probe raw cause message");

      assertThat(logs.events()).isEmpty();
      assertThat(timeline).containsExactly("selected-port.update.failed", "outer.rollback");
      assertThat(transactionManager.commits).isZero();
      assertThat(transactionManager.rollbacks).isEqualTo(1);
    }
  }

  @Test
  void outerFailureAfterSuccessKeepsTheFrozenEventCauseFreeBeforeRollback() {
    var fixture = configuredFixture();
    List<String> timeline = new ArrayList<>();
    when(
      fixture
        .ports()
        .basicMutation()
        .replace(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())
    ).thenAnswer(invocation -> {
      timeline.add("selected-port.update.return");
      return categoryResult();
    });
    TestTransactionManager transactionManager = new TestTransactionManager(timeline);

    try (
      LogCapture logs = LogCapture.attach(
        AdminCategoryApplicationService.class,
        timeline,
        "generic.update.log"
      )
    ) {
      assertThatThrownBy(() ->
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
          fixture.service().update("catalogue", 10L, replace(fixture));
          timeline.add("outer.failure");
          throw new IllegalStateException("probe raw cause message");
        })
      )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("probe raw cause message");

      ILoggingEvent event = logs.singleEvent();
      assertThat(timeline).containsExactly(
        "selected-port.update.return",
        "generic.update.log",
        "outer.failure",
        "outer.rollback"
      );
      assertThat(event.getThrowableProxy()).isNull();
      assertThat(event.getArgumentArray()).isNullOrEmpty();
      assertThat(event.getFormattedMessage()).doesNotContain("probe raw cause message");
      assertThat(keyValues(event).values()).doesNotContain("probe raw cause message");
      assertThat(transactionManager.commits).isZero();
      assertThat(transactionManager.rollbacks).isEqualTo(1);
    }
  }

  @Test
  void unsupportedCapabilityEmitsExactlyOneBoundedWarnAndNoPortWork() {
    var definition = AdminCategoryRuntimeTestFixture.completeDefinition(
      "flat-restricted",
      Set.of(Operation.CAPABILITIES, Operation.LIST, Operation.GET)
    );
    var fixture = AdminCategoryRuntimeTestFixture.harness(definition);
    try (LogCapture logs = LogCapture.attach(AdminCategoryApplicationService.class)) {
      assertThatThrownBy(() -> fixture.service().delete(" flat-restricted ", 10L)).isInstanceOf(
        RuntimeException.class
      );

      ILoggingEvent event = logs.singleEvent();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getMessage()).isEqualTo("admin category capability unsupported");
      assertThat(event.getFormattedMessage()).isEqualTo("admin category capability unsupported");
      assertThat(event.getArgumentArray()).isNullOrEmpty();
      assertThat(event.getMDCPropertyMap()).isEmpty();
      assertThat(event.getMarkerList()).isNullOrEmpty();
      assertThat(event.getThrowableProxy()).isNull();
      assertThat(keyValues(event)).containsExactlyInAnyOrderEntriesOf(
        Map.of(
          "category.event",
          "capability.unsupported",
          "systemKey",
          "flat-restricted",
          "operation",
          "delete",
          "outcome",
          "failed",
          "errorCode",
          "CATEGORY_CAPABILITY_UNSUPPORTED"
        )
      );
      verifyNoInteractions(
        fixture.ports().categoryRead(),
        fixture.ports().hierarchyRead(),
        fixture.ports().basicMutation(),
        fixture.ports().hierarchyMutation(),
        fixture.ports().ordering(),
        fixture.ports().lifecycle()
      );
      assertSafe(event);
    }
  }

  private static Stream<Arguments> mutations() {
    return Stream.of(
      Arguments.of(
        "create",
        (MutationInvocation) fixture -> fixture.service().create("catalogue", create(fixture))
      ),
      Arguments.of(
        "update",
        (MutationInvocation) fixture -> fixture.service().update("catalogue", 10L, replace(fixture))
      ),
      Arguments.of(
        "patch",
        (MutationInvocation) fixture -> fixture.service().patch("catalogue", 10L, patch(fixture))
      ),
      Arguments.of(
        "move",
        (MutationInvocation) fixture ->
          fixture.service().move("catalogue", 10L, placement(fixture, Operation.MOVE))
      ),
      Arguments.of(
        "reorder",
        (MutationInvocation) fixture ->
          fixture.service().reorder("catalogue", 10L, placement(fixture, Operation.REORDER))
      ),
      Arguments.of(
        "activate",
        (MutationInvocation) fixture -> fixture.service().activate("catalogue", 10L)
      ),
      Arguments.of(
        "deactivate",
        (MutationInvocation) fixture -> fixture.service().deactivate("catalogue", 10L)
      ),
      Arguments.of(
        "delete",
        (MutationInvocation) fixture -> fixture.service().delete("catalogue", 10L)
      )
    );
  }

  private static AdminCategoryRuntimeTestFixture.Harness configuredFixture() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var category = categoryResult();
    when(fixture.ports().basicMutation().create(org.mockito.ArgumentMatchers.any())).thenReturn(
      category
    );
    when(
      fixture
        .ports()
        .basicMutation()
        .replace(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())
    ).thenReturn(category);
    when(
      fixture
        .ports()
        .basicMutation()
        .patch(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())
    ).thenReturn(category);
    when(
      fixture
        .ports()
        .ordering()
        .reorder(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())
    ).thenReturn(category);
    return fixture;
  }

  private static com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.Category categoryResult() {
    return new com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.Category(
      10L,
      null,
      1_000_000_000L,
      "Storage",
      "storage",
      true,
      OffsetDateTime.parse("2026-06-29T00:00:00Z"),
      OffsetDateTime.parse("2026-06-30T00:00:00Z")
    );
  }

  private static AdminCategoryCreateValidationInput create(
    AdminCategoryRuntimeTestFixture.Harness fixture
  ) {
    return new AdminCategoryCreateValidationInput(
      fixture.definition(),
      null,
      null,
      null,
      null,
      "raw category payload should not appear",
      "storage",
      true
    );
  }

  private static AdminCategoryReplaceValidationInput replace(
    AdminCategoryRuntimeTestFixture.Harness fixture
  ) {
    return new AdminCategoryReplaceValidationInput(
      fixture.definition(),
      10L,
      null,
      "raw category payload should not appear",
      "storage",
      true
    );
  }

  private static AdminCategoryPatchValidationInput patch(
    AdminCategoryRuntimeTestFixture.Harness fixture
  ) {
    return new AdminCategoryPatchValidationInput(
      fixture.definition(),
      10L,
      null,
      false,
      "raw category payload should not appear",
      true,
      null,
      false,
      null,
      false
    );
  }

  private static AdminCategoryPlacementValidationInput placement(
    AdminCategoryRuntimeTestFixture.Harness fixture,
    Operation operation
  ) {
    return new AdminCategoryPlacementValidationInput(
      fixture.definition(),
      operation,
      null,
      null,
      null,
      null
    );
  }

  private static Map<String, String> keyValues(ILoggingEvent event) {
    return event
      .getKeyValuePairs()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          pair -> pair.key,
          pair -> String.valueOf(pair.value),
          (left, right) -> right
        )
      );
  }

  private static void assertSafe(ILoggingEvent event) {
    String rendered = event.getFormattedMessage() + " " + keyValues(event);
    assertThat(rendered)
      .doesNotContain("raw category payload")
      .doesNotContain("secret-token")
      .doesNotContain("Authorization")
      .doesNotContain("Bearer")
      .doesNotContain("cursor")
      .doesNotContain("permission")
      .doesNotContain("policy")
      .doesNotContain("provider")
      .doesNotContain("select ")
      .doesNotContain("catalogue_category_closure")
      .doesNotContain("password");
  }

  private static final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level originalLevel;
    private final TransactionAwareListAppender appender;

    private LogCapture(Logger logger, Level originalLevel, TransactionAwareListAppender appender) {
      this.logger = logger;
      this.originalLevel = originalLevel;
      this.appender = appender;
    }

    static LogCapture attach(Class<?> loggerType) {
      return attach(loggerType, null, null);
    }

    static LogCapture attach(Class<?> loggerType, List<String> timeline, String eventName) {
      Logger logger = (Logger) LoggerFactory.getLogger(loggerType);
      Level originalLevel = logger.getLevel();
      logger.setLevel(Level.INFO);
      TransactionAwareListAppender appender = new TransactionAwareListAppender(timeline, eventName);
      appender.start();
      logger.addAppender(appender);
      return new LogCapture(logger, originalLevel, appender);
    }

    ILoggingEvent singleEvent() {
      assertThat(appender.list).hasSize(1);
      return appender.list.getFirst();
    }

    List<ILoggingEvent> events() {
      return List.copyOf(appender.list);
    }

    boolean singleEventWasLoggedInTransaction() {
      assertThat(appender.transactionActive).hasSize(1);
      return appender.transactionActive.getFirst();
    }

    @Override
    public void close() {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
    }
  }

  private static final class TransactionAwareListAppender extends ListAppender<ILoggingEvent> {

    private final List<Boolean> transactionActive = new ArrayList<>();
    private final List<String> timeline;
    private final String eventName;

    private TransactionAwareListAppender(List<String> timeline, String eventName) {
      this.timeline = timeline;
      this.eventName = eventName;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
      transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
      if (timeline != null) {
        timeline.add(eventName);
      }
      super.append(eventObject);
    }
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    private final List<String> timeline;
    private int commits;
    private int rollbacks;

    private TestTransactionManager() {
      this(new ArrayList<>());
    }

    private TestTransactionManager(List<String> timeline) {
      this.timeline = timeline;
    }

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
      timeline.add("outer.commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
      timeline.add("outer.rollback");
    }
  }

  @FunctionalInterface
  private interface MutationInvocation {
    void invoke(AdminCategoryRuntimeTestFixture.Harness fixture);
  }
}
