package com.ferozfaiz.uniapi.category.api;

import static com.ferozfaiz.uniapi.category.contract.AdminCategoryContractAssertions.assertMatrixManifest;
import static com.ferozfaiz.uniapi.category.contract.AdminCategoryOperationMatrixCase.generateDeterministic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.authorization.AdminCategorySystemAuthorizationPolicy;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.DeletionPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.GeneralValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.HierarchyPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.LifecyclePolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.MutationValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.OrderingPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.QueryPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOperationPort;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryOperationMatrixCase;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyProbeCorpus;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture.Harness;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AdminCategoryController.class)
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  AdminCategoryApiExceptionHandler.class,
  AdminCategorySystemAuthorizationPolicy.class,
  AdminCategoryAuthorizationPrecedenceMatrixTest.ActualRuntimeConfiguration.class,
})
class AdminCategoryAuthorizationPrecedenceMatrixTest {

  private static final String FULL_SYSTEM = "catalogue";
  private static final String OTHER_SYSTEM = "descriptor-a";
  private static final String RESTRICTED_SYSTEM = "neutral-restricted";
  private static final String FULL_PATH =
    "/api/v1/admin/category-systems/" + FULL_SYSTEM + "/categories";
  private static final String RESTRICTED_PATH =
    "/api/v1/admin/category-systems/" + RESTRICTED_SYSTEM + "/categories";

  private static final List<Operation> OPERATIONS = List.of(
    Operation.CAPABILITIES,
    Operation.LIST,
    Operation.TREE,
    Operation.PARENT_OPTIONS,
    Operation.GET,
    Operation.BREADCRUMB,
    Operation.CREATE,
    Operation.REPLACE,
    Operation.PATCH,
    Operation.DELETE,
    Operation.MOVE,
    Operation.REORDER,
    Operation.ACTIVATE,
    Operation.DEACTIVATE
  );
  private static final Set<Operation> READ_OPERATIONS = Set.of(
    Operation.CAPABILITIES,
    Operation.LIST,
    Operation.TREE,
    Operation.PARENT_OPTIONS,
    Operation.GET,
    Operation.BREADCRUMB
  );
  private static final List<AuthorityState> FULL_AUTHORITY_STATES = List.of(
    AuthorityState.UNAUTHENTICATED,
    AuthorityState.EXACT,
    AuthorityState.NONE,
    AuthorityState.OTHER_SYSTEM,
    AuthorityState.READ_ONLY,
    AuthorityState.WRITE_ONLY,
    AuthorityState.LEGACY_GLOBAL,
    AuthorityState.SCOPE_ONLY,
    AuthorityState.UNRELATED,
    AuthorityState.ROLE_ADMIN,
    AuthorityState.ROLE_MODERATOR
  );
  private static final Set<Operation> RESTRICTED_DECLARATIONS = Set.of(
    Operation.CAPABILITIES,
    Operation.LIST,
    Operation.CREATE
  );
  private static final List<PrecedenceState> RESTRICTED_STATES = List.of(
    PrecedenceState.UNAUTHENTICATED,
    PrecedenceState.MISSING_PERMISSION,
    PrecedenceState.PRELIMINARY_PERMISSION
  );

  private static final List<String> DENIED_FORBIDDEN = List.of(
    "application",
    "capability",
    "definition-validation",
    "codec",
    "cursor",
    "resource",
    "provider",
    "repository",
    "sql",
    "lock",
    "event",
    "mutation",
    "fallback"
  );
  private static final List<String> SUCCESS_FORBIDDEN = List.of(
    "non-mapped-port",
    "other-registration",
    "other-provider",
    "legacy-registry-source",
    "storage-registry",
    "fallback"
  );
  private static final List<String> UNSUPPORTED_FORBIDDEN = List.of(
    "definition-validation",
    "codec",
    "cursor",
    "resource",
    "provider",
    "repository",
    "sql",
    "lock",
    "event",
    "mutation",
    "legacy-registry-source",
    "fallback"
  );

  @Autowired
  private MockMvc mvc;

