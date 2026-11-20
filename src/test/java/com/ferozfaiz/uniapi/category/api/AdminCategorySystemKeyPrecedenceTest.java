package com.ferozfaiz.uniapi.category.api;

import static com.ferozfaiz.uniapi.category.contract.AdminCategoryContractAssertions.assertMatrixManifest;
import static com.ferozfaiz.uniapi.category.contract.AdminCategoryOperationMatrixCase.generateDeterministic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.authorization.AdminCategorySystemAuthorizationPolicy;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOperationPort;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryOperationMatrixCase;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture.Harness;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AdminCategoryController.class)
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  AdminCategoryApiExceptionHandler.class,
  AdminCategorySystemAuthorizationPolicy.class,
  AdminCategorySystemKeyPrecedenceTest.ActualRuntimeConfiguration.class,
})
class AdminCategorySystemKeyPrecedenceTest {

  private static final String PATH_PREFIX = "/api/v1/admin/category-systems/";
  private static final String PATH_SUFFIX = "/categories/capabilities";
  private static final String OVERLENGTH_KEY = "a".repeat(64);
  private static final List<String> FORBIDDEN_INTERACTIONS = List.of(
    "case-folding",
    "key-repair",
    "default-key",
    "other-registration",
    "provider",
    "repository",
    "cursor",
    "sql",
    "fallback"
  );

