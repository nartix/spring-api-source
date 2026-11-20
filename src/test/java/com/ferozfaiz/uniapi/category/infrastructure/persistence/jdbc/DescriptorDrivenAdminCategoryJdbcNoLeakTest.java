package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.Category;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyProbeCorpus;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyProbeCorpus.Probe;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemProperties;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableOperations;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankOperations;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
  properties = {
    "app.category.admin.jdbc.enabled-systems[0]=descriptor-a",
    "uniapi.category-systems.descriptor-a.owning-module=category",
    "uniapi.category-systems.descriptor-a.node-table=test_category_a",
    "uniapi.category-systems.descriptor-a.closure-table=test_category_a_closure",
    "uniapi.category-systems.descriptor-a.ranking.enabled=true",
    "uniapi.category-systems.descriptor-a.ranking.column-name=sort_rank",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[0]=APPEND",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[1]=BEFORE_ID",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[2]=AFTER_ID",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[3]=TARGET_INDEX",
    "uniapi.category-systems.descriptor-a.ranking.sort-rank-exposed=true",
    "uniapi.category-systems.descriptor-a.fallback-order[0].property=name",
    "uniapi.category-systems.descriptor-a.fallback-order[0].column-name=name",
    "uniapi.category-systems.descriptor-a.fallback-order[0].direction=ASC",
    "uniapi.category-systems.descriptor-a.fallback-order[1].property=id",
    "uniapi.category-systems.descriptor-a.fallback-order[1].column-name=id",
    "uniapi.category-systems.descriptor-a.fallback-order[1].direction=ASC",
    "uniapi.category-systems.descriptor-missing.owning-module=category",
    "uniapi.category-systems.descriptor-missing.node-table=test_category_missing",
    "uniapi.category-systems.descriptor-missing.closure-table=test_category_missing_closure",
    "uniapi.category-systems.descriptor-missing.ranking.enabled=true",
    "uniapi.category-systems.descriptor-missing.ranking.column-name=sort_rank",
    "uniapi.category-systems.descriptor-missing.ranking.placement-modes[0]=APPEND",
    "uniapi.category-systems.descriptor-missing.ranking.sort-rank-exposed=true",
    "uniapi.category-systems.descriptor-missing.fallback-order[0].property=name",
    "uniapi.category-systems.descriptor-missing.fallback-order[0].column-name=name",
    "uniapi.category-systems.descriptor-missing.fallback-order[0].direction=ASC",
    "uniapi.category-systems.descriptor-missing.fallback-order[1].property=id",
    "uniapi.category-systems.descriptor-missing.fallback-order[1].column-name=id",
    "uniapi.category-systems.descriptor-missing.fallback-order[1].direction=ASC",
  }
)
@AutoConfigureMockMvc
class DescriptorDrivenAdminCategoryJdbcNoLeakTest {

  private static final String BASE = "/api/v1/admin/category-systems/descriptor-a/categories";