  @Autowired
  private MatrixRuntime runtime;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ApplicationContext applicationContext;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void clearRuntimeObservations() {
    runtime.clearObservations();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fullAuthorizationCases")
  void fullProfileAuthorizationMatrixExercisesHttpSecurityApplicationAndExactPorts(MatrixCell cell)
    throws Exception {
    MockHttpServletRequestBuilder request = requestFor(FULL_PATH, cell.operation());
    if (cell.authorityState() != AuthorityState.UNAUTHENTICATED) {
      request.with(authorityFor(cell.operation(), cell.authorityState()));
    }

    org.springframework.test.web.servlet.MvcResult result;
    List<StructuredLogCapture.CapturedEvent> logs;
    try (
      StructuredLogCapture applicationLogs = StructuredLogCapture.attach(
        AdminCategoryApplicationService.class
      );
      StructuredLogCapture authorizationLogs = StructuredLogCapture.attach(
        AdminCategorySystemAuthorizationPolicy.class
      )
    ) {
      result = mvc.perform(request).andExpect(status().is(cell.expectedStatus())).andReturn();
      logs = Stream.concat(
        applicationLogs.events().stream(),
        authorizationLogs.events().stream()
      ).toList();
    }
    if (!"none".equals(cell.expectedStableError())) {
      assertThat(
        objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asText()
      ).isEqualTo(cell.expectedStableError());
    } else {
      assertThat(result.getResponse().getStatus()).isEqualTo(cell.expectedStatus());
    }

    boolean success = cell.expectedStableError().equals("none");
    assertApplicationCalls(
      runtime.service(),
      success ? applicationCalls(cell.operation()) : List.of()
    );
    assertProviderCalls(runtime.full(), success ? cell.expectedPortCalls() : List.of());
    assertNoProviderCalls(runtime.other());
    assertNoProviderCalls(runtime.restricted());
    assertSelectedRegistry(FULL_SYSTEM, cell.authorityState() == AuthorityState.UNAUTHENTICATED);
    assertExpectedLogOutcome(cell, logs);
    assertForbiddenInteractions(cell, runtime.full(), logs);
    assertSafeErrorSurface(cell, result.getResponse().getContentAsString(), logs);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("restrictedUnsupportedPrecedenceCases")
  void restrictedUnsupportedPrecedenceStopsAtTheExactWinningSeam(MatrixCell cell) throws Exception {
    MockHttpServletRequestBuilder request = restrictedRequest(cell.operation());
    if (cell.precedenceState() != PrecedenceState.UNAUTHENTICATED) {
      request.with(restrictedAuthority(cell.operation(), cell.precedenceState()));
    }

    org.springframework.test.web.servlet.MvcResult result;
    List<StructuredLogCapture.CapturedEvent> logs;
    try (
      StructuredLogCapture applicationLogs = StructuredLogCapture.attach(
        AdminCategoryApplicationService.class
      );
      StructuredLogCapture authorizationLogs = StructuredLogCapture.attach(
        AdminCategorySystemAuthorizationPolicy.class
      )
    ) {
      result = mvc
        .perform(request)
        .andExpect(status().is(cell.expectedStatus()))
        .andExpect(jsonPath("$.code").value(cell.expectedStableError()))
        .andReturn();
      logs = Stream.concat(
        applicationLogs.events().stream(),
        authorizationLogs.events().stream()
      ).toList();
    }

    if (cell.precedenceState() == PrecedenceState.PRELIMINARY_PERMISSION) {
      assertApplicationCalls(runtime.service(), restrictedApplicationCalls(cell.operation()));
    } else {
      assertApplicationCalls(runtime.service(), List.of());
    }
    assertNoProviderCalls(runtime.restricted());
    assertNoProviderCalls(runtime.full());
    assertNoProviderCalls(runtime.other());
    assertSelectedRegistry(
      RESTRICTED_SYSTEM,
      cell.precedenceState() == PrecedenceState.UNAUTHENTICATED
    );
    assertExpectedLogOutcome(cell, logs);
    assertForbiddenInteractions(cell, runtime.restricted(), logs);
    assertSafeErrorSurface(cell, result.getResponse().getContentAsString(), logs);
  }

  private void assertSelectedRegistry(String expectedSystemKey, boolean noLookupExpected) {
    List<String> lookupKeys = registryLookupKeys();
    if (noLookupExpected) {
      assertThat(lookupKeys).isEmpty();
    } else {
      assertThat(lookupKeys).isNotEmpty().allMatch(expectedSystemKey::equals);
    }
  }

  private void assertExpectedLogOutcome(
    MatrixCell cell,
    List<StructuredLogCapture.CapturedEvent> logs
  ) {
    List<StructuredLogCapture.CapturedEvent> applicationLogs = logsFrom(
      logs,
      AdminCategoryApplicationService.class
    );
    List<StructuredLogCapture.CapturedEvent> authorizationLogs = logsFrom(
      logs,
      AdminCategorySystemAuthorizationPolicy.class
    );
    assertThat(logs).hasSize(applicationLogs.size() + authorizationLogs.size());

    switch (cell.expectedLogOutcome()) {
      case "no-application-log", "safe-access-security-only" -> assertThat(logs).isEmpty();
      case "no-generic-category-or-unsupported-log" -> {
        assertThat(applicationLogs).isEmpty();
        assertThat(authorizationLogs).hasSize(1);
        var event = authorizationLogs.getFirst();
        assertThat(event.level()).isEqualTo(Level.WARN);
        assertThat(structuredFields(event))
          .containsEntry("category.event", "authorization.denied")
          .containsEntry(
            "systemKey",
            cell.authorityState() == null ? RESTRICTED_SYSTEM : FULL_SYSTEM
          )
          .containsEntry("operation", cell.operation().code())
          .containsEntry("outcome", "denied")
          .containsEntry("errorCode", "ACCESS_DENIED");
      }
      case "frozen-mutation-success" -> {
        assertThat(authorizationLogs).isEmpty();
        assertThat(applicationLogs).hasSize(1);
        var event = applicationLogs.getFirst();
        assertThat(event.level()).isEqualTo(Level.INFO);
        assertThat(structuredFields(event))
          .containsEntry("systemKey", FULL_SYSTEM)
          .containsEntry("outcome", "succeeded")
          .containsEntry("categoryId", "10");
      }
      case "one-bounded-capability-unsupported-warn" -> {
        assertThat(authorizationLogs).isEmpty();
        assertThat(applicationLogs).hasSize(1);
        var event = applicationLogs.getFirst();
        assertThat(event.level()).isEqualTo(Level.WARN);
        assertThat(structuredFields(event))
          .containsEntry("category.event", "capability.unsupported")
          .containsEntry("systemKey", RESTRICTED_SYSTEM)
          .containsEntry("operation", cell.operation().code())
          .containsEntry("outcome", "failed")
          .containsEntry("errorCode", "CATEGORY_CAPABILITY_UNSUPPORTED");
      }
      default -> throw new AssertionError("unexecuted log outcome " + cell.expectedLogOutcome());
    }
  }

  private static List<StructuredLogCapture.CapturedEvent> logsFrom(
    List<StructuredLogCapture.CapturedEvent> logs,
    Class<?> loggerType
  ) {
    return logs
      .stream()
      .filter(event -> event.loggerName().equals(loggerType.getName()))
      .toList();
  }

  private void assertForbiddenInteractions(
    MatrixCell cell,
    Harness selected,
    List<StructuredLogCapture.CapturedEvent> logs
  ) {
    List<String> applicationCalls = invocationNames(runtime.service());
    List<String> selectedPortCalls = providerInvocationNames(selected);
    List<String> otherPortCalls = Stream.of(runtime.full(), runtime.other(), runtime.restricted())
      .filter(harness -> harness != selected)
      .flatMap(harness -> providerInvocationNames(harness).stream())
      .toList();
    List<String> lookupKeys = registryLookupKeys();
    boolean codecCalled = !invocationNames(
      selected.registration().runtimeBinding().orElseThrow().identifierCodec()
    ).isEmpty();

    for (String forbidden : cell.forbiddenInteractions()) {
      boolean observed = switch (forbidden) {
        case "application", "capability" -> !applicationCalls.isEmpty();
        case "definition-validation" -> definitionValidationObserved(selected);
        case "codec" -> codecCalled;
        case
          "cursor",
          "resource",
          "repository",
          "sql",
          "lock",
          "event" -> !selectedPortCalls.isEmpty();
        case "provider" -> !selectedPortCalls.isEmpty();
        case "mutation" -> selectedPortCalls
          .stream()
          .anyMatch(
            call -> !Set.of("list", "tree", "parentOptions", "getNode", "breadcrumb").contains(call)
          );
        case "non-mapped-port" -> !selectedPortCalls.equals(cell.expectedPortCalls());
        case "other-registration" -> lookupKeys
          .stream()
          .anyMatch(key -> !selected.definition().systemKey().equals(key));
        case "other-provider" -> !otherPortCalls.isEmpty();
        case "legacy-registry-source", "fallback" -> !otherPortCalls.isEmpty();
        case "storage-registry" -> !applicationContext
          .getBeansOfType(CategorySystemRegistry.class)
          .isEmpty();
        default -> throw new AssertionError("unexecuted forbidden interaction " + forbidden);
      };
      assertThat(observed)
        .as(cell.id() + " forbids " + forbidden)
        .isFalse();
    }
    assertThat(logs).allSatisfy(event -> assertThat(event.attachedThrowable()).isEmpty());
  }

  private static void assertSafeErrorSurface(
    MatrixCell cell,
    String responseBody,
    List<StructuredLogCapture.CapturedEvent> logs
  ) {
    if (!cell.expectedStableError().equals("none")) {
      InformationSafetyProbeCorpus.assertNoLeaks(responseBody);
      assertThat(responseBody)
        .doesNotContain("not-base64!")
        .doesNotContain("beforeId")
        .doesNotContain("afterId")
        .doesNotContain("targetIndex")
        .doesNotContain("admin:catalogue-categories")
        .doesNotContain("admin:descriptor-a-categories");
    }
    InformationSafetyProbeCorpus.assertNoLeaks(
      logs
        .stream()
        .flatMap(event -> event.searchableSurfaces().stream())
        .toList()
    );
  }

  private List<String> registryLookupKeys() {
    return mockingDetails(runtime.registry())
      .getInvocations()
      .stream()
      .filter(invocation -> invocation.getMethod().getName().equals("findRegistration"))
      .map(invocation -> (String) invocation.getArgument(0))
      .toList();
  }

  private static Map<String, String> structuredFields(StructuredLogCapture.CapturedEvent event) {
    return event
      .structuredFields()
      .stream()
      .collect(
        java.util.stream.Collectors.toUnmodifiableMap(
          StructuredLogCapture.StructuredField::key,
          StructuredLogCapture.StructuredField::value
        )
      );
  }

  private static Stream<Arguments> fullAuthorizationCases() {
    List<AdminCategoryOperationMatrixCase> manifest = generateDeterministic(
      OPERATIONS,
      FULL_AUTHORITY_STATES,
      (operation, state) -> fullAuthorizationCell(operation, state).toContractCase()
    );
    assertMatrixManifest("full-profile authorization", manifest, 154);
    List<MatrixCell> cells = manifest
      .stream()
      .map(row ->
        fullAuthorizationCell(row.operation(), AuthorityState.fromCode(row.authorityState()))
      )
      .toList();

    assertThat(OPERATIONS).hasSize(14).doesNotHaveDuplicates();
    assertThat(READ_OPERATIONS).hasSize(6);
    assertThat(OPERATIONS)
      .filteredOn(operation -> !READ_OPERATIONS.contains(operation))
      .hasSize(8);
    assertCompleteManifest(cells, 154, "AUTH-");
    assertThat(cells)
      .extracting(MatrixCell::id)
      .containsExactlyInAnyOrderElementsOf(
        OPERATIONS.stream()
          .flatMap(operation ->
            FULL_AUTHORITY_STATES.stream().map(
              state -> "AUTH-" + code(operation) + "-" + state.code
            )
          )
          .toList()
      );
    for (Operation operation : OPERATIONS) {
      assertThat(cells)
        .filteredOn(cell -> cell.operation() == operation)
        .hasSize(11);
    }
    for (AuthorityState state : FULL_AUTHORITY_STATES) {
      assertThat(cells)
        .filteredOn(cell -> cell.authorityState() == state)
        .hasSize(14);
    }
    return cells.stream().map(Arguments::of);
  }

  private static Stream<Arguments> restrictedUnsupportedPrecedenceCases() {
    List<Operation> undeclared = OPERATIONS.stream()
      .filter(operation -> !RESTRICTED_DECLARATIONS.contains(operation))
      .toList();
    List<AdminCategoryOperationMatrixCase> manifest = generateDeterministic(
      undeclared,
      RESTRICTED_STATES,
      (operation, state) -> restrictedCell(operation, state).toContractCase()
    );
    assertMatrixManifest("restricted unsupported precedence", manifest, 33);
    List<MatrixCell> cells = manifest
      .stream()
      .map(row -> restrictedCell(row.operation(), PrecedenceState.fromCode(row.authorityState())))
      .toList();

    assertThat(RESTRICTED_DECLARATIONS).containsExactlyInAnyOrder(
      Operation.CAPABILITIES,
      Operation.LIST,
      Operation.CREATE
    );
    assertThat(undeclared).hasSize(11).doesNotHaveDuplicates();
    assertCompleteManifest(cells, 33, "RUP-");
    assertThat(cells)
      .extracting(MatrixCell::id)
      .containsExactlyInAnyOrderElementsOf(
        undeclared
          .stream()
          .flatMap(operation ->
            RESTRICTED_STATES.stream().map(state -> "RUP-" + code(operation) + "-" + state.code)
          )
          .toList()
      );
    for (Operation operation : undeclared) {
      assertThat(cells)
        .filteredOn(cell -> cell.operation() == operation)
        .hasSize(3);
    }
    for (PrecedenceState state : RESTRICTED_STATES) {
      assertThat(cells)
        .filteredOn(cell -> cell.precedenceState() == state)
        .hasSize(11);
    }
    return cells.stream().map(Arguments::of);
  }

  private static MatrixCell fullAuthorizationCell(Operation operation, AuthorityState state) {
    boolean success = switch (state) {
      case EXACT -> true;
      case READ_ONLY -> READ_OPERATIONS.contains(operation);
      case WRITE_ONLY -> !READ_OPERATIONS.contains(operation);
      case
        UNAUTHENTICATED,
        NONE,
        OTHER_SYSTEM,
        LEGACY_GLOBAL,
        SCOPE_ONLY,
        UNRELATED,
        ROLE_ADMIN,
        ROLE_MODERATOR -> false;
    };
    boolean unauthenticated = state == AuthorityState.UNAUTHENTICATED;
    return new MatrixCell(
      "AUTH-" + code(operation) + "-" + state.code,
      "FULL/catalogue",
      operation,
      true,
      unauthenticated ? "unauthenticated" : "authenticated",
      state,
      null,
      "V(" + operation.code() + ")",
      unauthenticated ? 401 : success ? successfulStatus(operation) : 403,
      unauthenticated ? "UNAUTHENTICATED" : success ? "none" : "FORBIDDEN",
      success ? successfulPortCalls(operation) : List.of(),
      success ? SUCCESS_FORBIDDEN : DENIED_FORBIDDEN,
      success
        ? (READ_OPERATIONS.contains(operation) ? "no-application-log" : "frozen-mutation-success")
        : unauthenticated
          ? "safe-access-security-only"
          : "no-generic-category-or-unsupported-log",
      success
        ? "declared operation and exact selected permission class"
        : "authentication or selected-system authorization wins before application dispatch"
    );
  }

  private static MatrixCell restrictedCell(Operation operation, PrecedenceState state) {
    boolean unauthenticated = state == PrecedenceState.UNAUTHENTICATED;
    boolean permitted = state == PrecedenceState.PRELIMINARY_PERMISSION;
    return new MatrixCell(
      "RUP-" + code(operation) + "-" + state.code,
      "RST/neutral-restricted",
      operation,
      false,
      unauthenticated ? "unauthenticated" : "authenticated",
      null,
      state,
      restrictedInput(operation),
      unauthenticated ? 401 : permitted ? 409 : 403,
      unauthenticated
        ? "UNAUTHENTICATED"
        : permitted
          ? "CATEGORY_CAPABILITY_UNSUPPORTED"
          : "FORBIDDEN",
      List.of(),
      permitted ? UNSUPPORTED_FORBIDDEN : DENIED_FORBIDDEN,
      permitted
        ? "one-bounded-capability-unsupported-warn"
        : unauthenticated
          ? "safe-access-security-only"
          : "no-generic-category-or-unsupported-log",
      permitted
        ? "undeclared capability wins before the competing later-stage input"
        : "authentication or preliminary authorization wins before capability disclosure"
    );
  }

  private static void assertCompleteManifest(
    List<MatrixCell> cells,
    int expectedSize,
    String idPrefix
  ) {
    assertThat(cells).hasSize(expectedSize);
    assertThat(cells)
      .extracting(MatrixCell::id)
      .doesNotHaveDuplicates()
      .allMatch(id -> id.startsWith(idPrefix));
    assertThat(cells).allSatisfy(cell -> {
      assertThat(cell.profileOrSystem()).isNotBlank();
      assertThat(cell.operation()).isNotNull();
      assertThat(cell.authenticationState()).isNotBlank();
      assertThat(cell.inputCondition()).isNotBlank();
      assertThat(cell.expectedStatus()).isIn(200, 201, 204, 401, 403, 409);
      assertThat(cell.expectedStableError()).isNotBlank().isNotEqualToIgnoringCase("N/A");
      assertThat(cell.expectedPortCalls()).doesNotContain("N/A");
      assertThat(cell.forbiddenInteractions()).isNotEmpty().doesNotContain("N/A");
      assertThat(cell.expectedLogOutcome()).isNotBlank().isNotEqualToIgnoringCase("N/A");
      assertThat(cell.applicabilityEvidence()).isNotBlank().isNotEqualToIgnoringCase("N/A");
    });
  }

  private static MockHttpServletRequestBuilder requestFor(String basePath, Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> get(basePath + "/capabilities");
      case LIST -> get(basePath);
      case TREE -> get(basePath + "/tree");
      case PARENT_OPTIONS -> get(basePath + "/parent-options");
      case GET -> get(basePath + "/10");
      case BREADCRUMB -> get(basePath + "/10/breadcrumb");
      case CREATE -> post(basePath)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentId\":2,\"name\":\"Storage\",\"slug\":\"storage\",\"active\":true}");
      case REPLACE -> put(basePath + "/10")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentId\":2,\"name\":\"Storage\",\"slug\":\"storage\",\"active\":true}");
      case PATCH -> patch(basePath + "/10")
        .contentType("application/merge-patch+json")
        .content("{\"name\":\"Updated\"}");
      case DELETE -> delete(basePath + "/10");
      case MOVE -> post(basePath + "/10/move")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"newParentId\":2}");
      case REORDER -> post(basePath + "/10/reorder")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentId\":2,\"targetIndex\":0}");
      case ACTIVATE -> post(basePath + "/10/activate");
      case DEACTIVATE -> post(basePath + "/10/deactivate");
    };
  }

  private static MockHttpServletRequestBuilder restrictedRequest(Operation operation) {
    return switch (operation) {
      case TREE -> get(RESTRICTED_PATH + "/tree?size=10001");
      case PARENT_OPTIONS -> get(RESTRICTED_PATH + "/parent-options?cursor=not-base64!");
      case GET -> get(RESTRICTED_PATH + "/0");
      case BREADCRUMB -> get(RESTRICTED_PATH + "/0/breadcrumb");
      case REPLACE -> put(RESTRICTED_PATH + "/0")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentId\":null,\"name\":\"X\",\"slug\":\"x\",\"active\":true}");
      case PATCH -> patch(RESTRICTED_PATH + "/0")
        .contentType("application/merge-patch+json")
        .content("{\"name\":\"x\"}");
      case DELETE -> delete(RESTRICTED_PATH + "/0");
      case MOVE -> post(RESTRICTED_PATH + "/0/move")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"newParentId\":2,\"beforeId\":3,\"afterId\":4}");
      case REORDER -> post(RESTRICTED_PATH + "/0/reorder")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentId\":2,\"beforeId\":3,\"afterId\":4}");
      case ACTIVATE -> post(RESTRICTED_PATH + "/0/activate");
      case DEACTIVATE -> post(RESTRICTED_PATH + "/0/deactivate");
      case CAPABILITIES, LIST, CREATE -> throw new IllegalArgumentException(
        "declared restricted operation has no unsupported-precedence row"
      );
    };
  }

  private static RequestPostProcessor authorityFor(Operation operation, AuthorityState state) {
    boolean read = READ_OPERATIONS.contains(operation);
    String selected = permission(FULL_SYSTEM, read);
    String authority = switch (state) {
      case EXACT -> selected;
      case NONE -> null;
      case OTHER_SYSTEM -> permission(OTHER_SYSTEM, read);
      case READ_ONLY -> permission(FULL_SYSTEM, true);
      case WRITE_ONLY -> permission(FULL_SYSTEM, false);
      case LEGACY_GLOBAL -> read ? "admin:categories:read" : "admin:categories:write";
      case SCOPE_ONLY -> "SCOPE_" + selected;
      case UNRELATED -> "unrelated:permission";
      case ROLE_ADMIN -> "ROLE_ADMIN";
      case ROLE_MODERATOR -> "ROLE_MODERATOR";
      case UNAUTHENTICATED -> throw new IllegalArgumentException(
        "unauthenticated cells must not install a JWT"
      );
    };
    GrantedAuthority[] authorities =
      authority == null
        ? new GrantedAuthority[0]
        : new GrantedAuthority[] { new SimpleGrantedAuthority(authority) };
    return jwt().authorities(authorities);
  }

  private static RequestPostProcessor restrictedAuthority(
    Operation operation,
    PrecedenceState state
  ) {
    if (state == PrecedenceState.UNAUTHENTICATED) {
      throw new IllegalArgumentException("unauthenticated cells must not install a JWT");
    }
    if (state == PrecedenceState.MISSING_PERMISSION) {
      return jwt().authorities(new GrantedAuthority[0]);
    }
    return jwt().authorities(
      new SimpleGrantedAuthority(permission(RESTRICTED_SYSTEM, READ_OPERATIONS.contains(operation)))
    );
  }

  private static String permission(String systemKey, boolean read) {
    return "admin:" + systemKey + "-categories:" + (read ? "read" : "write");
  }

  private static int successfulStatus(Operation operation) {
    return switch (operation) {
      case CREATE -> 201;
      case DELETE, MOVE, ACTIVATE, DEACTIVATE -> 204;
      case
        CAPABILITIES,
        LIST,
        TREE,
        PARENT_OPTIONS,
        GET,
        BREADCRUMB,
        REPLACE,
        PATCH,
        REORDER -> 200;
    };
  }

  private static List<String> successfulPortCalls(Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> List.of();
      case LIST -> List.of("list");
      case TREE -> List.of("tree");
      case PARENT_OPTIONS -> List.of("parentOptions");
      case GET -> List.of("getNode");
      case BREADCRUMB -> List.of("breadcrumb");
      case CREATE -> List.of("create");
      case REPLACE -> List.of("replace");
      case PATCH -> List.of("patch");
      case DELETE -> List.of("delete");
      case MOVE -> List.of("move");
      case REORDER -> List.of("reorder");
      case ACTIVATE -> List.of("activate");
      case DEACTIVATE -> List.of("deactivate");
    };
  }

  private static List<String> applicationCalls(Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> List.of("capabilities");
      case LIST -> List.of("definition", "list");
      case TREE -> List.of("definition", "tree");
      case PARENT_OPTIONS -> List.of("definition", "parentOptions");
      case GET -> List.of("get", "getNode");
      case BREADCRUMB -> List.of("breadcrumb");
      case CREATE -> List.of("definition", "create");
      case REPLACE -> List.of("definition", "update");
      case PATCH -> List.of("definition", "patch");
      case DELETE -> List.of("delete");
      case MOVE -> List.of("definition", "move");
      case REORDER -> List.of("definition", "reorder");
      case ACTIVATE -> List.of("activate");
      case DEACTIVATE -> List.of("deactivate");
    };
  }

  private static List<String> restrictedApplicationCalls(Operation operation) {
    return switch (operation) {
      case TREE, PARENT_OPTIONS, REPLACE, PATCH, MOVE, REORDER -> List.of("definition");
      case GET -> List.of("get", "getNode");
      case BREADCRUMB -> List.of("breadcrumb");
      case DELETE -> List.of("delete");
      case ACTIVATE -> List.of("activate");
      case DEACTIVATE -> List.of("deactivate");
      case CAPABILITIES, LIST, CREATE -> throw new IllegalArgumentException(
        "declared restricted operation has no unsupported-precedence row"
      );
    };
  }

  private static String restrictedInput(Operation operation) {
    return switch (operation) {
      case TREE -> "requested size 10001";
      case PARENT_OPTIONS -> "opaque cursor not-base64! and otherwise valid query";
      case GET, BREADCRUMB, DELETE, ACTIVATE, DEACTIVATE -> "numeric path ID 0";
      case REPLACE -> "ID 0 with syntactically valid replace JSON requiring later validation";
      case PATCH -> "ID 0 with syntactically valid merge-patch JSON";
      case MOVE, REORDER -> "ID 0 with two conflicting placement selectors";
      case CAPABILITIES, LIST, CREATE -> throw new IllegalArgumentException(
        "declared restricted operation has no competing input"
      );
    };
  }

  private static String code(Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> "CAP";
      case LIST -> "LST";
      case TREE -> "TRE";
      case PARENT_OPTIONS -> "PAR";
      case GET -> "GET";
      case BREADCRUMB -> "BRD";
      case CREATE -> "CRE";
      case REPLACE -> "REP";
      case PATCH -> "PAT";
      case DELETE -> "DEL";
      case MOVE -> "MOV";
      case REORDER -> "REO";
      case ACTIVATE -> "ACT";
      case DEACTIVATE -> "DEA";
    };
  }

  private static void assertApplicationCalls(
    AdminCategoryApplicationService service,
    List<String> expectedCalls
  ) {
    assertThat(invocationNames(service)).containsExactlyElementsOf(expectedCalls);
  }

  private static void assertProviderCalls(Harness harness, List<String> expectedCalls) {
    assertThat(providerInvocationNames(harness)).containsExactlyElementsOf(expectedCalls);
  }

  private static void assertNoProviderCalls(Harness harness) {
    assertThat(providerInvocationNames(harness)).isEmpty();
  }

  private static List<String> providerInvocationNames(Harness harness) {
    return ports(harness)
      .stream()
      .flatMap(port -> mockingDetails(port).getInvocations().stream())
      .map(invocation -> invocation.getMethod().getName())
      .toList();
  }

  private static List<String> invocationNames(Object mock) {
    return mockingDetails(mock)
      .getInvocations()
      .stream()
      .map(Invocation::getMethod)
      .map(method -> method.getName())
      .toList();
  }

  private static boolean definitionValidationObserved(Harness harness) {
    List<Object> collaborators = harness
      .registration()
      .runtimeBinding()
      .orElseThrow()
      .policyBindings()
      .values()
      .stream()
      .map(AdminCategoryRuntimePolicyBinding::collaborator)
      .map(Object.class::cast)
      .toList();
    assertThat(collaborators)
      .as("definition-validation collaborators must remain observable spies")
      .allMatch(collaborator -> mockingDetails(collaborator).isSpy());
    return collaborators
      .stream()
      .anyMatch(collaborator -> !mockingDetails(collaborator).getInvocations().isEmpty());
  }

  private static List<AdminCategoryOperationPort> ports(Harness harness) {
    return List.of(
      harness.ports().categoryRead(),
      harness.ports().hierarchyRead(),
      harness.ports().basicMutation(),
      harness.ports().hierarchyMutation(),
      harness.ports().ordering(),
      harness.ports().lifecycle()
    );
  }

  private enum AuthorityState {
    UNAUTHENTICATED("U"),
    EXACT("X"),
    NONE("N"),
    OTHER_SYSTEM("O"),
    READ_ONLY("R"),
    WRITE_ONLY("W"),
    LEGACY_GLOBAL("G"),
    SCOPE_ONLY("C"),
    UNRELATED("Q"),
    ROLE_ADMIN("A"),
    ROLE_MODERATOR("M");

    private final String code;

    AuthorityState(String code) {
      this.code = code;
    }

    private static AuthorityState fromCode(String code) {
      return Arrays.stream(values())
        .filter(state -> state.code.equals(code))
        .findFirst()
        .orElseThrow();
    }
  }

  private enum PrecedenceState {
    UNAUTHENTICATED("U"),
    MISSING_PERMISSION("N"),
    PRELIMINARY_PERMISSION("P");

    private final String code;

    PrecedenceState(String code) {
      this.code = code;
    }

    private static PrecedenceState fromCode(String code) {
      return Arrays.stream(values())
        .filter(state -> state.code.equals(code))
        .findFirst()
        .orElseThrow();
    }
  }

  private record MatrixCell(
    String id,
    String profileOrSystem,
    Operation operation,
    boolean declaredCapability,
    String authenticationState,
    AuthorityState authorityState,
    PrecedenceState precedenceState,
    String inputCondition,
    int expectedStatus,
    String expectedStableError,
    List<String> expectedPortCalls,
    List<String> forbiddenInteractions,
    String expectedLogOutcome,
    String applicabilityEvidence
  ) {
    private MatrixCell {
      expectedPortCalls = List.copyOf(expectedPortCalls);
      forbiddenInteractions = List.copyOf(forbiddenInteractions);
    }

    private AdminCategoryOperationMatrixCase toContractCase() {
      return new AdminCategoryOperationMatrixCase(
        profileOrSystem,
        operation,
        declaredCapability,
        authenticationState,
        authorityState != null ? authorityState.code : precedenceState.code,
        inputCondition,
        Integer.toString(expectedStatus),
        expectedStableError,
        expectedPortCalls,
        forbiddenInteractions,
        expectedLogOutcome,
        applicabilityEvidence
      );
    }

    @Override
    public String toString() {
      return id;
    }
  }

  static final class MatrixRuntime {

    private final Harness full = observableHarness(AdminCategoryRuntimeTestFixture.harness());
    private final Harness other = observableHarness(
      AdminCategoryRuntimeTestFixture.harness(
        AdminCategoryRuntimeTestFixture.completeDefinition(OTHER_SYSTEM, Set.of(Operation.values()))
      )
    );
    private final Harness restricted = observableHarness(
      AdminCategoryRuntimeTestFixture.harness(
        AdminCategoryRuntimeTestFixture.completeDefinition(
          RESTRICTED_SYSTEM,
          RESTRICTED_DECLARATIONS
        )
      )
    );
    private final AdminCategorySystemRegistry registry = spy(
      actualRegistry(List.of(full.registration(), other.registration()))
    );
    private final AdminCategoryApplicationService service = spy(
      new AdminCategoryApplicationService(registry)
    );

    MatrixRuntime() {
      doReturn(Optional.of(restricted.registration()))
        .when(registry)
        .findRegistration(RESTRICTED_SYSTEM);
      stubSuccessfulPorts(full);
      clearObservations();
    }

    Harness full() {
      return full;
    }

    Harness other() {
      return other;
    }

    Harness restricted() {
      return restricted;
    }

    AdminCategorySystemRegistry registry() {
      return registry;
    }

    AdminCategoryApplicationService service() {
      return service;
    }

    void clearObservations() {
      clearInvocations(service, registry);
      Stream.of(full, other, restricted).forEach(harness -> {
        ports(harness).forEach(port -> clearInvocations(port));
        clearInvocations(harness.registration().runtimeBinding().orElseThrow().identifierCodec());
        harness
          .registration()
          .runtimeBinding()
          .orElseThrow()
          .policyBindings()
          .values()
          .forEach(binding -> clearInvocations(binding.collaborator()));
      });
    }

    private static Harness observableHarness(Harness source) {
      AdminCategoryRuntimeBinding sourceBinding = source
        .registration()
        .runtimeBinding()
        .orElseThrow();
      AdminCategoryDefinitionValidator validator = spy(
        (AdminCategoryDefinitionValidator) sourceBinding
          .policyBindings()
          .values()
          .getFirst()
          .collaborator()
      );
      List<AdminCategoryRuntimePolicyBinding> policyBindings = sourceBinding
        .policyBindings()
        .values()
        .stream()
        .map(binding -> observablePolicyBinding(binding, validator))
        .toList();
      AdminCategoryRuntimeBinding observableBinding = new AdminCategoryRuntimeBinding(
        sourceBinding.identifierCodec(),
        sourceBinding.permissionMapping(),
        new AdminCategoryRuntimePolicyBindings(policyBindings),
        sourceBinding.ports()
      );
      AdminCategorySystemRegistration observableRegistration = AdminCategorySystemRegistration.of(
        source.definition(),
        observableBinding
      );
      return new Harness(
        source.definition(),
        observableRegistration,
        source.registry(),
        source.service(),
        source.ports()
      );
    }

    private static AdminCategoryRuntimePolicyBinding observablePolicyBinding(
      AdminCategoryRuntimePolicyBinding binding,
      AdminCategoryDefinitionValidator validator
    ) {
      return switch (binding) {
        case GeneralValidationPolicyBinding value -> new GeneralValidationPolicyBinding(
          value.reference(),
          validator
        );
        case QueryPolicyBinding value -> new QueryPolicyBinding(value.reference(), validator);
        case HierarchyPolicyBinding value -> new HierarchyPolicyBinding(
          value.reference(),
          validator
        );
        case MutationValidationPolicyBinding value -> new MutationValidationPolicyBinding(
          value.reference(),
          validator
        );
        case OrderingPolicyBinding value -> new OrderingPolicyBinding(value.reference(), validator);
        case DeletionPolicyBinding value -> new DeletionPolicyBinding(value.reference(), validator);
        case LifecyclePolicyBinding value -> new LifecyclePolicyBinding(
          value.reference(),
          validator
        );
      };
    }

    private static void stubSuccessfulPorts(Harness harness) {
      AdminCategoryModels.Category category = category();
      AdminCategoryModels.CategoryNode node = new AdminCategoryModels.CategoryNode(
        category,
        0,
        false
      );
      when(harness.ports().categoryRead().list(any())).thenReturn(page(node));
      when(harness.ports().categoryRead().getNode(10L)).thenReturn(node);
      when(harness.ports().hierarchyRead().tree(any())).thenReturn(page(node));
      when(harness.ports().hierarchyRead().parentOptions(any())).thenReturn(
        new AdminCategoryModels.ParentOptionSlice(List.of(), false, null)
      );
      when(harness.ports().hierarchyRead().breadcrumb(10L)).thenReturn(
        List.of(new AdminCategoryModels.BreadcrumbItem(category, 0))
      );
      when(harness.ports().basicMutation().create(any())).thenReturn(category);
      when(harness.ports().basicMutation().replace(any(), any())).thenReturn(category);
      when(harness.ports().basicMutation().patch(any(), any())).thenReturn(category);
      when(harness.ports().ordering().reorder(any(), any())).thenReturn(category);
    }

    private static AdminCategorySystemRegistry actualRegistry(
      Collection<AdminCategorySystemRegistration> registrations
    ) {
      try {
        Constructor<AdminCategorySystemRegistry> constructor =
          AdminCategorySystemRegistry.class.getDeclaredConstructor(Collection.class);
        constructor.setAccessible(true);
        return constructor.newInstance(registrations);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException(
          "could not create the real immutable test registry",
          failure
        );
      }
    }

    private static AdminCategoryModels.Category category() {
      OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-14T00:00:00Z");
      return new AdminCategoryModels.Category(
        10L,
        2L,
        1_000_000_000L,
        "Storage",
        "storage",
        true,
        timestamp,
        timestamp
      );
    }

    private static PageResult<AdminCategoryModels.CategoryNode> page(
      AdminCategoryModels.CategoryNode node
    ) {
      return new PageResult<>(
        List.of(node),
        0,
        20,
        1,
        1,
        true,
        true,
        SortCriteria.of("id", SortDirection.ASC)
      );
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ActualRuntimeConfiguration {

    @Bean
    MatrixRuntime matrixRuntime() {
      return new MatrixRuntime();
    }

    @Bean
    AdminCategorySystemRegistry adminCategorySystemRegistry(MatrixRuntime runtime) {
      return runtime.registry();
    }

    @Bean
    AdminCategoryApplicationService adminCategoryApplicationService(MatrixRuntime runtime) {
      return runtime.service();
    }
  }
}
