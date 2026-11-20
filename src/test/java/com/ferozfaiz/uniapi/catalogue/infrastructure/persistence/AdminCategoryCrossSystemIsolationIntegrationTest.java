package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsCursor;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.category.api.mapper.AdminCategoryApiMapper;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOperationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.AdminCategoryParentOptionsCursorCodec;
import com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.DescriptorDrivenAdminCategoryJdbcProvider;
import jakarta.persistence.EntityManager;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest(
  properties = {
    "app.security.oauth2.clients.nextjs-server.scopes[0]=api",
    "app.security.oauth2.clients.nextjs-server.scopes[1]=catalogue:read",
    "app.security.oauth2.clients.nextjs-server.scopes[2]=catalogue:write",
    "app.category.admin.jdbc.enabled-systems[0]=descriptor-a",
    "uniapi.category-systems.descriptor-a.owning-module=category",
    "uniapi.category-systems.descriptor-a.node-table=test_cross_descriptor_category",
    "uniapi.category-systems.descriptor-a.closure-table=test_cross_descriptor_category_closure",
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
  }
)
@AutoConfigureMockMvc
@Import({
  CataloguePersistenceTestContainersConfiguration.class,
  AdminCategoryCrossSystemIsolationIntegrationTest.ObservationConfiguration.class,
})
class AdminCategoryCrossSystemIsolationIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  private static final String CATALOGUE = "catalogue";
  private static final String DESCRIPTOR = "descriptor-a";
  private static final String DESCRIPTOR_TABLE = "test_cross_descriptor_category";
  private static final String DESCRIPTOR_CLOSURE = "test_cross_descriptor_category_closure";
  private static final String CATALOGUE_CURSOR_LITERAL =
    "eyJ2ZXJzaW9uIjoxLCJtb2RlIjoiU0VBUkNIIiwicSI6ImdwdSIsImluY2x1ZGVJbmFjdGl2ZSI6dHJ1ZSwiZXhjbHVkZVN1YnRyZWVJZCI6OSwicmFuayI6MiwiYWN0aXZlIjp0cnVlLCJuYW1lIjoiZ3JhcGhpY3MgY2FyZHMiLCJpZCI6NDR9";
  private static final String DESCRIPTOR_CURSOR_LITERAL =
    "MQlkZXNjcmlwdG9yLWEJd29qR3pNSXp2UE5TSWdkSjgxSDBfQzJoNVRDQVpjNFQ3MWR4cXNwRW50NAkyCXRydWUJWjNKaGNHaHBZM01nWTJGeVpITQk0NA";
  private static final RuntimeObservationProbe RUNTIME_OBSERVATIONS = new RuntimeObservationProbe();
  private static final SqlObservationProbe SQL_OBSERVATIONS = new SqlObservationProbe();
  private static final long ROOT_ID = 91_001L;
  private static final long CHILD_ID = 91_002L;
  private static final long PEER_ID = 91_003L;
  private static final long SELECTED_ID = 91_004L;
  private static final long OTHER_ONLY_ID = 91_005L;
  private static final Set<RouteOperation> MUTATIONS = EnumSet.of(
    RouteOperation.CREATE,
    RouteOperation.REPLACE,
    RouteOperation.PATCH,
    RouteOperation.DELETE,
    RouteOperation.MOVE,
    RouteOperation.REORDER,
    RouteOperation.ACTIVATE,
    RouteOperation.DEACTIVATE
  );

  @Autowired
  private MockMvc mvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private AdminCategorySystemRegistry registry;

  @Autowired
  private EntityManager entityManager;

  @MockitoSpyBean
  private CatalogueCategoryRepository catalogueRepository;

  private AdminCategoryParentOptionsCursorCodec descriptorCursorCodec;

  @BeforeEach
  void createDescriptorSchema() {
    installRuntimeObservationPorts();
    descriptorCursorCodec = installDescriptorCursorCodecSpy();
    dropDescriptorSchema();
    jdbc.execute(
      """
      CREATE TABLE test_cross_descriptor_category (
        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
        parent_id BIGINT NULL,
        sort_rank BIGINT NOT NULL DEFAULT 1000000000,
        name TEXT NOT NULL,
        slug TEXT NOT NULL,
        active BOOLEAN NOT NULL DEFAULT TRUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        UNIQUE (slug)
      )
      """
    );
    jdbc.execute(
      """
      CREATE TABLE test_cross_descriptor_category_closure (
        ancestor_id BIGINT NOT NULL,
        descendant_id BIGINT NOT NULL,
        depth INTEGER NOT NULL,
        PRIMARY KEY (ancestor_id, descendant_id)
      )
      """
    );
  }

  @AfterEach
  void clearObservationState() {
    RUNTIME_OBSERVATIONS.reset();
    SQL_OBSERVATIONS.reset();
    clearInvocations(catalogueRepository, descriptorCursorCodec);
  }

  private void installRuntimeObservationPorts() {
    if (
      registry
        .registrations()
        .stream()
        .allMatch(registration ->
          registration
            .runtimeBinding()
            .map(binding -> portsAreObserved(binding.ports()))
            .orElse(false)
        )
    ) {
      return;
    }
    List<AdminCategorySystemRegistration> observedRegistrations = registry
      .registrations()
      .stream()
      .map(this::withObservedPorts)
      .toList();
    Map<String, AdminCategorySystemRegistration> observedLookup = new LinkedHashMap<>();
    observedRegistrations.forEach(registration ->
      observedLookup.put(registration.systemKey().orElseThrow(), registration)
    );
    setField(registry, "registrations", List.copyOf(observedRegistrations));
    setField(registry, "registrationsBySystemKey", Map.copyOf(observedLookup));
  }

  private AdminCategorySystemRegistration withObservedPorts(
    AdminCategorySystemRegistration registration
  ) {
    String systemKey = registration.systemKey().orElseThrow();
    AdminCategoryRuntimeBinding binding = registration.runtimeBinding().orElseThrow();
    AdminCategoryRuntimePorts ports = binding.ports();
    var observedPorts = new AdminCategoryRuntimePorts(
      observedPort(systemKey, "category-read", ports.categoryRead(), AdminCategoryReadPort.class),
      observedPort(
        systemKey,
        "hierarchy-read",
        ports.hierarchyRead(),
        AdminCategoryHierarchyReadPort.class
      ),
      observedPort(
        systemKey,
        "basic-mutation",
        ports.basicMutation(),
        AdminCategoryBasicMutationPort.class
      ),
      observedPort(
        systemKey,
        "hierarchy-mutation",
        ports.hierarchyMutation(),
        AdminCategoryHierarchyMutationPort.class
      ),
      observedPort(systemKey, "ordering", ports.ordering(), AdminCategoryOrderingPort.class),
      observedPort(systemKey, "lifecycle", ports.lifecycle(), AdminCategoryLifecyclePort.class)
    );
    return AdminCategorySystemRegistration.of(
      registration.definition().orElseThrow(),
      new AdminCategoryRuntimeBinding(
        binding.identifierCodec(),
        binding.permissionMapping(),
        binding.policyBindings(),
        observedPorts
      )
    );
  }

  private static <T extends AdminCategoryOperationPort> Optional<T> observedPort(
    String systemKey,
    String capability,
    Optional<T> port,
    Class<T> portType
  ) {
    return port.map(delegate ->
      portType.cast(
        Proxy.newProxyInstance(
          portType.getClassLoader(),
          new Class<?>[] { portType },
          new RecordingPortInvocationHandler(systemKey, capability, delegate)
        )
      )
    );
  }

  private static boolean portsAreObserved(AdminCategoryRuntimePorts ports) {
    return (
      ports
        .categoryRead()
        .map(port -> Proxy.isProxyClass(port.getClass()))
        .orElse(false) &&
      Proxy.getInvocationHandler(ports.categoryRead().orElseThrow()) instanceof
        RecordingPortInvocationHandler
    );
  }

  private AdminCategoryParentOptionsCursorCodec installDescriptorCursorCodecSpy() {
    AdminCategoryOperationPort selectedPort = registry
      .findRegistration(DESCRIPTOR)
      .orElseThrow()
      .runtimeBinding()
      .orElseThrow()
      .ports()
      .categoryRead()
      .orElseThrow();
    if (
      Proxy.isProxyClass(selectedPort.getClass()) &&
      Proxy.getInvocationHandler(selectedPort) instanceof RecordingPortInvocationHandler handler
    ) {
      selectedPort = handler.delegate;
    }
    DescriptorDrivenAdminCategoryJdbcProvider provider = Optional.of(selectedPort)
      .filter(DescriptorDrivenAdminCategoryJdbcProvider.class::isInstance)
      .map(DescriptorDrivenAdminCategoryJdbcProvider.class::cast)
      .orElseThrow();
    Field field = declaredField(provider.getClass(), "cursorCodec");
    AdminCategoryParentOptionsCursorCodec current = getField(field, provider);
    if (mockingDetails(current).isSpy()) {
      return current;
    }
    AdminCategoryParentOptionsCursorCodec observed = spy(current);
    setField(field, provider, observed);
    return observed;
  }

  private static Field declaredField(Class<?> type, String fieldName) {
    try {
      Field field = type.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field;
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("test observation field is unavailable", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T getField(Field field, Object target) {
    try {
      return (T) field.get(target);
    } catch (IllegalAccessException exception) {
      throw new AssertionError("test observation field is unreadable", exception);
    }
  }

  private static void setField(Object target, String fieldName, Object value) {
    setField(declaredField(target.getClass(), fieldName), target, value);
  }

  private static void setField(Field field, Object target, Object value) {
    try {
      field.set(target, value);
    } catch (IllegalAccessException exception) {
      throw new AssertionError("test observation field is immutable", exception);
    }
  }

  @Test
  void allFourteenGenericRoutesStayScopedToTheCanonicalSelectedRegistration() throws Exception {
    assertThat(registry.registrations())
      .extracting(registration -> registration.systemKey().orElseThrow())
      .containsExactly(CATALOGUE, DESCRIPTOR);
    assertThat(registry.registrations()).allSatisfy(registration ->
      assertThat(
        registration.definition().orElseThrow().operations().supportedOperations()
      ).containsExactlyInAnyOrder(Operation.values())
    );

    List<RouteIsolationObservation> ledger = new ArrayList<>();
    for (String selectedSystem : List.of(CATALOGUE, DESCRIPTOR)) {
      for (RouteOperation operation : RouteOperation.values()) {
        restoreOverlappingFixture();
        if (operation == RouteOperation.CREATE) {
          insertOtherSystemCreateCollision(selectedSystem);
        }
        StoreSnapshot selectedBefore = snapshot(selectedSystem);
        StoreSnapshot otherBefore = snapshot(otherSystem(selectedSystem));

        resetRouteObservations();
        RouteResult result = performOperation(selectedSystem, operation);
        List<PortCall> portCalls = RUNTIME_OBSERVATIONS.portCalls();
        List<String> sqlStatements = categorySqlStatements(SQL_OBSERVATIONS.statements());
        int repositoryInvocationCount = repositoryInvocationCount();

        StoreSnapshot selectedAfter = snapshot(selectedSystem);
        StoreSnapshot otherAfter = snapshot(otherSystem(selectedSystem));
        assertThat(otherAfter)
          .as("%s must not change non-selected %s", operation, otherSystem(selectedSystem))
          .isEqualTo(otherBefore);
        if (MUTATIONS.contains(operation)) {
          assertThat(selectedAfter)
            .as("%s must change selected %s", operation, selectedSystem)
            .isNotEqualTo(selectedBefore);
        } else {
          assertThat(selectedAfter).isEqualTo(selectedBefore);
        }
        assertExactSelectedOutcome(
          selectedSystem,
          operation,
          result.body(),
          selectedBefore,
          selectedAfter
        );
        assertExecutableIsolationObservations(
          selectedSystem,
          operation,
          portCalls,
          sqlStatements,
          repositoryInvocationCount
        );
        int forbiddenInteractionCount = forbiddenInteractionCount(
          selectedSystem,
          portCalls,
          sqlStatements,
          repositoryInvocationCount
        );

        ledger.add(
          new RouteIsolationObservation(
            selectedSystem,
            operation,
            permission(selectedSystem, operation),
            portCalls.isEmpty() ? "definition-only" : portCalls.getFirst().portMethod(),
            observedStoreFamily(selectedSystem, sqlStatements),
            selectedBefore,
            selectedAfter,
            otherBefore,
            otherAfter,
            result.status(),
            "none",
            portCalls,
            sqlStatements,
            repositoryInvocationCount,
            forbiddenInteractionCount
          )
        );
      }
    }

    assertThat(ledger).hasSize(28);
    assertThat(
      ledger
        .stream()
        .map(entry -> entry.selectedSystem() + ":" + entry.operation())
        .toList()
    ).doesNotHaveDuplicates();
    assertThat(ledger).allSatisfy(entry -> {
      assertThat(entry.selectedSystem()).isNotBlank();
      assertThat(entry.permission()).isNotBlank();
      assertThat(entry.portMethod()).isNotBlank();
      assertThat(entry.storeFamily()).isNotBlank();
      assertThat(entry.stableError()).isEqualTo("none");
      assertThat(entry.forbiddenInteractionCount()).isZero();
      assertThat(entry.otherAfter()).isEqualTo(entry.otherBefore());
      if (entry.operation() == RouteOperation.CAPABILITIES) {
        assertThat(entry.portCalls()).isEmpty();
        assertThat(entry.sqlStatements()).isEmpty();
        assertThat(entry.repositoryInvocationCount()).isZero();
      } else {
        assertThat(entry.portCalls()).containsExactly(
          new PortCall(entry.selectedSystem(), entry.operation().portMethod)
        );
        assertThat(entry.sqlStatements()).isNotEmpty();
      }
    });
    assertThat(ledger)
      .extracting(RouteIsolationObservation::operation)
      .containsOnly(RouteOperation.values());
    assertThat(
      new HashSet<>(ledger.stream().map(RouteIsolationObservation::operation).toList())
    ).containsExactlyInAnyOrder(RouteOperation.values());
  }

  @Test
  void pinnedLiteralCursorsContinueOnlyThroughTheirSelectedDecoderAndStore() throws Exception {
    restoreOverlappingFixture();
    insertLiteralCursorFixture();
    String requestSuffix =
      "/parent-options?q=gpu&limit=50&includeInactive=true&excludeSubtreeId=9" +
      "&includeIds=5&includeIds=2&includeIds=5&cursor=";
    try (
      MockedStatic<AdminCatalogueCategoryParentOptionsCursor> catalogueCodec = Mockito.mockStatic(
        AdminCatalogueCategoryParentOptionsCursor.class,
        Mockito.CALLS_REAL_METHODS
      )
    ) {
      resetRouteObservations();
      JsonNode catalogue = getJson(
        base(CATALOGUE) + requestSuffix + CATALOGUE_CURSOR_LITERAL,
        readJwt(CATALOGUE),
        200
      );
      assertObservedParentOptionsRoute(CATALOGUE);
      catalogueCodec.verify(() ->
        AdminCatalogueCategoryParentOptionsCursor.decode(CATALOGUE_CURSOR_LITERAL)
      );
      verifyNoInteractions(descriptorCursorCodec);
      assertLiteralSelectedSlice(CATALOGUE, catalogue);

      resetRouteObservations();
      catalogueCodec.clearInvocations();
      JsonNode descriptor = getJson(
        base(DESCRIPTOR) + requestSuffix + DESCRIPTOR_CURSOR_LITERAL,
        readJwt(DESCRIPTOR),
        200
      );
      assertObservedParentOptionsRoute(DESCRIPTOR);
      Mockito.verify(descriptorCursorCodec).decode(
        Mockito.eq(DESCRIPTOR),
        Mockito.any(),
        Mockito.eq(DESCRIPTOR_CURSOR_LITERAL)
      );
      catalogueCodec.verifyNoInteractions();
      assertLiteralSelectedSlice(DESCRIPTOR, descriptor);
    }
  }

  @Test
  void cursorFamiliesAndSelectedHydrationNeverCrossSystemBoundaries() throws Exception {
    restoreOverlappingFixture();
    StoreSnapshot catalogueBefore = snapshot(CATALOGUE);
    StoreSnapshot descriptorBefore = snapshot(DESCRIPTOR);
    try (
      MockedStatic<AdminCatalogueCategoryParentOptionsCursor> catalogueCodec = Mockito.mockStatic(
        AdminCatalogueCategoryParentOptionsCursor.class,
        Mockito.CALLS_REAL_METHODS
      )
    ) {
      JsonNode catalogueFirst = parentOptions(CATALOGUE, null, List.of(), true, 1);
      JsonNode descriptorFirst = parentOptions(DESCRIPTOR, null, List.of(), true, 1);
      String catalogueCursor = catalogueFirst.path("nextCursor").textValue();
      String descriptorCursor = descriptorFirst.path("nextCursor").textValue();
      assertThat(catalogueCursor).isNotBlank();
      assertThat(descriptorCursor).isNotBlank();
      assertThat(catalogueCursor).isNotEqualTo(descriptorCursor);

      resetRouteObservations();
      catalogueCodec.clearInvocations();
      JsonNode catalogueSecond = parentOptions(CATALOGUE, catalogueCursor, List.of(), true, 1);
      assertObservedParentOptionsRoute(CATALOGUE);
      catalogueCodec.verify(() ->
        AdminCatalogueCategoryParentOptionsCursor.decode(catalogueCursor)
      );
      verifyNoInteractions(descriptorCursorCodec);
      assertSelectedBody(CATALOGUE, catalogueSecond.toString());

      resetRouteObservations();
      catalogueCodec.clearInvocations();
      JsonNode descriptorSecond = parentOptions(DESCRIPTOR, descriptorCursor, List.of(), true, 1);
      assertObservedParentOptionsRoute(DESCRIPTOR);
      Mockito.verify(descriptorCursorCodec).decode(
        Mockito.eq(DESCRIPTOR),
        Mockito.any(),
        Mockito.eq(descriptorCursor)
      );
      catalogueCodec.verifyNoInteractions();
      assertSelectedBody(DESCRIPTOR, descriptorSecond.toString());

      resetRouteObservations();
      catalogueCodec.clearInvocations();
      JsonNode catalogueHydrated = parentOptions(
        CATALOGUE,
        null,
        List.of(SELECTED_ID, SELECTED_ID, CHILD_ID),
        false,
        10
      );
      assertObservedParentOptionsRoute(CATALOGUE);
      assertExactHydration(CATALOGUE, catalogueHydrated);

      resetRouteObservations();
      catalogueCodec.clearInvocations();
      JsonNode descriptorHydrated = parentOptions(
        DESCRIPTOR,
        null,
        List.of(SELECTED_ID, SELECTED_ID, CHILD_ID),
        false,
        10
      );
      assertObservedParentOptionsRoute(DESCRIPTOR);
      assertExactHydration(DESCRIPTOR, descriptorHydrated);

      resetRouteObservations();
      catalogueCodec.clearInvocations();
      JsonNode descriptorRejectsCatalogue = getJson(
        base(DESCRIPTOR) +
          "/parent-options?q=shared&limit=1&includeInactive=true&cursor=" +
          catalogueCursor,
        readJwt(DESCRIPTOR),
        400
      );
      assertCursorRejectedBeforeStorage(DESCRIPTOR);
      Mockito.verify(descriptorCursorCodec).decode(
        Mockito.eq(DESCRIPTOR),
        Mockito.any(),
        Mockito.eq(catalogueCursor)
      );
      catalogueCodec.verifyNoInteractions();

      resetRouteObservations();
      catalogueCodec.clearInvocations();
      JsonNode catalogueRejectsDescriptor = getJson(
        base(CATALOGUE) +
          "/parent-options?q=shared&limit=1&includeInactive=true&cursor=" +
          descriptorCursor,
        readJwt(CATALOGUE),
        400
      );
      assertCursorRejectedBeforeStorage(CATALOGUE);
      catalogueCodec.verify(() ->
        AdminCatalogueCategoryParentOptionsCursor.decode(descriptorCursor)
      );
      verifyNoInteractions(descriptorCursorCodec);

      assertThat(descriptorRejectsCatalogue.path("code").textValue()).isEqualTo(
        "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID"
      );
      assertThat(catalogueRejectsDescriptor.path("code").textValue()).isEqualTo(
        "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID"
      );
      assertThat(descriptorRejectsCatalogue.toString()).doesNotContain(catalogueCursor);
      assertThat(catalogueRejectsDescriptor.toString()).doesNotContain(descriptorCursor);
    }
    assertThat(snapshot(CATALOGUE)).isEqualTo(catalogueBefore);
    assertThat(snapshot(DESCRIPTOR)).isEqualTo(descriptorBefore);
  }

  @Test
  void permissionsAndSelectedErrorsCannotReadTheOtherSystemAsFallback() throws Exception {
    restoreOverlappingFixture();
    insertOtherOnly(DESCRIPTOR, "descriptor-only");
    StoreSnapshot catalogueBefore = snapshot(CATALOGUE);
    StoreSnapshot descriptorBefore = snapshot(DESCRIPTOR);

    resetRouteObservations();
    JsonNode catalogueMissing = getJson(
      base(CATALOGUE) + "/" + OTHER_ONLY_ID,
      readJwt(CATALOGUE),
      404
    );
    assertExecutableIsolationObservations(
      CATALOGUE,
      RouteOperation.GET,
      RUNTIME_OBSERVATIONS.portCalls(),
      categorySqlStatements(SQL_OBSERVATIONS.statements()),
      repositoryInvocationCount()
    );
    assertThat(catalogueMissing.path("code").textValue()).isEqualTo("CATEGORY_NOT_FOUND");
    assertThat(catalogueMissing.toString()).doesNotContain("descriptor-only");

    resetRouteObservations();
    assertThat(
      getJson(base(CATALOGUE), readJwt(DESCRIPTOR), 403).path("code").textValue()
    ).isEqualTo("ADMIN_ACCESS_DENIED");
    assertRejectedBeforeRuntime();
    assertThat(snapshot(CATALOGUE)).isEqualTo(catalogueBefore);
    assertThat(snapshot(DESCRIPTOR)).isEqualTo(descriptorBefore);

    restoreOverlappingFixture();
    insertOtherOnly(CATALOGUE, "catalogue-only");
    catalogueBefore = snapshot(CATALOGUE);
    descriptorBefore = snapshot(DESCRIPTOR);

    resetRouteObservations();
    JsonNode descriptorMissing = getJson(
      base(DESCRIPTOR) + "/" + OTHER_ONLY_ID,
      readJwt(DESCRIPTOR),
      404
    );
    assertExecutableIsolationObservations(
      DESCRIPTOR,
      RouteOperation.GET,
      RUNTIME_OBSERVATIONS.portCalls(),
      categorySqlStatements(SQL_OBSERVATIONS.statements()),
      repositoryInvocationCount()
    );
    assertThat(descriptorMissing.path("code").textValue()).isEqualTo("CATEGORY_NOT_FOUND");
    assertThat(descriptorMissing.toString()).doesNotContain("catalogue-only");

    resetRouteObservations();
    assertThat(
      getJson(base(DESCRIPTOR), readJwt(CATALOGUE), 403).path("code").textValue()
    ).isEqualTo("ADMIN_ACCESS_DENIED");
    assertRejectedBeforeRuntime();
    assertThat(snapshot(CATALOGUE)).isEqualTo(catalogueBefore);
    assertThat(snapshot(DESCRIPTOR)).isEqualTo(descriptorBefore);
  }

  @Test
  void selectedMutationErrorsAndWritePermissionDenialNeverUseTheOtherSystem() throws Exception {
    List<String> errorLedger = new ArrayList<>();
    for (String selectedSystem : List.of(CATALOGUE, DESCRIPTOR)) {
      for (SelectedErrorScenario scenario : SelectedErrorScenario.values()) {
        restoreOverlappingFixture();
        insertOtherOnly(otherSystem(selectedSystem), "other-error-probe");
        StoreSnapshot selectedBefore = snapshot(selectedSystem);
        StoreSnapshot otherBefore = snapshot(otherSystem(selectedSystem));

        resetRouteObservations();
        JsonNode error = performJson(
          errorRequest(selectedSystem, scenario).with(writeJwt(selectedSystem)),
          scenario.status
        );
        assertThat(error.path("code").textValue()).isEqualTo(scenario.errorCode);
        assertThat(error.toString())
          .doesNotContain("other-error-probe")
          .doesNotContain(otherPrefix(selectedSystem));
        assertExecutableIsolationObservations(
          selectedSystem,
          scenario.operation,
          RUNTIME_OBSERVATIONS.portCalls(),
          categorySqlStatements(SQL_OBSERVATIONS.statements()),
          repositoryInvocationCount()
        );
        assertThat(snapshot(selectedSystem)).isEqualTo(selectedBefore);
        assertThat(snapshot(otherSystem(selectedSystem))).isEqualTo(otherBefore);
        errorLedger.add(selectedSystem + ":" + scenario.name());
      }

      restoreOverlappingFixture();
      StoreSnapshot selectedBefore = snapshot(selectedSystem);
      StoreSnapshot otherBefore = snapshot(otherSystem(selectedSystem));
      resetRouteObservations();
      JsonNode denied = performJson(
        post(base(selectedSystem))
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson(ROOT_ID, "denied-create", "denied-create", true))
          .with(writeJwt(otherSystem(selectedSystem))),
        403
      );
      assertThat(denied.path("code").textValue()).isEqualTo("ADMIN_ACCESS_DENIED");
      assertRejectedBeforeRuntime();
      assertThat(snapshot(selectedSystem)).isEqualTo(selectedBefore);
      assertThat(snapshot(otherSystem(selectedSystem))).isEqualTo(otherBefore);
    }
    assertThat(errorLedger).hasSize(10).doesNotHaveDuplicates();
  }

  @Test
  void everySelectedMutationRollsBackWithoutTouchingTheOtherStore() {
    List<String> rollbackLedger = new ArrayList<>();

    for (String selectedSystem : List.of(CATALOGUE, DESCRIPTOR)) {
      for (RouteOperation operation : MUTATIONS) {
        restoreOverlappingFixture();
        if (operation == RouteOperation.CREATE) {
          insertOtherSystemCreateCollision(selectedSystem);
        }
        StoreSnapshot selectedBefore = snapshot(selectedSystem);
        StoreSnapshot otherBefore = snapshot(otherSystem(selectedSystem));

        resetRouteObservations();
        RUNTIME_OBSERVATIONS.failAfter(
          selectedSystem,
          operation.portMethod,
          selectedSystem.equals(CATALOGUE) ? entityManager::flush : () -> {}
        );
        assertThatThrownBy(() ->
          performOperationWithoutSuccessResponse(selectedSystem, operation)
        ).hasRootCauseInstanceOf(ControlledRollback.class);

        List<PortCall> portCalls = RUNTIME_OBSERVATIONS.portCalls();
        List<String> sqlStatements = categorySqlStatements(SQL_OBSERVATIONS.statements());
        int repositoryInvocationCount = repositoryInvocationCount();
        assertSelectedMutationDml(selectedSystem, operation, sqlStatements);
        assertExecutableIsolationObservations(
          selectedSystem,
          operation,
          portCalls,
          sqlStatements,
          repositoryInvocationCount
        );
        assertThat(
          forbiddenInteractionCount(
            selectedSystem,
            portCalls,
            sqlStatements,
            repositoryInvocationCount
          )
        ).isZero();

        assertThat(snapshot(selectedSystem))
          .as("%s rollback for %s", operation, selectedSystem)
          .isEqualTo(selectedBefore);
        assertThat(snapshot(otherSystem(selectedSystem)))
          .as("%s must not touch %s during rollback", operation, otherSystem(selectedSystem))
          .isEqualTo(otherBefore);
        rollbackLedger.add(selectedSystem + ":" + operation);
      }
    }

    assertThat(rollbackLedger).hasSize(16).doesNotHaveDuplicates();
  }

  private RouteResult performOperation(String system, RouteOperation operation) throws Exception {
    MockHttpServletRequestBuilder request = operationRequest(system, operation);
    var result = mvc
      .perform(request)
      .andExpect(MockMvcResultMatchers.status().is(operation.expectedStatus))
      .andReturn();
    return new RouteResult(
      result.getResponse().getStatus(),
      result.getResponse().getContentAsString()
    );
  }

  private void performOperationWithoutSuccessResponse(String system, RouteOperation operation)
    throws Exception {
    mvc.perform(operationRequest(system, operation));
    throw new AssertionError("failure injection unexpectedly produced a response");
  }

  private MockHttpServletRequestBuilder operationRequest(String system, RouteOperation operation) {
    MockHttpServletRequestBuilder request = switch (operation) {
      case CAPABILITIES -> get(base(system) + "/capabilities");
      case LIST -> get(base(system));
      case TREE -> get(base(system) + "/tree");
      case PARENT_OPTIONS -> get(base(system) + "/parent-options")
        .param("limit", "20")
        .param("includeInactive", "true");
      case GET -> get(base(system) + "/" + CHILD_ID);
      case BREADCRUMB -> get(base(system) + "/" + CHILD_ID + "/breadcrumb");
      case CREATE -> post(base(system))
        .contentType(MediaType.APPLICATION_JSON)
        .content(categoryJson(ROOT_ID, system + "-created", "same-new-slug", true));
      case REPLACE -> put(base(system) + "/" + CHILD_ID)
        .contentType(MediaType.APPLICATION_JSON)
        .content(categoryJson(ROOT_ID, system + "-replaced", "selected-replaced", true));
      case PATCH -> patch(base(system) + "/" + CHILD_ID)
        .contentType("application/merge-patch+json")
        .content("{\"name\":\"" + system + "-patched\"}");
      case DELETE -> delete(base(system) + "/" + PEER_ID);
      case MOVE -> post(base(system) + "/" + CHILD_ID + "/move")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"newParentId\":null}");
      case REORDER -> post(base(system) + "/" + PEER_ID + "/reorder")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentId\":" + ROOT_ID + ",\"targetIndex\":0}");
      case ACTIVATE -> post(base(system) + "/" + SELECTED_ID + "/activate");
      case DEACTIVATE -> post(base(system) + "/" + CHILD_ID + "/deactivate");
    };
    request.with(MUTATIONS.contains(operation) ? writeJwt(system) : readJwt(system));
    return request;
  }

  private JsonNode parentOptions(
    String system,
    String cursor,
    List<Long> includeIds,
    boolean includeInactive,
    int limit
  ) throws Exception {
    MockHttpServletRequestBuilder request = get(base(system) + "/parent-options")
      .param("q", "shared")
      .param("limit", Integer.toString(limit))
      .param("includeInactive", Boolean.toString(includeInactive))
      .with(readJwt(system));
    if (cursor != null) {
      request.param("cursor", cursor);
    }
    if (!includeIds.isEmpty()) {
      request.param(
        "includeIds",
        includeIds
          .stream()
          .map(String::valueOf)
          .toArray(String[]::new)
      );
    }
    var result = mvc.perform(request).andExpect(MockMvcResultMatchers.status().isOk()).andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode getJson(String path, RequestPostProcessor auth, int expectedStatus)
    throws Exception {
    return performJson(get(path).with(auth), expectedStatus);
  }

  private JsonNode performJson(MockHttpServletRequestBuilder request, int expectedStatus)
    throws Exception {
    var result = mvc
      .perform(request)
      .andExpect(MockMvcResultMatchers.status().is(expectedStatus))
      .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private static MockHttpServletRequestBuilder errorRequest(
    String system,
    SelectedErrorScenario scenario
  ) {
    return switch (scenario) {
      case DUPLICATE_SLUG -> post(base(system))
        .contentType(MediaType.APPLICATION_JSON)
        .content(categoryJson(ROOT_ID, "duplicate", "shared-child", true));
      case MISSING_PARENT -> post(base(system))
        .contentType(MediaType.APPLICATION_JSON)
        .content(categoryJson(OTHER_ONLY_ID, "missing parent", "missing-parent", true));
      case INVALID_MOVE -> post(base(system) + "/" + ROOT_ID + "/move")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"newParentId\":" + CHILD_ID + "}");
      case INVALID_PLACEMENT -> post(base(system) + "/" + PEER_ID + "/reorder")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentId\":" + ROOT_ID + ",\"beforeId\":" + OTHER_ONLY_ID + "}");
      case DELETE_BLOCKED -> delete(base(system) + "/" + ROOT_ID);
    };
  }

  private void resetRouteObservations() {
    RUNTIME_OBSERVATIONS.reset();
    SQL_OBSERVATIONS.reset();
    clearInvocations(catalogueRepository, descriptorCursorCodec);
  }

  private int repositoryInvocationCount() {
    return mockingDetails(catalogueRepository).getInvocations().size();
  }

  private void assertObservedParentOptionsRoute(String selectedSystem) {
    assertExecutableIsolationObservations(
      selectedSystem,
      RouteOperation.PARENT_OPTIONS,
      RUNTIME_OBSERVATIONS.portCalls(),
      categorySqlStatements(SQL_OBSERVATIONS.statements()),
      repositoryInvocationCount()
    );
  }

  private void assertCursorRejectedBeforeStorage(String selectedSystem) {
    List<PortCall> portCalls = RUNTIME_OBSERVATIONS.portCalls();
    List<String> sqlStatements = categorySqlStatements(SQL_OBSERVATIONS.statements());
    int repositoryInvocationCount = repositoryInvocationCount();
    assertThat(portCalls).containsExactly(
      new PortCall(selectedSystem, RouteOperation.PARENT_OPTIONS.portMethod)
    );
    assertThat(sqlStatements).isEmpty();
    assertThat(repositoryInvocationCount).isZero();
    assertThat(
      forbiddenInteractionCount(selectedSystem, portCalls, sqlStatements, repositoryInvocationCount)
    ).isZero();
  }

  private void assertRejectedBeforeRuntime() {
    assertThat(RUNTIME_OBSERVATIONS.portCalls()).isEmpty();
    assertThat(categorySqlStatements(SQL_OBSERVATIONS.statements())).isEmpty();
    assertThat(repositoryInvocationCount()).isZero();
    verifyNoInteractions(catalogueRepository, descriptorCursorCodec);
  }

  private static void assertExactHydration(String selectedSystem, JsonNode response) {
    JsonNode data = response.path("data");
    List<Long> actualIds = new ArrayList<>();
    data.forEach(option -> actualIds.add(option.path("id").longValue()));
    assertThat(actualIds).containsExactly(SELECTED_ID, CHILD_ID, PEER_ID, ROOT_ID);
    JsonNode selected = data.get(0);
    String valuePrefix = selectedSystem.equals(CATALOGUE) ? CATALOGUE : "descriptor";
    assertThat(selected.path("name").textValue()).isEqualTo(valuePrefix + "-selected");
    assertThat(selected.path("active").booleanValue()).isFalse();
    List<String> pathNames = new ArrayList<>();
    selected.path("path").forEach(item -> pathNames.add(item.path("name").textValue()));
    assertThat(pathNames).containsExactly(valuePrefix + "-root", valuePrefix + "-selected");
    if (selectedSystem.equals(CATALOGUE)) {
      assertThat(selected.path("selectable").booleanValue()).isFalse();
      assertThat(selected.path("disabledReason").textValue()).isEqualTo("INACTIVE");
    } else {
      assertThat(selected.path("selectable").booleanValue()).isTrue();
      assertThat(selected.path("disabledReason").isNull()).isTrue();
    }
    assertThat(response.path("hasMore").booleanValue()).isFalse();
    assertThat(response.path("nextCursor").isNull()).isTrue();
    assertSelectedBody(selectedSystem, response.toString());
  }

  private static void assertTerminalLiteralSlice(JsonNode response, String rawCursor) {
    assertThat(response.path("data").isArray()).isTrue();
    assertThat(response.path("data")).isEmpty();
    assertThat(response.path("hasMore").booleanValue()).isFalse();
    assertThat(response.path("nextCursor").isNull()).isTrue();
    assertThat(response.toString()).doesNotContain(rawCursor);
  }

  private static List<String> categorySqlStatements(List<String> statements) {
    return statements
      .stream()
      .filter(
        statement -> referencesStore(statement, CATALOGUE) || referencesStore(statement, DESCRIPTOR)
      )
      .toList();
  }

  private void assertExecutableIsolationObservations(
    String selectedSystem,
    RouteOperation operation,
    List<PortCall> portCalls,
    List<String> sqlStatements,
    int repositoryInvocationCount
  ) {
    if (operation == RouteOperation.CAPABILITIES) {
      assertThat(portCalls).isEmpty();
      assertThat(sqlStatements).isEmpty();
      assertThat(repositoryInvocationCount).isZero();
      return;
    }
    assertThat(portCalls)
      .as("one actual selected port for %s:%s", selectedSystem, operation)
      .containsExactly(new PortCall(selectedSystem, operation.portMethod));
    assertThat(sqlStatements)
      .as("selected storage SQL for %s:%s", selectedSystem, operation)
      .anySatisfy(statement -> assertThat(referencesStore(statement, selectedSystem)).isTrue())
      .noneSatisfy(statement ->
        assertThat(referencesStore(statement, otherSystem(selectedSystem))).isTrue()
      );
    if (selectedSystem.equals(DESCRIPTOR)) {
      assertThat(repositoryInvocationCount).isZero();
      verifyNoInteractions(catalogueRepository);
    } else {
      assertThat(repositoryInvocationCount).isPositive();
    }
  }

  private static int forbiddenInteractionCount(
    String selectedSystem,
    List<PortCall> portCalls,
    List<String> sqlStatements,
    int repositoryInvocationCount
  ) {
    int nonSelectedPortCalls = (int) portCalls
      .stream()
      .filter(call -> !call.systemKey().equals(selectedSystem))
      .count();
    int nonSelectedSqlStatements = (int) sqlStatements
      .stream()
      .filter(statement -> referencesStore(statement, otherSystem(selectedSystem)))
      .count();
    int nonSelectedRepositoryCalls = selectedSystem.equals(DESCRIPTOR)
      ? repositoryInvocationCount
      : 0;
    return nonSelectedPortCalls + nonSelectedSqlStatements + nonSelectedRepositoryCalls;
  }

  private static String observedStoreFamily(String selectedSystem, List<String> sqlStatements) {
    if (sqlStatements.isEmpty()) {
      return "none";
    }
    assertThat(sqlStatements).anySatisfy(statement ->
      assertThat(referencesStore(statement, selectedSystem)).isTrue()
    );
    return selectedSystem.equals(CATALOGUE) ? "catalogue_category" : DESCRIPTOR_TABLE;
  }

  private static void assertSelectedMutationDml(
    String selectedSystem,
    RouteOperation operation,
    List<String> sqlStatements
  ) {
    String tableFamily = selectedSystem.equals(CATALOGUE) ? "catalogue_category" : DESCRIPTOR_TABLE;
    String dmlPrefix =
      operation == RouteOperation.CREATE
        ? "insert into "
        : operation == RouteOperation.DELETE
          ? "delete from "
          : "update ";
    assertThat(sqlStatements)
      .as("%s must execute selected DML before injected failure for %s", selectedSystem, operation)
      .anyMatch(statement -> statement.contains(dmlPrefix + tableFamily));
  }

  private static boolean referencesStore(String sql, String system) {
    String tableFamily = system.equals(CATALOGUE) ? "catalogue_category" : DESCRIPTOR_TABLE;
    return sql.contains(tableFamily);
  }

  private void assertExactSelectedOutcome(
    String selectedSystem,
    RouteOperation operation,
    String body,
    StoreSnapshot selectedBefore,
    StoreSnapshot selectedAfter
  ) throws Exception {
    if (operation == RouteOperation.CAPABILITIES) {
      String expected = objectMapper.writeValueAsString(
        AdminCategoryApiMapper.toCapabilityResponse(
          registry.findRegistration(selectedSystem).orElseThrow().definition().orElseThrow()
        )
      );
      assertThat(body).isEqualTo(expected);
    }
    switch (operation) {
      case LIST -> assertExactPage(
        selectedSystem,
        objectMapper.readTree(body),
        List.of(SELECTED_ID, PEER_ID, CHILD_ID, ROOT_ID),
        20,
        List.of("updatedAt:DESC", "id:ASC")
      );
      case TREE -> assertExactPage(
        selectedSystem,
        objectMapper.readTree(body),
        List.of(ROOT_ID, CHILD_ID, PEER_ID, SELECTED_ID),
        10_000,
        List.of("sortRank:ASC", "id:ASC")
      );
      case PARENT_OPTIONS -> assertExactOrdinaryParentOptions(
        selectedSystem,
        objectMapper.readTree(body)
      );
      case GET -> assertExactDetail(selectedSystem, objectMapper.readTree(body), CHILD_ID);
      case BREADCRUMB -> assertExactBreadcrumb(
        selectedSystem,
        objectMapper.readTree(body),
        List.of(ROOT_ID, CHILD_ID)
      );
      case CREATE -> {
        Map<String, Object> created = rowBySlug(selectedAfter, "same-new-slug");
        assertRow(created, null, ROOT_ID, selectedSystem + "-created", "same-new-slug", true);
        assertThat(body).contains(selectedSystem + "-created", "same-new-slug");
      }
      case REPLACE -> {
        assertRow(
          rowById(selectedAfter, CHILD_ID),
          CHILD_ID,
          ROOT_ID,
          selectedSystem + "-replaced",
          "selected-replaced",
          true
        );
        assertThat(body).contains(selectedSystem + "-replaced", "selected-replaced");
      }
      case PATCH -> {
        assertRow(
          rowById(selectedAfter, CHILD_ID),
          CHILD_ID,
          ROOT_ID,
          selectedSystem + "-patched",
          "shared-child",
          true
        );
        assertThat(body).contains(selectedSystem + "-patched", "shared-child");
      }
      case DELETE -> {
        assertThat(selectedAfter.rows()).noneSatisfy(row ->
          assertThat(longValue(row.get("id"))).isEqualTo(PEER_ID)
        );
        assertThat(selectedAfter.closureRows()).noneSatisfy(row ->
          assertThat(longValue(row.get("descendant_id"))).isEqualTo(PEER_ID)
        );
      }
      case MOVE -> {
        assertThat(rowById(selectedAfter, CHILD_ID).get("parent_id")).isNull();
        assertThat(hasPath(selectedAfter, ROOT_ID, CHILD_ID)).isFalse();
        assertThat(hasPath(selectedAfter, CHILD_ID, CHILD_ID)).isTrue();
      }
      case REORDER -> {
        long peerRank = longValue(rowById(selectedAfter, PEER_ID).get("sort_rank"));
        long childRank = longValue(rowById(selectedAfter, CHILD_ID).get("sort_rank"));
        assertThat(peerRank).isLessThan(childRank);
        assertThat(rowById(selectedAfter, PEER_ID).get("parent_id")).isEqualTo(ROOT_ID);
      }
      case ACTIVATE -> assertThat(rowById(selectedAfter, SELECTED_ID).get("active")).isEqualTo(
        true
      );
      case DEACTIVATE -> assertThat(rowById(selectedAfter, CHILD_ID).get("active")).isEqualTo(
        false
      );
      case CAPABILITIES -> assertThat(selectedAfter).isEqualTo(selectedBefore);
    }
    if (!body.isBlank()) {
      assertThat(body).doesNotContain(otherPrefix(selectedSystem));
    }
  }

  private static void assertExactPage(
    String selectedSystem,
    JsonNode response,
    List<Long> expectedIds,
    int expectedSize,
    List<String> expectedSort
  ) {
    assertExactCategoryArray(selectedSystem, response.path("data"), expectedIds);
    JsonNode page = response.path("page");
    assertThat(page.path("number").intValue()).isZero();
    assertThat(page.path("size").intValue()).isEqualTo(expectedSize);
    assertThat(page.path("totalElements").longValue()).isEqualTo(expectedIds.size());
    assertThat(page.path("totalPages").intValue()).isEqualTo(1);
    assertThat(page.path("numberOfElements").intValue()).isEqualTo(expectedIds.size());
    assertThat(page.path("first").booleanValue()).isTrue();
    assertThat(page.path("last").booleanValue()).isTrue();
    List<String> actualSort = new ArrayList<>();
    response
      .path("sort")
      .forEach(sort ->
        actualSort.add(sort.path("property").textValue() + ":" + sort.path("direction").textValue())
      );
    assertThat(actualSort).containsExactlyElementsOf(expectedSort);
    assertThat(response.path("links").path("self").textValue())
      .contains("/category-systems/" + selectedSystem + "/categories")
      .doesNotContain(otherSystem(selectedSystem));
    assertThat(response.path("links").path("next").isNull()).isTrue();
    assertThat(response.path("links").path("previous").isNull()).isTrue();
  }

  private static void assertExactOrdinaryParentOptions(String selectedSystem, JsonNode response) {
    JsonNode data = response.path("data");
    List<Long> expectedIds = List.of(CHILD_ID, PEER_ID, ROOT_ID, SELECTED_ID);
    assertThat(jsonIds(data)).containsExactlyElementsOf(expectedIds);
    for (int index = 0; index < expectedIds.size(); index++) {
      long id = expectedIds.get(index);
      JsonNode option = data.get(index);
      assertExactCategory(selectedSystem, option, id);
      List<Long> expectedPath = id == ROOT_ID ? List.of(ROOT_ID) : List.of(ROOT_ID, id);
      assertExactBreadcrumb(selectedSystem, option.path("path"), expectedPath);
      assertThat(option.path("label").textValue()).isEqualTo(selectedName(selectedSystem, id));
    }
    assertThat(response.path("hasMore").booleanValue()).isFalse();
    assertThat(response.path("nextCursor").isNull()).isTrue();
  }

  private static void assertExactCategoryArray(
    String selectedSystem,
    JsonNode data,
    List<Long> expectedIds
  ) {
    assertThat(jsonIds(data)).containsExactlyElementsOf(expectedIds);
    for (int index = 0; index < expectedIds.size(); index++) {
      long id = expectedIds.get(index);
      JsonNode node = data.get(index);
      assertExactCategory(selectedSystem, node, id);
      assertExactRankAndTimestamps(node, id);
      assertThat(node.path("depth").intValue()).isEqualTo(id == ROOT_ID ? 0 : 1);
      assertThat(node.path("hasChildren").booleanValue()).isEqualTo(id == ROOT_ID);
    }
  }

  private static void assertExactCategory(
    String selectedSystem,
    JsonNode category,
    long expectedId
  ) {
    assertThat(category.path("id").longValue()).isEqualTo(expectedId);
    if (expectedId == ROOT_ID) {
      assertThat(category.path("parentId").isNull()).isTrue();
    } else {
      assertThat(category.path("parentId").longValue()).isEqualTo(ROOT_ID);
    }
    assertThat(category.path("name").textValue()).isEqualTo(
      selectedName(selectedSystem, expectedId)
    );
    assertThat(category.path("slug").textValue()).isEqualTo(selectedSlug(expectedId));
    assertThat(category.path("active").booleanValue()).isEqualTo(expectedId != SELECTED_ID);
  }

  private static void assertExactDetail(String selectedSystem, JsonNode category, long expectedId) {
    assertExactCategory(selectedSystem, category, expectedId);
    assertExactRankAndTimestamps(category, expectedId);
  }

  private static void assertExactRankAndTimestamps(JsonNode category, long expectedId) {
    assertThat(category.path("sortRank").longValue()).isEqualTo(expectedSortRank(expectedId));
    assertThat(category.path("createdAt").textValue()).isNotBlank();
    assertThat(category.path("updatedAt").textValue()).isNotBlank();
  }

  private static long expectedSortRank(long id) {
    return id == ROOT_ID || id == CHILD_ID
      ? 1_000_000_000L
      : id == PEER_ID
        ? 2_000_000_000L
        : 3_000_000_000L;
  }

  private static void assertLiteralSelectedSlice(String selectedSystem, JsonNode response) {
    JsonNode data = response.path("data");
    assertThat(jsonIds(data)).containsExactly(5L, 2L);
    for (int index = 0; index < 2; index++) {
      long id = index == 0 ? 5L : 2L;
      JsonNode option = data.get(index);
      String expectedName = selectedSystem + "-literal-" + (id == 5L ? "five" : "two");
      assertThat(option.path("id").longValue()).isEqualTo(id);
      assertThat(option.path("parentId").isNull()).isTrue();
      assertThat(option.path("name").textValue()).isEqualTo(expectedName);
      assertThat(option.path("slug").textValue()).isEqualTo("literal-" + id);
      assertThat(option.path("active").booleanValue()).isEqualTo(id == 2L);
      assertThat(option.path("depth").intValue()).isZero();
      assertThat(option.path("label").textValue()).isEqualTo(expectedName);
      assertThat(jsonIds(option.path("path"))).containsExactly(id);
      assertThat(option.path("path").get(0).path("name").textValue()).isEqualTo(expectedName);
      if (selectedSystem.equals(CATALOGUE) && id == 5L) {
        assertThat(option.path("selectable").booleanValue()).isFalse();
        assertThat(option.path("disabledReason").textValue()).isEqualTo("INACTIVE");
      } else {
        assertThat(option.path("selectable").booleanValue()).isTrue();
        assertThat(option.path("disabledReason").isNull()).isTrue();
      }
    }
    assertThat(response.path("hasMore").booleanValue()).isFalse();
    assertThat(response.path("nextCursor").isNull()).isTrue();
  }

  private static void assertExactBreadcrumb(
    String selectedSystem,
    JsonNode breadcrumb,
    List<Long> expectedIds
  ) {
    assertThat(jsonIds(breadcrumb)).containsExactlyElementsOf(expectedIds);
    for (int index = 0; index < expectedIds.size(); index++) {
      long id = expectedIds.get(index);
      assertExactCategory(selectedSystem, breadcrumb.get(index), id);
      assertExactRankAndTimestamps(breadcrumb.get(index), id);
      int expectedDepth = selectedSystem.equals(CATALOGUE) ? index : expectedIds.size() - index - 1;
      assertThat(breadcrumb.get(index).path("depth").intValue()).isEqualTo(expectedDepth);
    }
  }

  private static List<Long> jsonIds(JsonNode values) {
    List<Long> ids = new ArrayList<>();
    values.forEach(value -> ids.add(value.path("id").longValue()));
    return List.copyOf(ids);
  }

  private static String selectedName(String selectedSystem, long id) {
    String prefix = selectedSystem.equals(CATALOGUE) ? "catalogue" : "descriptor";
    return (
      prefix +
      switch ((int) (id - ROOT_ID)) {
        case 0 -> "-root";
        case 1 -> "-child";
        case 2 -> "-peer";
        case 3 -> "-selected";
        default -> throw new AssertionError("unexpected fixture id");
      }
    );
  }

  private static String selectedSlug(long id) {
    return switch ((int) (id - ROOT_ID)) {
      case 0 -> "shared-root";
      case 1 -> "shared-child";
      case 2 -> "shared-peer";
      case 3 -> "shared-selected";
      default -> throw new AssertionError("unexpected fixture id");
    };
  }

  private static Map<String, Object> rowById(StoreSnapshot snapshot, long id) {
    return snapshot
      .rows()
      .stream()
      .filter(row -> longValue(row.get("id")) == id)
      .findFirst()
      .orElseThrow();
  }

  private static Map<String, Object> rowBySlug(StoreSnapshot snapshot, String slug) {
    return snapshot
      .rows()
      .stream()
      .filter(row -> slug.equals(row.get("slug")))
      .findFirst()
      .orElseThrow();
  }

  private static void assertRow(
    Map<String, Object> row,
    Long expectedId,
    Long expectedParentId,
    String expectedName,
    String expectedSlug,
    boolean expectedActive
  ) {
    if (expectedId != null) {
      assertThat(longValue(row.get("id"))).isEqualTo(expectedId);
    }
    assertThat(row.get("parent_id")).isEqualTo(expectedParentId);
    assertThat(row.get("name")).isEqualTo(expectedName);
    assertThat(row.get("slug")).isEqualTo(expectedSlug);
    assertThat(row.get("active")).isEqualTo(expectedActive);
  }

  private static boolean hasPath(StoreSnapshot snapshot, long ancestorId, long descendantId) {
    return snapshot
      .closureRows()
      .stream()
      .anyMatch(
        row ->
          longValue(row.get("ancestor_id")) == ancestorId &&
          longValue(row.get("descendant_id")) == descendantId
      );
  }

  private static long longValue(Object value) {
    return ((Number) value).longValue();
  }

  private static void assertSelectedBody(String selectedSystem, String body) {
    if (selectedSystem.equals(CATALOGUE)) {
      assertThat(body).contains(CATALOGUE).doesNotContain("descriptor-root", "descriptor-child");
    } else {
      assertThat(body).contains("descriptor").doesNotContain("catalogue-root", "catalogue-child");
    }
  }

  private void restoreOverlappingFixture() {
    jdbc.execute(
      "TRUNCATE TABLE catalogue_product_category, catalogue_category_closure, catalogue_category RESTART IDENTITY CASCADE"
    );
    jdbc.execute("TRUNCATE TABLE " + DESCRIPTOR_CLOSURE + ", " + DESCRIPTOR_TABLE);
    insertBaseline(CATALOGUE, "catalogue");
    insertBaseline(DESCRIPTOR, "descriptor");
    advanceSequence(CATALOGUE);
    advanceSequence(DESCRIPTOR);
  }

  private void insertBaseline(String system, String valuePrefix) {
    insertNode(system, ROOT_ID, null, 1_000_000_000L, valuePrefix + "-root", "shared-root", true);
    insertNode(
      system,
      CHILD_ID,
      ROOT_ID,
      1_000_000_000L,
      valuePrefix + "-child",
      "shared-child",
      true
    );
    insertNode(
      system,
      PEER_ID,
      ROOT_ID,
      2_000_000_000L,
      valuePrefix + "-peer",
      "shared-peer",
      true
    );
    insertNode(
      system,
      SELECTED_ID,
      ROOT_ID,
      3_000_000_000L,
      valuePrefix + "-selected",
      "shared-selected",
      false
    );
    insertClosure(system, ROOT_ID, ROOT_ID, 0);
    for (long id : List.of(CHILD_ID, PEER_ID, SELECTED_ID)) {
      insertClosure(system, id, id, 0);
      insertClosure(system, ROOT_ID, id, 1);
    }
  }

  private void insertOtherSystemCreateCollision(String selectedSystem) {
    insertOtherOnly(otherSystem(selectedSystem), "other-create-collision", "same-new-slug");
  }

  private void insertLiteralCursorFixture() {
    for (String system : List.of(CATALOGUE, DESCRIPTOR)) {
      insertNode(system, 2L, null, 2_000_000_000L, system + "-literal-two", "literal-2", true);
      insertNode(system, 5L, null, 5_000_000_000L, system + "-literal-five", "literal-5", false);
      insertNode(system, 9L, null, 9_000_000_000L, system + "-literal-nine", "literal-9", true);
      insertClosure(system, 2L, 2L, 0);
      insertClosure(system, 5L, 5L, 0);
      insertClosure(system, 9L, 9L, 0);
    }
  }

  private void insertOtherOnly(String system, String value) {
    insertOtherOnly(system, value, "other-only");
  }

  private void insertOtherOnly(String system, String value, String slug) {
    insertNode(system, OTHER_ONLY_ID, null, 4_000_000_000L, value, slug, true);
    insertClosure(system, OTHER_ONLY_ID, OTHER_ONLY_ID, 0);
  }

  private void insertNode(
    String system,
    long id,
    Long parentId,
    long sortRank,
    String name,
    String slug,
    boolean active
  ) {
    if (system.equals(CATALOGUE)) {
      jdbc.update(
        """
        INSERT INTO catalogue_category (id, parent_id, sort_rank, name, slug, is_active)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        id,
        parentId,
        sortRank,
        name,
        slug,
        active
      );
    } else {
      jdbc.update(
        """
        INSERT INTO test_cross_descriptor_category
          (id, parent_id, sort_rank, name, slug, active)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        id,
        parentId,
        sortRank,
        name,
        slug,
        active
      );
    }
  }

  private void insertClosure(String system, long ancestorId, long descendantId, int depth) {
    String table = system.equals(CATALOGUE) ? "catalogue_category_closure" : DESCRIPTOR_CLOSURE;
    jdbc.update(
      "INSERT INTO " + table + " (ancestor_id, descendant_id, depth) VALUES (?, ?, ?)",
      ancestorId,
      descendantId,
      depth
    );
  }

  private void advanceSequence(String system) {
    String table = system.equals(CATALOGUE) ? "catalogue_category" : DESCRIPTOR_TABLE;
    jdbc.queryForObject(
      "SELECT setval(pg_get_serial_sequence(?, 'id'), ?, true)",
      Long.class,
      table,
      OTHER_ONLY_ID
    );
  }

  private StoreSnapshot snapshot(String system) {
    String table = system.equals(CATALOGUE) ? "catalogue_category" : DESCRIPTOR_TABLE;
    String closure = system.equals(CATALOGUE) ? "catalogue_category_closure" : DESCRIPTOR_CLOSURE;
    String activeColumn = system.equals(CATALOGUE) ? "is_active" : "active";
    List<Map<String, Object>> rows = jdbc.queryForList(
      "SELECT id, parent_id, sort_rank, name, slug, " +
        activeColumn +
        " AS active, created_at, updated_at FROM " +
        table +
        " ORDER BY id"
    );
    List<Map<String, Object>> paths = jdbc.queryForList(
      "SELECT ancestor_id, descendant_id, depth FROM " +
        closure +
        " ORDER BY ancestor_id, descendant_id"
    );
    return new StoreSnapshot(rows, paths);
  }

  private void dropDescriptorSchema() {
    jdbc.execute("DROP TABLE IF EXISTS " + DESCRIPTOR_CLOSURE);
    jdbc.execute("DROP TABLE IF EXISTS " + DESCRIPTOR_TABLE);
  }

  private static String base(String system) {
    return "/api/v1/admin/category-systems/" + system + "/categories";
  }

  private static String otherSystem(String system) {
    return system.equals(CATALOGUE) ? DESCRIPTOR : CATALOGUE;
  }

  private static String otherPrefix(String system) {
    return system.equals(CATALOGUE) ? "descriptor-" : "catalogue-";
  }

  private static String permission(String system, RouteOperation operation) {
    return "admin:" + system + "-categories:" + (MUTATIONS.contains(operation) ? "write" : "read");
  }

  private static RequestPostProcessor readJwt(String system) {
    return authorityJwt("admin:" + system + "-categories:read");
  }

  private static RequestPostProcessor writeJwt(String system) {
    return authorityJwt("admin:" + system + "-categories:write");
  }

  private static RequestPostProcessor authorityJwt(String authority) {
    return jwt().authorities(new SimpleGrantedAuthority(authority));
  }

  private static String categoryJson(Long parentId, String name, String slug, boolean active) {
    return """
    {
      "parentId": %s,
      "name": "%s",
      "slug": "%s",
      "active": %s
    }
    """.formatted(parentId, name, slug, active);
  }

  private enum SelectedErrorScenario {
    DUPLICATE_SLUG(RouteOperation.CREATE, 409, "DUPLICATE_CATEGORY_SLUG"),
    MISSING_PARENT(RouteOperation.CREATE, 404, "CATEGORY_PARENT_NOT_FOUND"),
    INVALID_MOVE(RouteOperation.MOVE, 400, "INVALID_CATEGORY_MOVE"),
    INVALID_PLACEMENT(RouteOperation.REORDER, 400, "INVALID_CATEGORY_PLACEMENT_TARGET"),
    DELETE_BLOCKED(RouteOperation.DELETE, 409, "CATEGORY_DELETE_BLOCKED");

    private final RouteOperation operation;
    private final int status;
    private final String errorCode;

    SelectedErrorScenario(RouteOperation operation, int status, String errorCode) {
      this.operation = operation;
      this.status = status;
      this.errorCode = errorCode;
    }
  }

  private enum RouteOperation {
    CAPABILITIES("definition-only", 200),
    LIST("category-read.list", 200),
    TREE("hierarchy-read.tree", 200),
    PARENT_OPTIONS("hierarchy-read.parentOptions", 200),
    GET("category-read.getNode", 200),
    BREADCRUMB("hierarchy-read.breadcrumb", 200),
    CREATE("basic-mutation.create", 201),
    REPLACE("basic-mutation.replace", 200),
    PATCH("basic-mutation.patch", 200),
    DELETE("basic-mutation.delete", 204),
    MOVE("hierarchy-mutation.move", 204),
    REORDER("ordering.reorder", 200),
    ACTIVATE("lifecycle.activate", 204),
    DEACTIVATE("lifecycle.deactivate", 204);

    private final String portMethod;
    private final int expectedStatus;

    RouteOperation(String portMethod, int expectedStatus) {
      this.portMethod = portMethod;
      this.expectedStatus = expectedStatus;
    }
  }

  private record StoreSnapshot(
    List<Map<String, Object>> rows,
    List<Map<String, Object>> closureRows
  ) {}

  private record RouteResult(int status, String body) {}

  private record RouteIsolationObservation(
    String selectedSystem,
    RouteOperation operation,
    String permission,
    String portMethod,
    String storeFamily,
    StoreSnapshot selectedBefore,
    StoreSnapshot selectedAfter,
    StoreSnapshot otherBefore,
    StoreSnapshot otherAfter,
    int status,
    String stableError,
    List<PortCall> portCalls,
    List<String> sqlStatements,
    int repositoryInvocationCount,
    int forbiddenInteractionCount
  ) {}

  private static final class RuntimeObservationProbe {

    private final List<PortCall> portCalls = Collections.synchronizedList(new ArrayList<>());
    private String failAfterSystem;
    private String failAfterPortMethod;
    private Runnable beforeFailure = () -> {};

    void record(String systemKey, String portMethod) {
      portCalls.add(new PortCall(systemKey, portMethod));
    }

    List<PortCall> portCalls() {
      synchronized (portCalls) {
        return List.copyOf(portCalls);
      }
    }

    void failAfter(String systemKey, String portMethod, Runnable beforeFailure) {
      failAfterSystem = systemKey;
      failAfterPortMethod = portMethod;
      this.beforeFailure = beforeFailure;
    }

    boolean shouldFailAfter(String systemKey, String portMethod) {
      return systemKey.equals(failAfterSystem) && portMethod.equals(failAfterPortMethod);
    }

    void beforeFailure() {
      beforeFailure.run();
    }

    void reset() {
      portCalls.clear();
      failAfterSystem = null;
      failAfterPortMethod = null;
      beforeFailure = () -> {};
    }
  }

  private record PortCall(String systemKey, String portMethod) {}

  private static final class RecordingPortInvocationHandler implements InvocationHandler {

    private final String systemKey;
    private final String capability;
    private final AdminCategoryOperationPort delegate;

    private RecordingPortInvocationHandler(
      String systemKey,
      String capability,
      AdminCategoryOperationPort delegate
    ) {
      this.systemKey = systemKey;
      this.capability = capability;
      this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return invokeDelegate(method, arguments);
      }
      String portMethod = capability + "." + method.getName();
      RUNTIME_OBSERVATIONS.record(systemKey, portMethod);
      Object result = invokeDelegate(method, arguments);
      if (RUNTIME_OBSERVATIONS.shouldFailAfter(systemKey, portMethod)) {
        RUNTIME_OBSERVATIONS.beforeFailure();
        throw new ControlledRollback();
      }
      return result;
    }

    private Object invokeDelegate(Method method, Object[] arguments) throws Throwable {
      try {
        return method.invoke(delegate, arguments);
      } catch (InvocationTargetException exception) {
        throw exception.getCause();
      }
    }
  }

  private static final class SqlObservationProbe {

    private final List<String> statements = Collections.synchronizedList(new ArrayList<>());

    void record(String sql) {
      if (sql != null && !sql.isBlank()) {
        statements.add(sql.toLowerCase(java.util.Locale.ROOT));
      }
    }

    List<String> statements() {
      synchronized (statements) {
        return List.copyOf(statements);
      }
    }

    void reset() {
      statements.clear();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ObservationConfiguration {

    @Bean
    static BeanPostProcessor observedDataSourcePostProcessor() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (bean instanceof DataSource dataSource && !(bean instanceof ObservedDataSource)) {
            return new ObservedDataSource(dataSource);
          }
          return bean;
        }
      };
    }
  }

  private static final class ObservedDataSource implements DataSource {

    private final DataSource delegate;

    private ObservedDataSource(DataSource delegate) {
      this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
      return observedConnection(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return observedConnection(delegate.getConnection(username, password));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
      return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
      delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
      delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
      return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
      return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    private static Connection observedConnection(Connection connection) {
      return (Connection) Proxy.newProxyInstance(
        Connection.class.getClassLoader(),
        new Class<?>[] { Connection.class },
        (proxy, method, arguments) -> {
          Object result = invokeReflectively(connection, method, arguments);
          if (result instanceof Statement statement) {
            String preparedSql =
              arguments != null && arguments.length > 0 && arguments[0] instanceof String sql
                ? sql
                : null;
            return observedStatement(statement, preparedSql);
          }
          return result;
        }
      );
    }

    private static Statement observedStatement(Statement statement, String preparedSql) {
      Class<?> statementType = statement instanceof CallableStatement
        ? CallableStatement.class
        : statement instanceof PreparedStatement
          ? PreparedStatement.class
          : Statement.class;
      return (Statement) Proxy.newProxyInstance(
        Statement.class.getClassLoader(),
        new Class<?>[] { statementType },
        (proxy, method, arguments) -> {
          if (method.getName().startsWith("execute")) {
            String sql =
              preparedSql != null
                ? preparedSql
                : arguments != null &&
                  arguments.length > 0 &&
                  arguments[0] instanceof String statementSql
                  ? statementSql
                  : null;
            SQL_OBSERVATIONS.record(sql);
          }
          return invokeReflectively(statement, method, arguments);
        }
      );
    }

    private static Object invokeReflectively(Object target, Method method, Object[] arguments)
      throws Throwable {
      try {
        return method.invoke(target, arguments);
      } catch (InvocationTargetException exception) {
        throw exception.getCause();
      }
    }
  }

  private static final class ControlledRollback extends RuntimeException {}
}