  @Autowired
  private MockMvc mvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DescriptorDrivenAdminCategoryJdbcProviderFactory factory;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    DescriptorBackedAdminCategoryJdbcFixture.dropSchema(jdbc);
    DescriptorBackedAdminCategoryJdbcFixture.createSchema(jdbc);
  }

  @Test
  void descriptorBackedErrorResponsesDoNotExposeStorageDetails() throws Exception {
    Long rootId = insertNode("Components", "components", null, true, 1_000_000_000L);
    insertPath(rootId, rootId, 0);

    mvc
      .perform(
        get("/api/v1/admin/category-systems/descriptor-missing/categories").with(
          authorityJwt("admin:descriptor-missing-categories:read")
        )
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY_SYSTEM"))
      .andExpect(content().string(Matchers.not(Matchers.containsString("test_category"))))
      .andExpect(content().string(Matchers.not(Matchers.containsString("SELECT"))));

    mvc
      .perform(get(BASE + "/404").with(readJwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
      .andExpect(content().string(Matchers.not(Matchers.containsString("test_category"))));

    mvc
      .perform(
        post(BASE)
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson(null, "Duplicate", "components"))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY_SLUG"))
      .andExpect(content().string(Matchers.not(Matchers.containsString("test_category"))));
  }

  @Test
  void realProviderInvalidCursorHttpResponseAndWarningAreSanitized() throws Exception {
    String rawCursor = "secret-parent-options-token-Authorization-Bearer-SELECT-test_category_a!";

    try (LogCapture logs = LogCapture.attach(DescriptorDrivenAdminCategoryJdbcProvider.class)) {
      mvc
        .perform(
          get(BASE + "/parent-options")
            .param("cursor", rawCursor)
            .param("limit", "25")
            .with(readJwt())
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CATEGORY_PARENT_OPTIONS_CURSOR_INVALID"))
        .andExpect(jsonPath("$.message").value("parent-options cursor is invalid"))
        .andExpect(content().string(Matchers.not(Matchers.containsString(rawCursor))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("Authorization"))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("test_category"))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("SELECT"))));

      ILoggingEvent event = logs.singleEvent();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getFormattedMessage()).isEqualTo("descriptor-backed category read failed");
      assertThat(keyValues(event)).containsExactlyInAnyOrderEntriesOf(
        Map.of(
          "category.event",
          "descriptor_provider.parent_options.cursor_invalid",
          "systemKey",
          DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A,
          "operation",
          "parentOptions",
          "outcome",
          "failed",
          "errorCode",
          "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID"
        )
      );
      assertThat(logs.transactionStates()).containsExactly(true);
      assertSafe(event);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("informationSafetyProbes")
  void everyProhibitedLiteralUsedAsARawCursorIsAbsentFromTheExactHttpErrorAndFullLogEvent(
    Probe probe
  ) throws Exception {
    StructuredLogCapture.CapturedEvent event;
    MvcResult result;
    try (
      StructuredLogCapture logs = StructuredLogCapture.attach(
        DescriptorDrivenAdminCategoryJdbcProvider.class
      )
    ) {
      result = mvc
        .perform(
          get(BASE + "/parent-options")
            .param("cursor", probe.literal())
            .param("limit", "25")
            .header("X-Request-Id", "descriptor-probe-request-001")
            .with(readJwt())
        )
        .andExpect(status().isBadRequest())
        .andReturn();
      event = logs.singleEvent();
    }

    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(iterable(response.fieldNames())).containsExactlyInAnyOrder(
      "timestamp",
      "code",
      "message",
      "path",
      "fieldErrors"
    );
    assertThat(response.path("code").textValue()).isEqualTo(
      "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID"
    );
    assertThat(response.path("message").textValue()).isEqualTo("parent-options cursor is invalid");
    assertThat(response.path("path").textValue()).isEqualTo(BASE + "/parent-options");
    assertThat(response.path("timestamp").textValue()).isNotBlank();
    assertThat(response.path("fieldErrors")).isEmpty();
    InformationSafetyProbeCorpus.assertNoLeaks(responseSurfaces(result));

    assertDescriptorLogEvent(
      event,
      DescriptorDrivenAdminCategoryJdbcProvider.class,
      Level.WARN,
      "descriptor-backed category read failed",
      Map.of(
        "category.event",
        "descriptor_provider.parent_options.cursor_invalid",
        "systemKey",
        DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A,
        "operation",
        "parentOptions",
        "outcome",
        "failed",
        "errorCode",
        "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID"
      ),
      Map.of("request.id", "descriptor-probe-request-001"),
      true
    );
    InformationSafetyProbeCorpus.assertNoLeaks(event.searchableSurfaces());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("databaseFailureProbes")
  void sqlAndAdapterFailuresWithNestedRawCausesNeverReachAnyDescriptorLogSurface(Probe probe) {
    NamedParameterJdbcTemplate failingJdbc = mock(NamedParameterJdbcTemplate.class);
    BadSqlGrammarException rawFailure = new BadSqlGrammarException(
      "safe descriptor read",
      probe.literal(),
      new SQLException(probe.literal(), new IllegalStateException(probe.literal()))
    );
    when(
      failingJdbc.queryForObject(
        anyString(),
        anyMap(),
        org.mockito.ArgumentMatchers.<RowMapper<Category>>any()
      )
    ).thenThrow(rawFailure);
    DescriptorDrivenAdminCategoryJdbcProvider provider = provider(failingJdbc);

    StructuredLogCapture.CapturedEvent event;
    RuntimeException thrown;
    try (
      StructuredLogCapture logs = StructuredLogCapture.attach(
        DescriptorDrivenAdminCategoryJdbcProvider.class
      )
    ) {
      thrown = catchThrowableOfType(() -> provider.get(42L), RuntimeException.class);
      event = logs.singleEvent();
    }

    assertThat(thrown).isSameAs(rawFailure);
    assertThat(rawFailure.getCause()).isNotNull();
    assertThat(rawFailure.getCause().getCause()).isNotNull();
    assertDescriptorLogEvent(
      event,
      DescriptorDrivenAdminCategoryJdbcProvider.class,
      Level.WARN,
      "descriptor-backed category read failed",
      Map.of(
        "category.event",
        "descriptor_provider.category.read.get",
        "systemKey",
        DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A,
        "operation",
        "get",
        "outcome",
        "failed",
        "categoryId",
        "42"
      ),
      Map.of(),
      false
    );
    InformationSafetyProbeCorpus.assertNoLeaks(event.searchableSurfaces());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("configurationFailureProbes")
  void unsafeConfigurationAndNestedAdapterCausesBecomeExactCauseFreeRejectedEvents(Probe probe) {
    AdminCategoryJdbcProviderProperties properties = new AdminCategoryJdbcProviderProperties();
    properties.setEnabledSystems(List.of(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A));
    RuntimeException nestedCause = new IllegalStateException(
      probe.literal(),
      new IllegalArgumentException(probe.literal().toUpperCase(Locale.ROOT))
    );
    IllegalArgumentException rawConfigurationFailure = new IllegalArgumentException(
      probe.literal(),
      nestedCause
    );
    AdminCategoryJdbcProviderProperties.SystemProperties unsafeConfiguration =
      new AdminCategoryJdbcProviderProperties.SystemProperties() {
        @Override
        public String getNameColumn() {
          throw rawConfigurationFailure;
        }
      };
    properties.setSystems(
      Map.of(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A, unsafeConfiguration)
    );

    StructuredLogCapture.CapturedEvent event;
    RuntimeException failure;
    try (
      StructuredLogCapture logs = StructuredLogCapture.attach(
        DescriptorDrivenAdminCategoryJdbcProviderFactory.class
      )
    ) {
      failure = catchThrowableOfType(
        () -> newFactory(properties, registry(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A)),
        RuntimeException.class
      );
      event = logs.singleEvent();
    }

    assertThat(failure)
      .isInstanceOf(AdminCategoryRegistrationValidationException.class)
      .hasMessage(
        "Admin category registration validation failed: systemKey=" +
          DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A +
          ",category=invalid-collection,component=registration-source"
      );
    assertThat(failure.getCause()).isNull();
    assertThat(failure.getSuppressed()).isEmpty();
    assertThat(rawConfigurationFailure.getCause()).isSameAs(nestedCause);
    assertThat(nestedCause.getCause()).isNotNull();
    InformationSafetyProbeCorpus.assertNoLeaks(throwableSurfaces(failure));
    assertDescriptorLogEvent(
      event,
      DescriptorDrivenAdminCategoryJdbcProviderFactory.class,
      Level.ERROR,
      "descriptor-backed category provider configuration rejected",
      Map.of(
        "category.event",
        "descriptor_provider.invalid_configuration",
        "systemKey",
        DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A,
        "outcome",
        "rejected"
      ),
      Map.of(),
      false
    );
    InformationSafetyProbeCorpus.assertNoLeaks(event.searchableSurfaces());
  }

  @Test
  void missingDescriptorProducesOnlyTheFrozenCauseFreeSafeErrorAndRejectedEvent() {
    AdminCategoryJdbcProviderProperties properties = new AdminCategoryJdbcProviderProperties();
    properties.setEnabledSystems(List.of("safe-missing-descriptor"));

    StructuredLogCapture.CapturedEvent event;
    RuntimeException failure;
    try (
      StructuredLogCapture logs = StructuredLogCapture.attach(
        DescriptorDrivenAdminCategoryJdbcProviderFactory.class
      )
    ) {
      failure = catchThrowableOfType(
        () -> newFactory(properties, registry(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A)),
        RuntimeException.class
      );
      event = logs.singleEvent();
    }

    assertThat(failure)
      .isInstanceOf(AdminCategoryRegistrationValidationException.class)
      .hasMessage(
        "Admin category registration validation failed: systemKey=safe-missing-descriptor," +
          "category=missing-component,component=registration-source"
      );
    assertThat(failure.getCause()).isNull();
    assertThat(failure.getSuppressed()).isEmpty();
    InformationSafetyProbeCorpus.assertNoLeaks(throwableSurfaces(failure));
    assertDescriptorLogEvent(
      event,
      DescriptorDrivenAdminCategoryJdbcProviderFactory.class,
      Level.ERROR,
      "descriptor-backed category provider descriptor missing",
      Map.of(
        "category.event",
        "descriptor_provider.missing_descriptor",
        "systemKey",
        "safe-missing-descriptor",
        "outcome",
        "rejected"
      ),
      Map.of(),
      false
    );
    InformationSafetyProbeCorpus.assertNoLeaks(event.searchableSurfaces());
  }

  @Test
  void everyDescriptorMutationSuccessUsesExactSafeFieldsInsideTheTransaction() {
    DescriptorDrivenAdminCategoryJdbcProvider provider = provider();
    var source = provider.create(
      new AdminCategoryCreateCommand(null, "Source", "source", true, SparseOrderTarget.append())
    );
    var destination = provider.create(
      new AdminCategoryCreateCommand(
        null,
        "Destination",
        "destination",
        true,
        SparseOrderTarget.append()
      )
    );
    AtomicReference<Long> affectedId = new AtomicReference<>();

    try (LogCapture logs = LogCapture.attach(DescriptorDrivenAdminCategoryJdbcProvider.class)) {
      runInOuterTransaction(() -> {
        affectedId.set(
          provider
            .create(
              new AdminCategoryCreateCommand(
                source.id(),
                "raw category payload Authorization Bearer SELECT test_category_a",
                "safe-mutation-slug",
                true,
                SparseOrderTarget.append()
              )
            )
            .id()
        );
        provider.replace(
          affectedId.get(),
          new AdminCategoryUpdateCommand(
            source.id(),
            "Updated raw category payload",
            "safe-mutation-updated",
            true
          )
        );
        provider.patch(
          affectedId.get(),
          new AdminCategoryPatchCommand(
            null,
            false,
            "Patched Authorization Bearer SELECT test_category_a",
            true,
            null,
            false,
            null,
            false
          )
        );
        provider.move(
          affectedId.get(),
          new AdminCategoryMoveCommand(destination.id(), SparseOrderTarget.append())
        );
        provider.reorder(
          affectedId.get(),
          new AdminCategoryReorderCommand(destination.id(), SparseOrderTarget.append())
        );
        provider.activate(affectedId.get());
        provider.deactivate(affectedId.get());
        provider.delete(affectedId.get());
      });

      List<String> operations = List.of(
        "create",
        "update",
        "patch",
        "move",
        "reorder",
        "activate",
        "deactivate",
        "delete"
      );
      assertThat(logs.events()).hasSize(operations.size());
      for (int index = 0; index < operations.size(); index++) {
        String operation = operations.get(index);
        ILoggingEvent event = logs.events().get(index);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("descriptor-backed category mutation");
        assertThat(keyValues(event)).containsExactlyInAnyOrderEntriesOf(
          mutationFields(operation, "succeeded", affectedId.get(), null)
        );
        assertSafe(event);
      }
      assertThat(logs.transactionStates()).containsOnly(true).hasSize(operations.size());
    }

    assertThat(
      jdbc.queryForObject(
        "SELECT COUNT(*) FROM test_category_a WHERE id = ?",
        Integer.class,
        affectedId.get()
      )
    ).isZero();
  }

  @Test
  void everyDescriptorMutationFailureUsesExactSafeFieldsInsideTheOuterTransaction() {
    Long existingId = insertNode(
      "Existing raw category payload",
      "existing",
      null,
      true,
      1_000_000_000L
    );
    insertPath(existingId, existingId, 0);
    DescriptorDrivenAdminCategoryJdbcProvider provider = provider();
    Long missingId = 404L;
    List<MutationFailureCase> failures = List.of(
      new MutationFailureCase("create", null, "DUPLICATE_CATEGORY_SLUG", () ->
        provider.create(
          new AdminCategoryCreateCommand(
            null,
            "Authorization Bearer SELECT test_category_a",
            "existing",
            true,
            SparseOrderTarget.append()
          )
        )
      ),
      new MutationFailureCase("update", missingId, "CATEGORY_NOT_FOUND", () ->
        provider.replace(
          missingId,
          new AdminCategoryUpdateCommand(null, "Missing", "missing-update", true)
        )
      ),
      new MutationFailureCase("patch", missingId, "CATEGORY_NOT_FOUND", () ->
        provider.patch(
          missingId,
          new AdminCategoryPatchCommand(null, false, "Missing", true, null, false, null, false)
        )
      ),
      new MutationFailureCase("move", missingId, "CATEGORY_NOT_FOUND", () ->
        provider.move(missingId, new AdminCategoryMoveCommand(null, SparseOrderTarget.append()))
      ),
      new MutationFailureCase("reorder", missingId, "CATEGORY_NOT_FOUND", () ->
        provider.reorder(
          missingId,
          new AdminCategoryReorderCommand(null, SparseOrderTarget.append())
        )
      ),
      new MutationFailureCase("activate", missingId, "CATEGORY_NOT_FOUND", () ->
        provider.activate(missingId)
      ),
      new MutationFailureCase("deactivate", missingId, "CATEGORY_NOT_FOUND", () ->
        provider.deactivate(missingId)
      ),
      new MutationFailureCase("delete", missingId, "CATEGORY_NOT_FOUND", () ->
        provider.delete(missingId)
      )
    );

    try (LogCapture logs = LogCapture.attach(DescriptorDrivenAdminCategoryJdbcProvider.class)) {
      for (MutationFailureCase failure : failures) {
        assertThatThrownBy(() -> runInOuterTransaction(failure.action())).isInstanceOfSatisfying(
          AdminCategoryException.class,
          exception -> assertThat(exception.code()).isEqualTo(failure.errorCode())
        );
      }

      assertThat(logs.events()).hasSize(failures.size());
      for (int index = 0; index < failures.size(); index++) {
        MutationFailureCase failure = failures.get(index);
        ILoggingEvent event = logs.events().get(index);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("descriptor-backed category mutation");
        assertThat(keyValues(event)).containsExactlyInAnyOrderEntriesOf(
          mutationFields(failure.operation(), "failed", failure.categoryId(), failure.errorCode())
        );
        assertSafe(event);
      }
      assertThat(logs.transactionStates()).containsOnly(true).hasSize(failures.size());
    }
  }

  @Test
  void descriptorProviderMutationLogsStableEventWithoutPersistenceInternals() {
    DescriptorDrivenAdminCategoryJdbcProvider provider = provider();

    try (LogCapture logs = LogCapture.attach(DescriptorDrivenAdminCategoryJdbcProvider.class)) {
      provider.create(
        new AdminCategoryCreateCommand(
          null,
          "raw category payload Authorization Bearer SELECT test_category_a",
          "safe-slug",
          true,
          SparseOrderTarget.append()
        )
      );

      ILoggingEvent event = logs.singleEvent();
      assertThat(event.getLevel()).isEqualTo(Level.INFO);
      assertThat(event.getFormattedMessage()).isEqualTo("descriptor-backed category mutation");
      assertThat(keyValues(event))
        .containsEntry("category.event", "descriptor_provider.category.create")
        .containsEntry("systemKey", DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A)
        .containsEntry("operation", "create")
        .containsEntry("outcome", "succeeded");
      assertSafe(event);
    }
  }

  @Test
  void descriptorProviderReadLogsStableEventsWithoutPersistenceInternals() {
    Long rootId = insertNode(
      "raw category payload Authorization Bearer SELECT test_category_a",
      "components",
      null,
      true,
      1_000_000_000L
    );
    insertPath(rootId, rootId, 0);
    DescriptorDrivenAdminCategoryJdbcProvider provider = provider();

    try (LogCapture logs = LogCapture.attach(DescriptorDrivenAdminCategoryJdbcProvider.class)) {
      assertThat(provider.get(rootId).slug()).isEqualTo("components");

      assertThatThrownBy(() ->
        provider.parentOptions(
          new AdminCategoryParentOptionsQuery(null, 25, "invalid!", List.of(), null, false)
        )
      ).isInstanceOfSatisfying(AdminCategoryException.class, ex ->
        assertThat(ex.code()).isEqualTo("CATEGORY_PARENT_OPTIONS_CURSOR_INVALID")
      );

      assertThat(logs.events()).hasSize(2);
      ILoggingEvent readEvent = logs.events().get(0);
      assertThat(readEvent.getLevel()).isEqualTo(Level.INFO);
      assertThat(readEvent.getFormattedMessage()).isEqualTo("descriptor-backed category read");
      assertThat(keyValues(readEvent))
        .containsEntry("category.event", "descriptor_provider.category.read.get")
        .containsEntry("systemKey", DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A)
        .containsEntry("operation", "get")
        .containsEntry("outcome", "succeeded")
        .containsEntry("categoryId", String.valueOf(rootId));
      assertSafe(readEvent);

      ILoggingEvent cursorEvent = logs.events().get(1);
      assertThat(cursorEvent.getLevel()).isEqualTo(Level.WARN);
      assertThat(cursorEvent.getFormattedMessage()).isEqualTo(
        "descriptor-backed category read failed"
      );
      assertThat(keyValues(cursorEvent))
        .containsEntry("category.event", "descriptor_provider.parent_options.cursor_invalid")
        .containsEntry("systemKey", DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A)
        .containsEntry("operation", "parentOptions")
        .containsEntry("outcome", "failed")
        .containsEntry("errorCode", "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID");
      assertSafe(cursorEvent);
    }
  }

  @Test
  void descriptorProviderMutationFailureLogsStableEventWithoutPersistenceInternals() {
    Long rootId = insertNode("Components", "components", null, true, 1_000_000_000L);
    insertPath(rootId, rootId, 0);
    DescriptorDrivenAdminCategoryJdbcProvider provider = provider();

    try (LogCapture logs = LogCapture.attach(DescriptorDrivenAdminCategoryJdbcProvider.class)) {
      assertThatThrownBy(() ->
        provider.create(
          new AdminCategoryCreateCommand(
            null,
            "raw category payload Authorization Bearer SELECT test_category_a",
            "components",
            true,
            SparseOrderTarget.append()
          )
        )
      ).isInstanceOfSatisfying(AdminCategoryException.class, ex ->
        assertThat(ex.code()).isEqualTo("DUPLICATE_CATEGORY_SLUG")
      );

      ILoggingEvent event = logs.singleEvent();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getFormattedMessage()).isEqualTo("descriptor-backed category mutation");
      assertThat(keyValues(event))
        .containsEntry("category.event", "descriptor_provider.category.create")
        .containsEntry("systemKey", DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A)
        .containsEntry("operation", "create")
        .containsEntry("outcome", "failed")
        .containsEntry("errorCode", "DUPLICATE_CATEGORY_SLUG");
      assertSafe(event);
    }
  }

  @Test
  void descriptorFactoryRegistrationLogsStableEventWithoutDescriptorInternals() {
    AdminCategoryJdbcProviderProperties properties = new AdminCategoryJdbcProviderProperties();
    properties.setEnabledSystems(List.of(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A));
    @SuppressWarnings("unchecked")
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    when(codec.identifierProfile()).thenReturn(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);
    AdminCategoryDefinitionValidator policies = new AdminCategoryDefinitionValidator();

    try (
      LogCapture logs = LogCapture.attach(DescriptorDrivenAdminCategoryJdbcProviderFactory.class)
    ) {
      new DescriptorDrivenAdminCategoryJdbcProviderFactory(
        properties,
        registry(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A),
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

      ILoggingEvent event = logs.singleEvent();
      assertThat(event.getLevel()).isEqualTo(Level.INFO);
      assertThat(event.getFormattedMessage()).isEqualTo(
        "descriptor-backed category provider enabled"
      );
      assertThat(keyValues(event))
        .containsEntry("category.event", "descriptor_provider.enabled")
        .containsEntry("systemKey", DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A)
        .containsEntry("outcome", "enabled");
      assertSafe(event);
    }
  }

  private DescriptorDrivenAdminCategoryJdbcProvider provider() {
    return (DescriptorDrivenAdminCategoryJdbcProvider) factory
      .registrations()
      .getFirst()
      .runtimeBinding()
      .orElseThrow()
      .ports()
      .categoryRead()
      .orElseThrow();
  }

  private static DescriptorDrivenAdminCategoryJdbcProvider provider(
    NamedParameterJdbcTemplate jdbc
  ) {
    CategorySystemRegistry registry = registry(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A);
    var categorySystem = registry
      .findDescriptor(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A)
      .orElseThrow();
    AdminCategoryNodeTableDescriptor node = AdminCategoryNodeTableDescriptor.from(
      categorySystem,
      new AdminCategoryJdbcProviderProperties.SystemProperties()
    );
    return new DescriptorDrivenAdminCategoryJdbcProvider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A,
      categorySystem,
      node,
      jdbc,
      mock(ClosureTableOperations.class),
      mock(SparseRankOperations.class),
      new TransactionTemplate(mock(PlatformTransactionManager.class)),
      new AdminCategoryParentOptionsCursorCodec()
    );
  }

  private static DescriptorDrivenAdminCategoryJdbcProviderFactory newFactory(
    AdminCategoryJdbcProviderProperties properties,
    CategorySystemRegistry categorySystems
  ) {
    @SuppressWarnings("unchecked")
    AdminCategoryIdentifierCodec<Long> codec = mock(AdminCategoryIdentifierCodec.class);
    when(codec.identifierProfile()).thenReturn(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);
    AdminCategoryDefinitionValidator policies = new AdminCategoryDefinitionValidator();
    return new DescriptorDrivenAdminCategoryJdbcProviderFactory(
      properties,
      categorySystems,
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

  private static Stream<Probe> informationSafetyProbes() {
    return InformationSafetyProbeCorpus.probes().stream();
  }

  private static Stream<Probe> databaseFailureProbes() {
    return informationSafetyProbes();
  }

  private static Stream<Probe> configurationFailureProbes() {
    return informationSafetyProbes();
  }

  private static void assertDescriptorLogEvent(
    StructuredLogCapture.CapturedEvent event,
    Class<?> expectedLogger,
    Level expectedLevel,
    String expectedMessage,
    Map<String, String> expectedFields,
    Map<String, String> expectedMdc,
    boolean expectedTransactionActive
  ) {
    assertThat(event.level()).isEqualTo(expectedLevel);
    assertThat(event.loggerName()).isEqualTo(expectedLogger.getName());
    assertThat(event.message()).isEqualTo(expectedMessage);
    assertThat(event.formattedMessage()).isEqualTo(expectedMessage);
    assertThat(event.arguments()).isEmpty();
    assertThat(event.structuredFieldNames()).containsExactlyInAnyOrderElementsOf(
      expectedFields.keySet()
    );
    assertThat(structuredFields(event)).containsExactlyInAnyOrderEntriesOf(expectedFields);
    assertThat(event.mdc()).containsExactlyInAnyOrderEntriesOf(expectedMdc);
    assertThat(event.markers()).isEmpty();
    assertThat(event.attachedThrowable()).isEmpty();
    assertThat(event.transactionActive()).isEqualTo(expectedTransactionActive);
  }

  private static Map<String, String> structuredFields(StructuredLogCapture.CapturedEvent event) {
    return event
      .structuredFields()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          StructuredLogCapture.StructuredField::key,
          StructuredLogCapture.StructuredField::value,
          (left, right) -> right,
          LinkedHashMap::new
        )
      );
  }

  private static List<String> responseSurfaces(MvcResult result) throws Exception {
    List<String> surfaces = new ArrayList<>();
    surfaces.add(result.getResponse().getContentAsString());
    for (String headerName : result.getResponse().getHeaderNames()) {
      surfaces.add(headerName);
      surfaces.addAll(result.getResponse().getHeaders(headerName));
    }
    return List.copyOf(surfaces);
  }

  private static Iterable<String> iterable(java.util.Iterator<String> iterator) {
    return () -> iterator;
  }

  private static List<String> throwableSurfaces(Throwable failure) {
    List<String> surfaces = new ArrayList<>();
    java.util.Set<Throwable> visited = java.util.Collections.newSetFromMap(
      new java.util.IdentityHashMap<>()
    );
    collectThrowableSurfaces(failure, surfaces, visited);
    return List.copyOf(surfaces);
  }

  private static void collectThrowableSurfaces(
    Throwable failure,
    List<String> surfaces,
    java.util.Set<Throwable> visited
  ) {
    if (failure == null || !visited.add(failure)) {
      return;
    }
    surfaces.add(failure.getClass().getName());
    surfaces.add(java.util.Objects.requireNonNullElse(failure.getMessage(), ""));
    for (Throwable suppressed : failure.getSuppressed()) {
      collectThrowableSurfaces(suppressed, surfaces, visited);
    }
    collectThrowableSurfaces(failure.getCause(), surfaces, visited);
  }

  private Long insertNode(String name, String slug, Long parentId, boolean active, long sortRank) {
    return DescriptorBackedAdminCategoryJdbcFixture.insertNode(
      jdbc,
      DescriptorBackedAdminCategoryJdbcFixture.TABLE_A,
      name,
      slug,
      parentId,
      active,
      sortRank
    );
  }

  private void insertPath(Long ancestorId, Long descendantId, int depth) {
    DescriptorBackedAdminCategoryJdbcFixture.insertClosure(
      jdbc,
      DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A,
      ancestorId,
      descendantId,
      depth
    );
  }

  private static String categoryJson(Long parentId, String name, String slug) {
    String parent = parentId == null ? "null" : parentId.toString();
    return """
    {
      "parentId": %s,
      "name": "%s",
      "slug": "%s",
      "active": true
    }
    """.formatted(parent, name, slug);
  }

  private static RequestPostProcessor readJwt() {
    return authorityJwt("admin:descriptor-a-categories:read");
  }

  private static RequestPostProcessor writeJwt() {
    return authorityJwt("admin:descriptor-a-categories:write");
  }

  private static RequestPostProcessor authorityJwt(String authority) {
    return jwt().authorities(new SimpleGrantedAuthority(authority));
  }

  private static CategorySystemRegistry registry(String systemKey) {
    CategorySystemProperties properties = new CategorySystemProperties();
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
    properties.setCategorySystems(Map.of(systemKey, system));
    return new CategorySystemRegistry(properties);
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

  private void runInOuterTransaction(Runnable action) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
  }

  private static Map<String, String> mutationFields(
    String operation,
    String outcome,
    Long categoryId,
    String errorCode
  ) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("category.event", "descriptor_provider.category." + operation);
    fields.put("systemKey", DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A);
    fields.put("operation", operation);
    fields.put("outcome", outcome);
    if (categoryId != null) {
      fields.put("categoryId", categoryId.toString());
    }
    if (errorCode != null) {
      fields.put("errorCode", errorCode);
    }
    return fields;
  }

  private static Map<String, String> keyValues(ILoggingEvent event) {
    if (event.getKeyValuePairs() == null) {
      return Map.of();
    }
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
      .doesNotContain("Authorization")
      .doesNotContain("Bearer")
      .doesNotContain("SELECT")
      .doesNotContain("test_category")
      .doesNotContain("closure")
      .doesNotContain("password")
      .doesNotContain("secret");
  }

  private record MutationFailureCase(
    String operation,
    Long categoryId,
    String errorCode,
    Runnable action
  ) {}

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
      Logger logger = (Logger) LoggerFactory.getLogger(loggerType);
      Level originalLevel = logger.getLevel();
      logger.setLevel(Level.INFO);
      TransactionAwareListAppender appender = new TransactionAwareListAppender();
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

    List<Boolean> transactionStates() {
      return appender.transactionStates();
    }

    @Override
    public void close() {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
    }
  }

  private static final class TransactionAwareListAppender extends ListAppender<ILoggingEvent> {

    private final java.util.ArrayList<Boolean> transactionStates = new java.util.ArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
      transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
      super.append(event);
    }

    List<Boolean> transactionStates() {
      return List.copyOf(transactionStates);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ContainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
    }
  }
}
