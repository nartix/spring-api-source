package com.ferozfaiz.uniapi.category.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.category.api.AdminCategoryApiExceptionHandler;
import com.ferozfaiz.uniapi.category.api.AdminCategoryController;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.authorization.AdminCategorySystemAuthorizationPolicy;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationCategory;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationComponent;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationDiagnostic;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyEvidenceLedger.ActualEvidence;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyEvidenceLedger.ActualValue;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyEvidenceLedger.Surface;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyProbeCorpus.Probe;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.category.support.sql.CountingDataSource;
import com.ferozfaiz.uniapi.category.support.sql.SqlStatementObservation;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@WebMvcTest(AdminCategoryController.class)
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  AdminCategoryApiExceptionHandler.class,
  AdminCategorySystemAuthorizationPolicy.class,
})
class AdminCategoryInformationSafetyContractTest {

  private static final String UNKNOWN_PATH = "/api/v1/admin/category-systems/unknown/categories";

  @Autowired
  private MockMvc mvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private AdminCategoryApplicationService categories;

  @MockitoBean
  private AdminCategorySystemRegistry registrations;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void corpusAndClosedFieldInventoriesAreExactAndDuplicateFree() {
    assertThat(InformationSafetyProbeCorpus.probes()).hasSize(20);
    assertThat(InformationSafetyProbeCorpus.probeIds()).doesNotHaveDuplicates();
    assertThat(InformationSafetyProbeCorpus.prohibitedLiterals()).doesNotHaveDuplicates();
    assertThat(InformationSafetyProbeCorpus.categoryOwnedStructuredFieldNames()).containsExactly(
      "category.event",
      "systemKey",
      "operation",
      "outcome",
      "errorCode",
      "categoryId"
    );
    assertThat(InformationSafetyProbeCorpus.platformMdcFieldNames()).containsExactly("request.id");
    InformationSafetyEvidenceLedger.assertComplete();
  }