  @Autowired
  private MockMvc mvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private KeyRuntime runtime;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void clearRuntimeObservations() {
    runtime.clearObservations();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("systemKeyCases")
  void exactSystemKeyManifestExercisesTransportSecurityControllerApplicationAndRegistry(
    KeyDefinition definition,
    AdminCategoryOperationMatrixCase contract
  ) throws Exception {
    String publicSurface;
    List<StructuredLogCapture.CapturedEvent> logs;
    try (
      StructuredLogCapture applicationLogs = StructuredLogCapture.attach(
        AdminCategoryApplicationService.class
      );
      StructuredLogCapture authorizationLogs = StructuredLogCapture.attach(
        AdminCategorySystemAuthorizationPolicy.class
      )
    ) {
      publicSurface = switch (definition.mode()) {
        case HTTP -> performHttp(definition, contract);
        case NO_ROUTE -> performNoRoute(definition);
        case DIRECT_APPLICATION -> performDirectApplication(definition, contract);
      };
      logs = Stream.concat(
        applicationLogs.events().stream(),
        authorizationLogs.events().stream()
      ).toList();
    }

    assertThat(applicationInvocationNames()).containsExactlyElementsOf(
      definition.expectedApplicationCalls()
    );
    assertThat(registryLookupKeys()).containsExactlyElementsOf(definition.expectedLookupKeys());
    assertNoProviderCalls(runtime.catalogue());
    assertExpectedLogOutcome(definition, logs);
    assertForbiddenInteractions(definition, contract);

    if (
      Set.of("KEY-08", "KEY-09", "KEY-10").contains(definition.id()) &&
      definition.rawKey() != null &&
      !definition.rawKey().isEmpty()
    ) {
      assertAll(
        () -> assertThat(publicSurface).doesNotContain(definition.rawKey()),
        () ->
          assertThat(logs).allSatisfy(event ->
            assertThat(event.searchableSurfaces()).noneMatch(surface ->
              surface.contains(definition.rawKey())
            )
          )
      );
    }
  }

  private String performHttp(KeyDefinition definition, AdminCategoryOperationMatrixCase contract)
    throws Exception {
    MockHttpServletRequestBuilder request = get(
      URI.create(PATH_PREFIX + definition.encodedPathSegment() + PATH_SUFFIX)
    );
    if (definition.authority() != null) {
      request.with(jwt().authorities(new SimpleGrantedAuthority(definition.authority())));
    }
    var result = mvc
      .perform(request)
      .andExpect(status().is(definition.expectedStatus()))
      .andReturn();
    String body = result.getResponse().getContentAsString();
    if (!contract.expectedStableError().equals("none")) {
      var error = objectMapper.readTree(body);
      assertThat(error.path("code").asText()).isEqualTo(contract.expectedStableError());
    } else {
      assertThat(objectMapper.readTree(body).path("supportedOperations").size()).isEqualTo(14);
    }
    return body;
  }

  private String performNoRoute(KeyDefinition definition) throws Exception {
    var result = mvc
      .perform(
        get("/api/v1/admin/category-systems/categories/capabilities").with(
          jwt().authorities(new SimpleGrantedAuthority("admin:catalogue-categories:read"))
        )
      )
      .andExpect(status().is(definition.expectedStatus()))
      .andReturn();
    return result.getResponse().getContentAsString();
  }

  private String performDirectApplication(
    KeyDefinition definition,
    AdminCategoryOperationMatrixCase contract
  ) {
    AdminCategoryException failure = catchThrowableOfType(AdminCategoryException.class, () ->
      runtime.service().capabilities(definition.rawKey())
    );
    assertThat(failure).isNotNull();
    assertThat(failure.status().value()).isEqualTo(definition.expectedStatus());
    assertThat(failure.code()).isEqualTo(contract.expectedStableError());
    try {
      return objectMapper.writeValueAsString(
        Map.of(
          "code",
          failure.code(),
          "message",
          failure.getMessage(),
          "path",
          "/api/v1/admin/category-systems/<unavailable>/categories/capabilities"
        )
      );
    } catch (com.fasterxml.jackson.core.JsonProcessingException serializationFailure) {
      throw new AssertionError(
        "could not serialize direct application error envelope",
        serializationFailure
      );
    }
  }

  private void assertExpectedLogOutcome(
    KeyDefinition definition,
    List<StructuredLogCapture.CapturedEvent> logs
  ) {
    switch (definition.expectedLogOutcome()) {
      case
        "no-application-log",
        "transport/access logging only; no generic application log" -> assertThat(logs).isEmpty();
      case "one-bounded-authorization-denied-warn" -> {
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().level()).isEqualTo(Level.WARN);
        assertThat(structuredFields(logs.getFirst()))
          .containsEntry("category.event", "authorization.denied")
          .containsEntry("outcome", "denied")
          .containsEntry("errorCode", "ACCESS_DENIED");
      }
      case "one-bounded-system-unknown-warn" -> {
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().level()).isEqualTo(Level.WARN);
        assertThat(structuredFields(logs.getFirst()))
          .containsEntry("category.event", "system.unknown")
          .containsEntry("outcome", "failed")
          .containsEntry("errorCode", "UNKNOWN_CATEGORY_SYSTEM");
      }
      default -> throw new AssertionError(
        "unexecuted log outcome " + definition.expectedLogOutcome()
      );
    }
  }

  private void assertForbiddenInteractions(
    KeyDefinition definition,
    AdminCategoryOperationMatrixCase contract
  ) {
    List<String> lookups = registryLookupKeys();
    List<String> applicationCalls = applicationInvocationNames();
    boolean providerCalled = ports(runtime.catalogue())
      .stream()
      .anyMatch(port -> !mockingDetails(port).getInvocations().isEmpty());
    for (String forbidden : contract.forbiddenInteractions()) {
      boolean observed = switch (forbidden) {
        case "case-folding", "key-repair", "other-registration" -> !lookups.equals(
          definition.expectedLookupKeys()
        );
        case "default-key" -> !definition.expectedLookupKeys().contains("catalogue") &&
          lookups.contains("catalogue");
        case "provider", "repository", "cursor", "sql" -> providerCalled;
        case "fallback" -> providerCalled;
        default -> throw new AssertionError("unexecuted forbidden interaction " + forbidden);
      };
      assertThat(observed)
        .as(definition.id() + " forbids " + forbidden)
        .isFalse();
    }
    if (definition.expectedApplicationCalls().isEmpty()) {
      assertThat(applicationCalls).isEmpty();
    }
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

  private static Stream<Arguments> systemKeyCases() {
    List<KeyDefinition> definitions = keyDefinitions();
    List<AdminCategoryOperationMatrixCase> manifest = generateDeterministic(
      definitions,
      KeyDefinition::contract
    );
    assertMatrixManifest("system-key precedence", manifest, 12);

    assertThat(definitions).hasSize(12);
    assertThat(definitions)
      .extracting(KeyDefinition::id)
      .doesNotHaveDuplicates()
      .containsExactlyElementsOf(
        IntStream.rangeClosed(1, 12)
          .mapToObj(row -> "KEY-%02d".formatted(row))
          .toList()
      );
    assertThat(definitions)
      .filteredOn(row -> row.expectedStatus() == 401)
      .hasSize(1);
    assertThat(definitions)
      .filteredOn(row -> row.expectedStatus() == 403)
      .hasSize(1);
    assertThat(definitions)
      .filteredOn(row -> row.expectedStatus() == 200)
      .hasSize(2);
    assertThat(definitions)
      .filteredOn(row -> row.expectedStatus() == 404)
      .hasSize(8);
    assertThat(definitions)
      .filteredOn(row -> row.mode() == Mode.NO_ROUTE)
      .hasSize(1);
    assertThat(definitions)
      .filteredOn(row -> row.mode() == Mode.DIRECT_APPLICATION)
      .hasSize(2);
    assertThat(manifest).allSatisfy(row -> assertThat(row.expectedPortCalls()).isEmpty());

    return IntStream.range(0, definitions.size()).mapToObj(index ->
      Arguments.of(definitions.get(index), manifest.get(index))
    );
  }

  private static List<KeyDefinition> keyDefinitions() {
    return List.of(
      key(
        1,
        "FULL/catalogue",
        true,
        "unauthenticated",
        "no authority",
        "canonical catalogue representing any key",
        401,
        "UNAUTHENTICATED",
        Mode.HTTP,
        "catalogue",
        "catalogue",
        null,
        List.of(),
        List.of(),
        "authentication precedes lookup and registration disclosure"
      ),
      key(
        2,
        "FULL/catalogue",
        true,
        "authenticated",
        "missing selected/preliminary read authority",
        "canonical catalogue representing any key",
        403,
        "FORBIDDEN",
        Mode.HTTP,
        "catalogue",
        "catalogue",
        "unrelated:permission",
        List.of(),
        List.of("catalogue"),
        "authorization precedes application lookup"
      ),
      key(
        3,
        "FULL/catalogue",
        true,
        "authenticated",
        "exact selected read authority",
        "exact canonical catalogue",
        200,
        "none",
        Mode.HTTP,
        "catalogue",
        "catalogue",
        readPermission("catalogue"),
        List.of("capabilities"),
        List.of("catalogue", "catalogue"),
        "exact immutable selection succeeds without a provider port"
      ),
      key(
        4,
        "FULL/catalogue",
        true,
        "authenticated",
        "exact selected read authority",
        "whitespace-padded key trimmed exactly once",
        200,
        "none",
        Mode.HTTP,
        "  catalogue  ",
        "%20%20catalogue%20%20",
        readPermission("catalogue"),
        List.of("capabilities"),
        List.of("catalogue", "catalogue"),
        "trim-only selection succeeds without an untrimmed lookup"
      ),
      key(
        5,
        "missing-system",
        false,
        "authenticated",
        "exact preliminary unknown-system read authority",
        "canonical but unknown missing-system",
        404,
        "UNKNOWN_CATEGORY_SYSTEM",
        Mode.HTTP,
        "missing-system",
        "missing-system",
        readPermission("missing-system"),
        List.of("capabilities"),
        List.of("missing-system", "missing-system"),
        "exact immutable lookup reports absent without fallback"
      ),
      key(
        6,
        "no-selected-profile",
        false,
        "authenticated",
        "exact preliminary uppercase-key read authority",
        "uppercase CATALOGUE",
        404,
        "UNKNOWN_CATEGORY_SYSTEM",
        Mode.HTTP,
        "CATALOGUE",
        "CATALOGUE",
        readPermission("CATALOGUE"),
        List.of("capabilities"),
        List.of("CATALOGUE"),
        "case-sensitive lookup performs no lowercase retry"
      ),
      key(
        7,
        "no-selected-profile",
        false,
        "authenticated",
        "exact preliminary mixed-case read authority",
        "mixed-case Catalogue",
        404,
        "UNKNOWN_CATEGORY_SYSTEM",
        Mode.HTTP,
        "Catalogue",
        "Catalogue",
        readPermission("Catalogue"),
        List.of("capabilities"),
        List.of("Catalogue"),
        "case-sensitive lookup performs no lowercase retry"
      ),
      key(
        8,
        "no-selected-profile",
        false,
        "direct-application-contract",
        "HTTP authorization not applicable",
        "blank string reaches the application boundary",
        404,
        "UNKNOWN_CATEGORY_SYSTEM",
        Mode.DIRECT_APPLICATION,
        "   ",
        null,
        null,
        List.of("capabilities"),
        List.of(""),
        "blank translates safely without construction or fallback failure"
      ),
      key(
        9,
        "no-selected-profile",
        false,
        "authenticated",
        "safely derived preliminary authority",
        "malformed bad key!",
        404,
        "UNKNOWN_CATEGORY_SYSTEM",
        Mode.HTTP,
        "bad key!",
        "bad%20key!",
        readPermission("bad key!"),
        List.of("capabilities"),
        List.of("bad key!"),
        "malformed key is neither normalized nor repaired"
      ),
      key(
        10,
        "no-selected-profile",
        false,
        "authenticated",
        "safely derived preliminary authority",
        "overlength 64-character lowercase key",
        404,
        "UNKNOWN_CATEGORY_SYSTEM",
        Mode.HTTP,
        OVERLENGTH_KEY,
        OVERLENGTH_KEY,
        readPermission(OVERLENGTH_KEY),
        List.of("capabilities"),
        List.of(OVERLENGTH_KEY),
        "overlength key is not truncated into another valid key"
      ),
      key(
        11,
        "no-route-profile",
        false,
        "authenticated",
        "any authority",
        "omitted systemKey path segment",
        404,
        "N/A",
        Mode.NO_ROUTE,
        null,
        null,
        readPermission("catalogue"),
        List.of(),
        List.of(),
        "transport owns the no-route outcome; no category handler is selected"
      ),
      key(
        12,
        "no-selected-profile",
        false,
        "direct-application-contract",
        "HTTP authentication not applicable",
        "null systemKey",
        404,
        "UNKNOWN_CATEGORY_SYSTEM",
        Mode.DIRECT_APPLICATION,
        null,
        null,
        null,
        List.of("capabilities"),
        List.of(""),
        "null translates safely without NullPointerException or fallback"
      )
    );
  }

  private static KeyDefinition key(
    int row,
    String profileOrSystem,
    boolean declared,
    String authentication,
    String authority,
    String input,
    int status,
    String stableError,
    Mode mode,
    String rawKey,
    String encodedPathSegment,
    String grantedAuthority,
    List<String> applicationCalls,
    List<String> lookupKeys,
    String applicability
  ) {
    String id = "KEY-%02d".formatted(row);
    return new KeyDefinition(
      id,
      profileOrSystem,
      declared,
      authentication,
      authority,
      input,
      status,
      stableError,
      mode,
      rawKey,
      encodedPathSegment,
      grantedAuthority,
      applicationCalls,
      lookupKeys,
      switch (row) {
        case 2 -> "one-bounded-authorization-denied-warn";
        case 5, 6, 7, 8, 9, 10, 12 -> "one-bounded-system-unknown-warn";
        case 11 -> "transport/access logging only; no generic application log";
        default -> "no-application-log";
      },
      applicability
    );
  }

  private static String readPermission(String systemKey) {
    return "admin:" + systemKey + "-categories:read";
  }

  private List<String> applicationInvocationNames() {
    return mockingDetails(runtime.service())
      .getInvocations()
      .stream()
      .map(Invocation::getMethod)
      .map(method -> method.getName())
      .toList();
  }

  private List<String> registryLookupKeys() {
    return mockingDetails(runtime.registry())
      .getInvocations()
      .stream()
      .filter(invocation -> invocation.getMethod().getName().equals("findRegistration"))
      .map(invocation -> (String) invocation.getArgument(0))
      .toList();
  }

  private static void assertNoProviderCalls(Harness harness) {
    assertThat(
      ports(harness)
        .stream()
        .flatMap(port -> mockingDetails(port).getInvocations().stream())
        .toList()
    ).isEmpty();
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

  private enum Mode {
    HTTP,
    NO_ROUTE,
    DIRECT_APPLICATION,
  }

  private record KeyDefinition(
    String id,
    String profileOrSystem,
    boolean declared,
    String authentication,
    String authorityState,
    String input,
    int expectedStatus,
    String stableError,
    Mode mode,
    String rawKey,
    String encodedPathSegment,
    String authority,
    List<String> expectedApplicationCalls,
    List<String> expectedLookupKeys,
    String expectedLogOutcome,
    String applicability
  ) {
    private KeyDefinition {
      expectedApplicationCalls = List.copyOf(expectedApplicationCalls);
      expectedLookupKeys = List.copyOf(expectedLookupKeys);
    }

    private AdminCategoryOperationMatrixCase contract() {
      return new AdminCategoryOperationMatrixCase(
        profileOrSystem,
        Operation.CAPABILITIES,
        declared,
        authentication,
        authorityState,
        input,
        Integer.toString(expectedStatus),
        stableError,
        List.of(),
        FORBIDDEN_INTERACTIONS,
        expectedLogOutcome,
        applicability
      );
    }

    @Override
    public String toString() {
      return id;
    }
  }

  static final class KeyRuntime {

    private final Harness catalogue = AdminCategoryRuntimeTestFixture.harness();
    private final AdminCategorySystemRegistry registry = spy(
      actualRegistry(List.of(catalogue.registration()))
    );
    private final AdminCategoryApplicationService service = spy(
      new AdminCategoryApplicationService(registry)
    );

    Harness catalogue() {
      return catalogue;
    }

    AdminCategorySystemRegistry registry() {
      return registry;
    }

    AdminCategoryApplicationService service() {
      return service;
    }

    void clearObservations() {
      clearInvocations(service, registry);
      ports(catalogue).forEach(port -> clearInvocations(port));
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
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ActualRuntimeConfiguration {

    @Bean
    KeyRuntime keyRuntime() {
      return new KeyRuntime();
    }

    @Bean
    AdminCategorySystemRegistry adminCategorySystemRegistry(KeyRuntime runtime) {
      return runtime.registry();
    }

    @Bean
    AdminCategoryApplicationService adminCategoryApplicationService(KeyRuntime runtime) {
      return runtime.service();
    }
  }
}