  @AfterAll
  static void assertObservedInformationSafetyLedgerWhenAllProvingTestsFinish() {
    InformationSafetyEvidenceLedger.finishAndAssertObserved(
      InformationSafetyEvidenceLedger.ProvingTest.PUBLIC_ERROR,
      InformationSafetyEvidenceLedger.ProvingTest.GENERIC_LOG,
      InformationSafetyEvidenceLedger.ProvingTest.SQL_CAPTURE
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("probes")
  void publicErrorBodyAndHeadersNeverRenderNestedInternalProbeCauses(Probe probe) throws Exception {
    RuntimeException rawCause = new IllegalStateException(
      probe.literal(),
      new IllegalArgumentException(probe.literal().toUpperCase(Locale.ROOT))
    );
    AdminCategoryException failure = AdminCategoryErrors.unknownSystem("unknown");
    failure.initCause(rawCause);
    java.util.concurrent.atomic.AtomicBoolean transactionActiveDuringDispatch =
      new java.util.concurrent.atomic.AtomicBoolean(true);
    when(categories.definition("unknown", Operation.LIST)).thenAnswer(invocation -> {
      transactionActiveDuringDispatch.set(
        TransactionSynchronizationManager.isActualTransactionActive()
      );
      throw failure;
    });

    var result = mvc
      .perform(get(UNKNOWN_PATH).with(readJwt("unknown")))
      .andExpect(status().isNotFound())
      .andReturn();
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(iterable(response.fieldNames())).containsExactlyInAnyOrder(
      "timestamp",
      "code",
      "message",
      "path",
      "fieldErrors"
    );
    assertThat(response.path("code").textValue()).isEqualTo("UNKNOWN_CATEGORY_SYSTEM");
    assertThat(response.path("message").textValue()).isEqualTo("category system was not found");
    assertThat(response.path("path").textValue()).isEqualTo(UNKNOWN_PATH);
    assertThat(response.path("timestamp").textValue()).isNotBlank();
    assertThat(response.path("fieldErrors").isArray()).isTrue();
    assertThat(response.path("fieldErrors")).isEmpty();
    InformationSafetyProbeCorpus.assertNoLeaks(
      responseSurfaces(result.getResponse().getContentAsString(), result.getResponse())
    );
    InformationSafetyEvidenceLedger.assertCovered(
      probe,
      httpEvidence(
        response,
        result.getResponse(),
        result.getResponse().getContentAsString(),
        transactionActiveDuringDispatch.get()
      )
    );
    if (failure.getCause() != rawCause || rawCause.getCause() == null) {
      throw new AssertionError("probe cause was not injected for " + probe.id());
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("probes")
  void everyProbeFlowsThroughRealGenericUnknownLoggingWithoutReachingAnyLogSurface(Probe probe) {
    AdminCategorySystemRegistry registry = mock(AdminCategorySystemRegistry.class);
    RuntimeException rawCause = new IllegalStateException(
      probe.literal(),
      new IllegalArgumentException(probe.literal().toUpperCase(Locale.ROOT))
    );
    AdminCategoryRegistrationValidationException collaboratorFailure =
      new AdminCategoryValidationDiagnostic(
        "unknown",
        AdminCategoryValidationCategory.INVALID_COLLECTION,
        Optional.of(AdminCategoryValidationComponent.REGISTRATION),
        Optional.empty(),
        Optional.empty()
      ).asException();
    collaboratorFailure.initCause(rawCause);
    when(registry.findRegistration("unknown")).thenThrow(collaboratorFailure);
    AdminCategoryApplicationService service = new AdminCategoryApplicationService(registry);
    StructuredLogCapture.CapturedEvent event;
    MDC.put("request.id", "bounded-request-001");
    try (StructuredLogCapture capture = StructuredLogCapture.attach(service.getClass())) {
      AdminCategoryException failure = catchThrowableOfType(
        () -> service.definition("unknown", Operation.LIST),
        AdminCategoryException.class
      );
      assertThat(failure).isNotNull();
      assertThat(failure.code()).isEqualTo("UNKNOWN_CATEGORY_SYSTEM");
      assertThat(failure.getCause()).isNull();
      assertThat(failure.getMessage()).doesNotContainIgnoringCase(probe.literal());
      event = capture.singleEvent();
    } finally {
      MDC.remove("request.id");
    }

    assertThat(event.level()).isEqualTo(Level.WARN);
    assertThat(event.loggerName()).isEqualTo(AdminCategoryApplicationService.class.getName());
    assertThat(event.message()).isEqualTo("unknown category system");
    assertThat(event.formattedMessage()).isEqualTo("unknown category system");
    assertThat(event.arguments()).isEmpty();
    assertThat(event.attachedThrowable()).isEmpty();
    assertThat(event.markers()).isEmpty();
    assertThat(event.structuredFieldNames()).containsExactly(
      "category.event",
      "systemKey",
      "outcome",
      "errorCode"
    );
    assertThat(structuredFields(event)).containsExactlyEntriesOf(
      Map.of(
        "category.event",
        "system.unknown",
        "systemKey",
        "unknown",
        "outcome",
        "failed",
        "errorCode",
        "UNKNOWN_CATEGORY_SYSTEM"
      )
    );
    assertThat(event.mdc()).containsExactly(Map.entry("request.id", "bounded-request-001"));
    assertThat(event.transactionActive()).isFalse();
    InformationSafetyProbeCorpus.assertNoLeaks(event.searchableSurfaces());
    InformationSafetyEvidenceLedger.assertCovered(probe, genericLogEvidence(event));
    assertThat(collaboratorFailure.getCause()).isSameAs(rawCause);
    assertThat(rawCause.getCause()).isNotNull();
    verify(registry).findRegistration("unknown");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("probes")
  void executableSqlCaptureRetainsProbePrivatelyButPublishesOnlySafeShapeEvidence(Probe probe)
    throws SQLException {
    CountingDataSource dataSource = new CountingDataSource(fakeDataSource());
    String connectionRendering;
    String statementRendering;
    boolean transactionActiveDuringSql;
    try (Connection connection = dataSource.getConnection()) {
      try (Statement statement = connection.createStatement()) {
        dataSource.reset();
        transactionActiveDuringSql = TransactionSynchronizationManager.isActualTransactionActive();
        statement.execute("select 1 /* " + probe.literal() + " */");
        connectionRendering = connection.toString();
        statementRendering = statement.toString();
      }
    }

    SqlStatementObservation observation = dataSource.measurementObservation();
    assertThat(observation.statementCount()).isOne();
    assertThat(observation.statementKinds()).containsExactly(
      SqlStatementObservation.StatementKind.SELECT
    );
    assertThat(observation.containsSqlFragment(probe.literal())).isTrue();
    assertThat(observation.normalizedFingerprints()).singleElement().asString().hasSize(64);
    List<String> publishedSurfaces = List.of(
      dataSource.toString(),
      connectionRendering,
      statementRendering,
      observation.toString(),
      observation.statementKinds().toString(),
      observation.normalizedFingerprints().toString(),
      observation.statements().toString(),
      observation.statements().getFirst().toString()
    );
    InformationSafetyProbeCorpus.assertNoLeaks(publishedSurfaces);
    InformationSafetyEvidenceLedger.assertCovered(
      probe,
      sqlEvidence(
        dataSource,
        connectionRendering,
        statementRendering,
        observation,
        transactionActiveDuringSql
      )
    );
  }

  private static ActualEvidence[] httpEvidence(
    JsonNode bodyNode,
    org.springframework.mock.web.MockHttpServletResponse response,
    String body,
    boolean transactionActive
  ) {
    List<String> bodyFields = iterable(bodyNode.fieldNames()).stream().sorted().toList();
    List<String> headerFields = response.getHeaderNames().stream().sorted().toList();
    String statusAndCode = "HTTP " + response.getStatus() + " / " + bodyNode.path("code").asText();
    String phase = "before provider work and outside a provider transaction";
    return new ActualEvidence[] {
      new ActualEvidence(
        Surface.HTTP_RESPONSE_BODY,
        ActualValue.observed(statusAndCode),
        ActualValue.structurallyAbsent(
          "an HTTP response body is structurally not a log event",
          bodyFields
            .stream()
            .noneMatch(name -> name.equals("category.event") || name.equals("event"))
        ),
        ActualValue.structurallyAbsent(
          "an HTTP response body has no log level",
          !bodyFields.contains("level")
        ),
        ActualValue.observed(String.join(",", bodyFields)),
        ActualValue.observedWhen(
          "absent; nested injected cause is not serialized",
          bodyFields
            .stream()
            .noneMatch(name ->
              Set.of("cause", "exception", "stackTrace", "suppressed", "throwable").contains(name)
            ),
          "HTTP body contains no throwable field"
        ),
        ActualValue.observedWhen(
          phase,
          !transactionActive,
          "HTTP dispatch is outside a transaction"
        ),
        List.of(body)
      ),
      new ActualEvidence(
        Surface.HTTP_RESPONSE_HEADERS,
        ActualValue.observed(statusAndCode),
        ActualValue.structurallyAbsent(
          "HTTP response headers are structurally not a log event",
          response.getHeader("category.event") == null
        ),
        ActualValue.structurallyAbsent(
          "HTTP response headers have no log level",
          response.getHeader("level") == null
        ),
        ActualValue.observed(String.join(",", headerFields)),
        ActualValue.structurallyAbsent(
          "HTTP headers have no throwable slot",
          lacksNamedMember(response.getClass(), "throwable")
        ),
        ActualValue.observedWhen(
          phase,
          !transactionActive,
          "HTTP dispatch is outside a transaction"
        ),
        headerSurfaces(response)
      ),
    };
  }

  private static ActualEvidence[] genericLogEvidence(StructuredLogCapture.CapturedEvent event) {
    ActualValue status = ActualValue.structurallyAbsent(
      "a generic application log is structurally not an HTTP/startup status",
      lacksNamedMember(event.getClass(), "status")
    );
    ActualValue eventName = ActualValue.observed(structuredFields(event).get("category.event"));
    ActualValue level = ActualValue.observed(event.level().toString());
    ActualValue absentThrowable = ActualValue.observedWhen(
      "absent",
      event.attachedThrowable().isEmpty(),
      "generic log has no attached throwable"
    );
    ActualValue phase = ActualValue.observedWhen(
      "before provider work and outside a transaction",
      !event.transactionActive(),
      "generic log is outside a transaction"
    );
    return new ActualEvidence[] {
      new ActualEvidence(
        Surface.GENERIC_LOG_MESSAGE,
        status,
        eventName,
        level,
        ActualValue.observed("message=" + event.message()),
        absentThrowable,
        phase,
        List.of(event.message())
      ),
      new ActualEvidence(
        Surface.GENERIC_LOG_FORMATTED_MESSAGE,
        status,
        eventName,
        level,
        ActualValue.observed("formattedMessage=" + event.formattedMessage()),
        absentThrowable,
        phase,
        List.of(event.formattedMessage())
      ),
      new ActualEvidence(
        Surface.GENERIC_LOG_ARGUMENTS,
        status,
        eventName,
        level,
        ActualValue.observedWhen(
          "exactly no logger arguments",
          event.arguments().isEmpty(),
          "generic log arguments are empty"
        ),
        absentThrowable,
        phase,
        event.arguments()
      ),
      new ActualEvidence(
        Surface.GENERIC_LOG_KEY_VALUES,
        status,
        eventName,
        level,
        ActualValue.observed(String.join(",", event.structuredFieldNames())),
        absentThrowable,
        phase,
        structuredFieldSurfaces(event)
      ),
      new ActualEvidence(
        Surface.GENERIC_LOG_MDC,
        status,
        eventName,
        level,
        ActualValue.observed(String.join(",", event.mdc().keySet())),
        absentThrowable,
        phase,
        mapSurfaces(event.mdc())
      ),
      new ActualEvidence(
        Surface.GENERIC_LOG_MARKERS,
        status,
        eventName,
        level,
        ActualValue.observedWhen(
          "exactly no markers",
          event.markers().isEmpty(),
          "generic log markers are empty"
        ),
        absentThrowable,
        phase,
        markerSurfaces(event.markers())
      ),
      new ActualEvidence(
        Surface.GENERIC_LOG_THROWABLE,
        status,
        eventName,
        level,
        ActualValue.observedWhen(
          "exactly no attached throwable fields",
          event.attachedThrowable().isEmpty(),
          "generic log has no throwable fields"
        ),
        ActualValue.observedWhen(
          "absent, including cause and suppressed chains",
          event.attachedThrowable().isEmpty(),
          "generic log has no throwable chain"
        ),
        phase,
        throwableSurfaces(event.attachedThrowable().orElse(null))
      ),
    };
  }

  private static ActualEvidence[] sqlEvidence(
    CountingDataSource dataSource,
    String connectionRendering,
    String statementRendering,
    SqlStatementObservation observation,
    boolean transactionActive
  ) {
    String dataSourceRendering = dataSource.toString();
    String observationRendering = observation.toString();
    String statementSummary = observation.statements().getFirst().toString();
    String fingerprintRendering = observation.normalizedFingerprints().toString();
    ActualValue phase = ActualValue.observedWhen(
      "test-only measurement window outside a transaction",
      !transactionActive,
      "SQL measurement is outside a transaction"
    );
    return new ActualEvidence[] {
      sqlEvidence(
        Surface.SQL_DATASOURCE_RENDERING,
        DataSource.class,
        "redacted datasource rendering",
        dataSourceRendering.equals("CountingDataSource[delegate=redacted]"),
        phase,
        List.of(dataSourceRendering)
      ),
      sqlEvidence(
        Surface.SQL_CONNECTION_RENDERING,
        Connection.class,
        "redacted connection rendering",
        connectionRendering.endsWith("[delegate=redacted]"),
        phase,
        List.of(connectionRendering)
      ),
      sqlEvidence(
        Surface.SQL_STATEMENT_RENDERING,
        Statement.class,
        "redacted JDBC statement rendering",
        statementRendering.endsWith("[delegate=redacted]"),
        phase,
        List.of(statementRendering)
      ),
      sqlEvidence(
        Surface.SQL_OBSERVATION_RENDERING,
        SqlStatementObservation.class,
        "window,statementCount,statementKinds,normalizedFingerprints",
        Stream.of(
          "window=",
          "statementCount=",
          "statementKinds=",
          "normalizedFingerprints="
        ).allMatch(observationRendering::contains),
        phase,
        List.of(observationRendering)
      ),
      sqlEvidence(
        Surface.SQL_STATEMENT_SUMMARY_RENDERING,
        SqlStatementObservation.Statement.class,
        "kind,fingerprints,batchSize,sqlShapeCount",
        Stream.of("kind=", "fingerprints=", "batchSize=", "sqlShapeCount=").allMatch(
          statementSummary::contains
        ),
        phase,
        List.of(observation.statements().toString(), statementSummary)
      ),
      sqlEvidence(
        Surface.SQL_FINGERPRINT_RENDERING,
        String.class,
        "one SHA-256 fingerprint",
        observation.normalizedFingerprints().size() == 1 &&
          observation.normalizedFingerprints().getFirst().length() == 64,
        phase,
        List.of(fingerprintRendering)
      ),
    };
  }

  private static ActualEvidence sqlEvidence(
    Surface surface,
    Class<?> renderedType,
    String fieldObservation,
    boolean fieldFact,
    ActualValue phase,
    List<String> renderedSurfaces
  ) {
    String surfaceName = surface.name().toLowerCase(Locale.ROOT);
    String article = surface == Surface.SQL_OBSERVATION_RENDERING ? "an " : "a ";
    return new ActualEvidence(
      surface,
      ActualValue.structurallyAbsent(
        "a test SQL capture is structurally not an HTTP/startup status",
        lacksNamedMember(renderedType, "status")
      ),
      ActualValue.structurallyAbsent(
        article + sqlSurfaceKind(surface) + " is structurally not a log event",
        lacksNamedMember(renderedType, "event")
      ),
      ActualValue.structurallyAbsent(
        article + sqlSurfaceKind(surface) + " has no log level",
        lacksNamedMember(renderedType, "level")
      ),
      ActualValue.observedWhen(fieldObservation, fieldFact, surfaceName + " safe shape"),
      ActualValue.structurallyAbsent(
        article + sqlSurfaceKind(surface) + " has no throwable slot",
        lacksNamedMember(renderedType, "throwable")
      ),
      phase,
      renderedSurfaces
    );
  }

  private static String sqlSurfaceKind(Surface surface) {
    return switch (surface) {
      case SQL_DATASOURCE_RENDERING -> "datasource rendering";
      case SQL_CONNECTION_RENDERING -> "connection rendering";
      case SQL_STATEMENT_RENDERING -> "statement rendering";
      case SQL_OBSERVATION_RENDERING -> "observation rendering";
      case SQL_STATEMENT_SUMMARY_RENDERING -> "statement summary";
      case SQL_FINGERPRINT_RENDERING -> "fingerprint rendering";
      default -> throw new IllegalArgumentException("not a SQL surface");
    };
  }

  private static List<String> headerSurfaces(
    org.springframework.mock.web.MockHttpServletResponse response
  ) {
    List<String> surfaces = new ArrayList<>();
    response.getHeaderNames().forEach(name -> {
      surfaces.add(name);
      surfaces.addAll(response.getHeaders(name));
    });
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

  private static List<String> mapSurfaces(Map<String, String> values) {
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

  private static List<String> throwableSurfaces(StructuredLogCapture.ThrowableSnapshot throwable) {
    if (throwable == null) {
      return List.of();
    }
    List<String> surfaces = new ArrayList<>();
    addThrowableSurfaces(throwable, surfaces);
    return List.copyOf(surfaces);
  }

  private static void addThrowableSurfaces(
    StructuredLogCapture.ThrowableSnapshot throwable,
    List<String> surfaces
  ) {
    surfaces.add(throwable.className());
    surfaces.add(throwable.message());
    surfaces.add(throwable.overridingMessage());
    surfaces.addAll(throwable.stackTrace());
    throwable.suppressed().forEach(value -> addThrowableSurfaces(value, surfaces));
    throwable.causeOptional().ifPresent(value -> addThrowableSurfaces(value, surfaces));
  }

  private static boolean lacksNamedMember(Class<?> type, String token) {
    String normalized = token.toLowerCase(Locale.ROOT);
    return Stream.concat(
      java.util.Arrays.stream(type.getDeclaredFields()).map(java.lang.reflect.Field::getName),
      java.util.Arrays.stream(type.getMethods()).map(Method::getName)
    ).noneMatch(name -> name.toLowerCase(Locale.ROOT).contains(normalized));
  }

  private static Stream<Probe> probes() {
    return InformationSafetyProbeCorpus.probes().stream();
  }

  private static RequestPostProcessor readJwt(String systemKey) {
    return jwt().authorities(new SimpleGrantedAuthority("admin:" + systemKey + "-categories:read"));
  }

  private static List<String> responseSurfaces(
    String body,
    org.springframework.mock.web.MockHttpServletResponse response
  ) {
    List<String> surfaces = new ArrayList<>();
    surfaces.add(body);
    response.getHeaderNames().forEach(header -> {
      surfaces.add(header);
      surfaces.addAll(response.getHeaders(header));
    });
    return List.copyOf(surfaces);
  }

  private static List<String> iterable(java.util.Iterator<String> values) {
    List<String> result = new ArrayList<>();
    values.forEachRemaining(result::add);
    return List.copyOf(result);
  }

  private static Map<String, String> structuredFields(StructuredLogCapture.CapturedEvent event) {
    Map<String, String> fields = new LinkedHashMap<>();
    event.structuredFields().forEach(field -> fields.put(field.key(), field.value()));
    return Map.copyOf(fields);
  }

  private static DataSource fakeDataSource() {
    return proxy(DataSource.class, (proxy, method, arguments) ->
      method.getName().equals("getConnection")
        ? fakeConnection()
        : defaultValue(method.getReturnType())
    );
  }

  private static Connection fakeConnection() {
    return proxy(Connection.class, (proxy, method, arguments) ->
      method.getName().equals("createStatement")
        ? fakeStatement()
        : defaultValue(method.getReturnType())
    );
  }

  private static Statement fakeStatement() {
    return proxy(Statement.class, (proxy, method, arguments) ->
      defaultValue(method.getReturnType())
    );
  }

  private static <T> T proxy(Class<T> type, InvocationHandler delegate) {
    return type.cast(
      Proxy.newProxyInstance(
        type.getClassLoader(),
        new Class<?>[] { type },
        new SafeObjectMethodHandler(delegate)
      )
    );
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == int.class) {
      return 0;
    }
    return null;
  }

  private record SafeObjectMethodHandler(InvocationHandler delegate) implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "equals" -> proxy == arguments[0];
          case "hashCode" -> System.identityHashCode(proxy);
          case "toString" -> "InformationSafetyJdbcProxy[delegate=redacted]";
          default -> throw new UnsupportedOperationException(method.getName());
        };
      }
      return delegate.invoke(proxy, method, arguments);
    }
  }
}
