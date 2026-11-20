package com.ferozfaiz.uniapi.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import com.ferozfaiz.uniapi.admincategorycontractfixture.NeutralAdminCategoryDefinitions;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.DeletionPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.GeneralValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.HierarchyPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.LifecyclePolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.MutationValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.OrderingPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.QueryPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryApiProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryLogicalPolicyId;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReference;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleState;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryContractAssertions;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryOperationMatrixCase;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminCategoryCapabilityDispatchMatrixTest {

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
  private static final List<ProfileSpec> PROFILES = List.of(
    new ProfileSpec(
      "CAT",
      "catalogue",
      AdminCategoryRuntimeTestFixture.completeDefinition("catalogue", Set.of(Operation.values()))
    ),
    new ProfileSpec(
      "DES",
      "descriptor-a",
      AdminCategoryRuntimeTestFixture.completeDefinition("descriptor-a", Set.of(Operation.values()))
    ),
    new ProfileSpec("FLT", "neutral-flat", NeutralAdminCategoryDefinitions.flatDefinition()),
    new ProfileSpec(
      "RST",
      "neutral-restricted",
      NeutralAdminCategoryDefinitions.restrictedDefinition()
    )
  );
  private static final Map<Operation, String> SUCCESS_RESULTS = Map.ofEntries(
    Map.entry(Operation.CAPABILITIES, "200 AdminCategoryCapabilityResponse"),
    Map.entry(Operation.LIST, "200 PageResponse<AdminCategoryListItemResponse>"),
    Map.entry(Operation.TREE, "200 PageResponse<AdminCategoryTreeItemResponse>"),
    Map.entry(Operation.PARENT_OPTIONS, "200 AdminCategoryParentOptionSliceResponse"),
    Map.entry(Operation.GET, "200 AdminCategoryDetailResponse"),
    Map.entry(Operation.BREADCRUMB, "200 root-to-selected breadcrumb array"),
    Map.entry(Operation.CREATE, "201 AdminCategoryDetailResponse plus frozen absolute Location"),
    Map.entry(Operation.REPLACE, "200 AdminCategoryDetailResponse"),
    Map.entry(Operation.PATCH, "200 AdminCategoryDetailResponse"),
    Map.entry(Operation.DELETE, "204 empty body"),
    Map.entry(Operation.MOVE, "204 empty body"),
    Map.entry(Operation.REORDER, "200 AdminCategoryDetailResponse"),
    Map.entry(Operation.ACTIVATE, "204 empty body"),
    Map.entry(Operation.DEACTIVATE, "204 empty body")
  );
  private static final Set<String> ALL_PROFILE_PORT_CALLS = allProfilePortCalls();
  private static final List<AdminCategoryOperationMatrixCase> MATRIX =
    AdminCategoryOperationMatrixCase.generateDeterministic(
      PROFILES,
      OPERATIONS,
      AdminCategoryCapabilityDispatchMatrixTest::matrixCase
    );
  private static final List<DispatchCell> MANIFEST = IntStream.range(0, MATRIX.size())
    .mapToObj(index -> new DispatchCell("CD-%03d".formatted(index + 1), MATRIX.get(index)))
    .toList();

  private List<DispatchHarness> harnesses;
  private TestRuntime runtime;
  private StructuredLogCapture logs;

  @BeforeAll
  void startRealRegistrationRuntime() {
    assertExactManifest();
    harnesses = PROFILES.stream().map(DispatchHarness::create).toList();
    runtime = TestRuntime.start(harnesses);
    logs = StructuredLogCapture.attach(AdminCategoryApplicationService.class);
  }

  @AfterAll
  void closeRealRegistrationRuntime() {
    if (logs != null) {
      logs.close();
    }
    if (runtime != null) {
      runtime.close();
    }
  }

  @Test
  void manifestSelfCountsAllExactCellsBeforeDispatch() {
    assertExactManifest();
  }

  @TestFactory
  List<DynamicTest> exactFourProfileDispatchManifest() {
    assertExactManifest();
    return MANIFEST.stream()
      .map(cell ->
        DynamicTest.dynamicTest(
          cell.cellId() + " " + cell.row().profileOrSystem() + " " + cell.row().operation(),
          () -> assertDispatch(cell)
        )
      )
      .toList();
  }

  @Test
  void activateOnlyLifecycleRegistrationRejectsUndeclaredSiblingBeforeBoundPort() {
    ProfileSpec profile = new ProfileSpec("ACT", "activate-only", activateOnlyDefinition());
    DispatchHarness harness = DispatchHarness.create(profile);
    try (
      TestRuntime lifecycleRuntime = TestRuntime.start(List.of(harness));
      StructuredLogCapture lifecycleLogs = StructuredLogCapture.attach(
        AdminCategoryApplicationService.class
      )
    ) {
      assertThat(
        harness.registration().runtimeBinding().orElseThrow().ports().lifecycle()
      ).containsSame(harness.ports().lifecycle());
      assertThat(
        harness.registration().runtimeBinding().orElseThrow().ports().populatedCapabilities()
      ).containsExactly(
        com.ferozfaiz.uniapi.category.application.registration.AdminCategoryProviderCapability.LIFECYCLE
      );

      clearInteractions(List.of(harness), lifecycleRuntime.registry());
      assertThatCode(() ->
        lifecycleRuntime.service().activate(profile.systemKey(), 1L)
      ).doesNotThrowAnyException();
      verify(harness.ports().lifecycle()).activate(1L);
      verify(harness.ports().lifecycle(), org.mockito.Mockito.never()).deactivate(any());

      clearInteractions(List.of(harness), lifecycleRuntime.registry());
      lifecycleLogs.clear();
      Throwable failure = catchThrowable(() ->
        lifecycleRuntime.service().deactivate(profile.systemKey(), 1L)
      );

      assertUnsupported(failure, Operation.DEACTIVATE, profile.systemKey());
      assertThat(portCalls(List.of(harness))).isEmpty();
      assertThat(mockingDetails(harness.policy()).getInvocations()).isEmpty();
      verify(lifecycleRuntime.registry()).findRegistration(profile.systemKey());
      verifyNoMoreInteractions(lifecycleRuntime.registry());
      assertUnsupportedLog(lifecycleLogs, Operation.DEACTIVATE, profile.systemKey());
    }
  }

  private void assertDispatch(DispatchCell cell) {
    AdminCategoryOperationMatrixCase row = cell.row();
    ProfileSpec profile = profile(row.profileOrSystem());
    DispatchHarness harness = harness(profile.code());
    clearInteractions(harnesses, runtime.registry());
    logs.clear();

    Throwable failure = null;
    String actualResult = null;
    try {
      actualResult = invoke(runtime.service(), harness.definition(), row.operation());
    } catch (Throwable thrown) {
      failure = thrown;
    }

    if (row.declaredCapability()) {
      assertThat(failure).isNull();
      assertThat(actualResult).isEqualTo(row.expectedStatusOrResult());
      assertSupportedLog(logs);
    } else {
      assertUnsupported(failure, row.operation(), profile.systemKey());
      assertThat("409 CATEGORY_CAPABILITY_UNSUPPORTED").isEqualTo(row.expectedStatusOrResult());
      assertThat(mockingDetails(harness.policy()).getInvocations()).isEmpty();
      assertUnsupportedLog(logs, row.operation(), profile.systemKey());
    }

    List<String> actualCalls = portCalls(harnesses);
    assertThat(actualCalls).containsExactlyElementsOf(row.expectedPortCalls());
    assertThat(actualCalls).doesNotContainAnyElementsOf(row.forbiddenInteractions());
    verify(runtime.registry()).findRegistration(profile.systemKey());
    verifyNoMoreInteractions(runtime.registry());
    if (row.operation() == Operation.CAPABILITIES) {
      assertThat(actualCalls).isEmpty();
      assertThat(mockingDetails(harness.policy()).getInvocations()).isEmpty();
    }
  }

  private static void assertExactManifest() {
    AdminCategoryContractAssertions.assertMatrixManifest("capability-dispatch", MATRIX, 56);
    assertThat(Operation.values()).containsExactlyElementsOf(OPERATIONS);
    assertThat(MANIFEST).hasSize(56);
    assertThat(MANIFEST)
      .extracting(DispatchCell::cellId)
      .containsExactlyElementsOf(
        IntStream.rangeClosed(1, 56)
          .mapToObj(index -> "CD-%03d".formatted(index))
          .toList()
      )
      .doesNotHaveDuplicates();
    assertThat(MANIFEST)
      .extracting(cell -> cell.row().caseId())
      .doesNotHaveDuplicates();
    assertThat(MANIFEST)
      .extracting(cell -> cell.row().profileOrSystem() + ":" + cell.row().operation())
      .doesNotHaveDuplicates();
    assertProfileAccounting("CAT/catalogue", 14, 0);
    assertProfileAccounting("DES/descriptor-a", 14, 0);
    assertProfileAccounting("FLT/neutral-flat", 3, 11);
    assertProfileAccounting("RST/neutral-restricted", 3, 11);
    assertThat(MANIFEST.stream().filter(cell -> cell.row().declaredCapability())).hasSize(34);
    assertThat(MANIFEST.stream().filter(cell -> !cell.row().declaredCapability())).hasSize(22);
    assertThat(MANIFEST.stream().filter(cell -> hasNotApplicableValue(cell.row()))).isEmpty();
    assertThat(MANIFEST).allSatisfy(cell -> {
      assertThat(cell.row().expectedPortCalls()).hasSizeLessThanOrEqualTo(1);
      assertThat(cell.row().forbiddenInteractions()).contains("provider.fallback");
      if (cell.row().operation() == Operation.CAPABILITIES) {
        assertThat(cell.row().expectedPortCalls()).isEmpty();
      }
    });
  }

  private static void assertProfileAccounting(
    String profileOrSystem,
    int declared,
    int unsupported
  ) {
    List<AdminCategoryOperationMatrixCase> rows = MATRIX.stream()
      .filter(row -> profileOrSystem.equals(row.profileOrSystem()))
      .toList();
    assertThat(rows).hasSize(14);
    assertThat(rows.stream().filter(AdminCategoryOperationMatrixCase::declaredCapability)).hasSize(
      declared
    );
    assertThat(rows.stream().filter(row -> !row.declaredCapability())).hasSize(unsupported);
    assertThat(rows)
      .extracting(AdminCategoryOperationMatrixCase::operation)
      .containsExactlyElementsOf(OPERATIONS);
  }

  private static boolean hasNotApplicableValue(AdminCategoryOperationMatrixCase row) {
    return (
      List.of(
        row.profileOrSystem(),
        row.authenticationState(),
        row.authorityState(),
        row.inputCondition(),
        row.expectedStatusOrResult(),
        row.expectedStableError(),
        row.expectedLogOutcome(),
        row.applicabilityEvidence()
      )
        .stream()
        .anyMatch("N/A"::equalsIgnoreCase) ||
      row.expectedPortCalls().stream().anyMatch("N/A"::equalsIgnoreCase) ||
      row.forbiddenInteractions().stream().anyMatch("N/A"::equalsIgnoreCase)
    );
  }

  private static AdminCategoryOperationMatrixCase matrixCase(
    ProfileSpec profile,
    Operation operation
  ) {
    boolean declared = profile.definition().operations().supportedOperations().contains(operation);
    Optional<String> canonicalCall = AdminCategoryProviderContractProfile.canonicalCall(
      operation
    ).map(call -> profile.code() + "/" + call);
    List<String> expectedCalls = declared ? canonicalCall.stream().toList() : List.of();
    LinkedHashSet<String> forbidden = new LinkedHashSet<>(ALL_PROFILE_PORT_CALLS);
    forbidden.removeAll(expectedCalls);
    forbidden.add("provider.fallback");
    forbidden.add("legacy-provider-registry");
    forbidden.add("legacy-provider-source");
    forbidden.add("storage-descriptor-registry");
    if (!declared) {
      forbidden.add("definition-validation");
      forbidden.add("identifier-conversion");
      forbidden.add("cursor-decoding");
      forbidden.add("resource-lookup");
      forbidden.add("repository-or-sql");
      forbidden.add("lock-event-or-mutation");
    }
    return new AdminCategoryOperationMatrixCase(
      profile.profileOrSystem(),
      operation,
      declared,
      "authenticated",
      declared ? "AUTH-X" : "AUTH-P",
      "V(" + operation.name() + ")",
      declared ? SUCCESS_RESULTS.get(operation) : "409 CATEGORY_CAPABILITY_UNSUPPORTED",
      declared ? "none" : "CATEGORY_CAPABILITY_UNSUPPORTED",
      expectedCalls,
      List.copyOf(forbidden),
      declared ? "LOG-S" : "LOG-U",
      declared
        ? "selected definition explicitly declares " + operation.code()
        : "selected definition omits " + operation.code() + " and requires stable rejection"
    );
  }

  private static Set<String> allProfilePortCalls() {
    LinkedHashSet<String> calls = new LinkedHashSet<>();
    PROFILES.forEach(profile ->
      OPERATIONS.forEach(operation ->
        AdminCategoryProviderContractProfile.canonicalCall(operation).ifPresent(call ->
          calls.add(profile.code() + "/" + call)
        )
      )
    );
    return Set.copyOf(calls);
  }

  private static String invoke(
    AdminCategoryApplicationService service,
    AdminCategorySystemDefinition definition,
    Operation operation
  ) {
    String systemKey = definition.systemKey();
    Object result = switch (operation) {
      case CAPABILITIES -> service.capabilities(systemKey);
      case LIST -> service.list(
        systemKey,
        new AdminCategoryListValidationInput(
          definition,
          null,
          null,
          null,
          null,
          null,
          null,
          List.of(),
          null
        )
      );
      case TREE -> service.tree(
        systemKey,
        new AdminCategoryTreeValidationInput(definition, null, null, List.of(), null)
      );
      case PARENT_OPTIONS -> service.parentOptions(
        systemKey,
        new AdminCategoryParentOptionsValidationInput(
          definition,
          null,
          null,
          null,
          List.of(),
          null,
          null
        )
      );
      case GET -> service.get(systemKey, 1L);
      case BREADCRUMB -> service.breadcrumb(systemKey, 1L);
      case CREATE -> service.create(
        systemKey,
        new AdminCategoryCreateValidationInput(
          definition,
          null,
          null,
          null,
          null,
          "Name",
          "name",
          true
        )
      );
      case REPLACE -> service.update(
        systemKey,
        1L,
        new AdminCategoryReplaceValidationInput(definition, 1L, null, "Name", "name", true)
      );
      case PATCH -> service.patch(
        systemKey,
        1L,
        new AdminCategoryPatchValidationInput(
          definition,
          1L,
          null,
          false,
          "Updated",
          true,
          null,
          false,
          null,
          false
        )
      );
      case DELETE -> {
        service.delete(systemKey, 1L);
        yield null;
      }
      case MOVE -> {
        service.move(systemKey, 1L, placement(definition, Operation.MOVE));
        yield null;
      }
      case REORDER -> service.reorder(systemKey, 1L, placement(definition, Operation.REORDER));
      case ACTIVATE -> {
        service.activate(systemKey, 1L);
        yield null;
      }
      case DEACTIVATE -> {
        service.deactivate(systemKey, 1L);
        yield null;
      }
    };
    assertSuccessfulResultType(operation, definition, result);
    return SUCCESS_RESULTS.get(operation);
  }

  private static void assertSuccessfulResultType(
    Operation operation,
    AdminCategorySystemDefinition definition,
    Object result
  ) {
    switch (operation) {
      case CAPABILITIES -> assertThat(result).isSameAs(definition);
      case LIST, TREE -> assertThat(result).isInstanceOf(PageResult.class);
      case PARENT_OPTIONS -> assertThat(result).isInstanceOf(
        AdminCategoryModels.ParentOptionSlice.class
      );
      case GET, CREATE, REPLACE, PATCH, REORDER -> assertThat(result).isInstanceOf(
        AdminCategoryModels.Category.class
      );
      case BREADCRUMB -> assertThat(result).isInstanceOf(List.class);
      case DELETE, MOVE, ACTIVATE, DEACTIVATE -> assertThat(result).isNull();
    }
  }

  private static AdminCategoryPlacementValidationInput placement(
    AdminCategorySystemDefinition definition,
    Operation operation
  ) {
    return new AdminCategoryPlacementValidationInput(definition, operation, null, null, null, null);
  }

  private static void assertUnsupported(Throwable failure, Operation operation, String systemKey) {
    assertThat(failure).isInstanceOf(AdminCategoryException.class);
    AdminCategoryException exception = (AdminCategoryException) failure;
    assertThat(exception.status().value()).isEqualTo(409);
    assertThat(exception.code()).isEqualTo("CATEGORY_CAPABILITY_UNSUPPORTED");
    assertThat(exception.messageKey()).isEqualTo("category.application.capability.unsupported");
    assertThat(exception.messageArgs()).containsExactly(operation.code(), systemKey);
  }

  private static void assertSupportedLog(StructuredLogCapture capture) {
    assertThat(capture.events()).noneSatisfy(event ->
      assertThat(structuredFields(event)).containsEntry("category.event", "capability.unsupported")
    );
  }

  private static void assertUnsupportedLog(
    StructuredLogCapture capture,
    Operation operation,
    String systemKey
  ) {
    StructuredLogCapture.CapturedEvent event = capture.singleEvent();
    assertThat(event.level()).isSameAs(Level.WARN);
    assertThat(event.message()).isEqualTo("admin category capability unsupported");
    assertThat(event.attachedThrowable()).isEmpty();
    assertThat(structuredFields(event)).containsExactlyInAnyOrderEntriesOf(
      Map.of(
        "category.event",
        "capability.unsupported",
        "systemKey",
        systemKey,
        "operation",
        operation.code(),
        "outcome",
        "failed",
        "errorCode",
        "CATEGORY_CAPABILITY_UNSUPPORTED"
      )
    );
  }

  private static Map<String, String> structuredFields(StructuredLogCapture.CapturedEvent event) {
    LinkedHashMap<String, String> fields = new LinkedHashMap<>();
    event.structuredFields().forEach(field -> fields.put(field.key(), field.value()));
    return Map.copyOf(fields);
  }

  private ProfileSpec profile(String profileOrSystem) {
    return PROFILES.stream()
      .filter(profile -> profile.profileOrSystem().equals(profileOrSystem))
      .findFirst()
      .orElseThrow();
  }

  private DispatchHarness harness(String profileCode) {
    return harnesses
      .stream()
      .filter(harness -> harness.profile().code().equals(profileCode))
      .findFirst()
      .orElseThrow();
  }

  private static void clearInteractions(
    List<DispatchHarness> dispatchHarnesses,
    AdminCategorySystemRegistry registry
  ) {
    List<Object> mocks = new ArrayList<>();
    dispatchHarnesses.forEach(harness -> {
      mocks.addAll(harness.ports().all());
      mocks.add(harness.policy());
    });
    mocks.add(registry);
    clearInvocations(mocks.toArray());
  }

  private static List<String> portCalls(List<DispatchHarness> dispatchHarnesses) {
    List<String> result = new ArrayList<>();
    dispatchHarnesses.forEach(harness ->
      harness
        .ports()
        .named()
        .forEach(port ->
          mockingDetails(port.mock())
            .getInvocations()
            .forEach(invocation ->
              result.add(
                harness.profile().code() +
                  "/" +
                  port.slot() +
                  "." +
                  invocation.getMethod().getName()
              )
            )
        )
    );
    return List.copyOf(result);
  }

  private static AdminCategorySystemDefinition activateOnlyDefinition() {
    return new AdminCategorySystemDefinition(
      "activate-only",
      "contract-tests",
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC,
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
      new OperationDeclaration(
        List.of(Operation.CAPABILITIES, Operation.ACTIVATE),
        List.of(
          AdminCategoryPolicyObligation.GENERAL_VALIDATION,
          AdminCategoryPolicyObligation.LIFECYCLE
        )
      ),
      new HierarchyDeclaration(false, Optional.empty(), Optional.empty(), List.of()),
      new OrderingDeclaration(false, false, false, false, List.of(), List.of()),
      new LifecycleDeclaration(
        true,
        List.of(LifecycleState.ACTIVE, LifecycleState.INACTIVE),
        List.of(Operation.ACTIVATE)
      ),
      new FieldQueryDeclaration(
        List.of(
          LogicalField.IDENTIFIER,
          LogicalField.NAME,
          LogicalField.SLUG,
          LogicalField.ACTIVE,
          LogicalField.CREATED_AT,
          LogicalField.UPDATED_AT
        ),
        List.of(),
        List.of(),
        List.of()
      ),
      new OperationalLimits(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
      )
    );
  }

  private record ProfileSpec(
    String code,
    String systemKey,
    AdminCategorySystemDefinition definition
  ) {
    private ProfileSpec {
      assertThat(code).isNotBlank();
      assertThat(systemKey).isEqualTo(definition.systemKey());
    }

    private String profileOrSystem() {
      return code + "/" + systemKey;
    }
  }

  private record DispatchCell(String cellId, AdminCategoryOperationMatrixCase row) {}

  private record NamedPort(String slot, Object mock) {}

  private record PortMocks(
    AdminCategoryReadPort categoryRead,
    AdminCategoryHierarchyReadPort hierarchyRead,
    AdminCategoryBasicMutationPort basicMutation,
    AdminCategoryHierarchyMutationPort hierarchyMutation,
    AdminCategoryOrderingPort ordering,
    AdminCategoryLifecyclePort lifecycle
  ) {
    private static PortMocks create() {
      return new PortMocks(
        mock(AdminCategoryReadPort.class),
        mock(AdminCategoryHierarchyReadPort.class),
        mock(AdminCategoryBasicMutationPort.class),
        mock(AdminCategoryHierarchyMutationPort.class),
        mock(AdminCategoryOrderingPort.class),
        mock(AdminCategoryLifecyclePort.class)
      );
    }

    private List<Object> all() {
      return List.of(
        categoryRead,
        hierarchyRead,
        basicMutation,
        hierarchyMutation,
        ordering,
        lifecycle
      );
    }

    private List<NamedPort> named() {
      return List.of(
        new NamedPort("category-read", categoryRead),
        new NamedPort("hierarchy-read", hierarchyRead),
        new NamedPort("basic-mutation", basicMutation),
        new NamedPort("hierarchy-mutation", hierarchyMutation),
        new NamedPort("ordering", ordering),
        new NamedPort("lifecycle", lifecycle)
      );
    }
  }

  private record DispatchHarness(
    ProfileSpec profile,
    AdminCategorySystemDefinition definition,
    AdminCategorySystemRegistration registration,
    PortMocks ports,
    AdminCategoryDefinitionValidator policy
  ) {
    private static DispatchHarness create(ProfileSpec profile) {
      PortMocks ports = PortMocks.create();
      AdminCategoryDefinitionValidator policy = spy(new AdminCategoryDefinitionValidator());
      AdminCategoryRuntimeBinding binding = new AdminCategoryRuntimeBinding(
        numericCodec(),
        permissions(profile.definition()),
        policies(profile.definition(), policy),
        runtimePorts(profile.definition(), ports)
      );
      AdminCategorySystemRegistration registration = AdminCategorySystemRegistration.of(
        profile.definition(),
        binding
      );
      stubSuccesses(ports);
      return new DispatchHarness(profile, profile.definition(), registration, ports, policy);
    }

    private static AdminCategoryRuntimePorts runtimePorts(
      AdminCategorySystemDefinition definition,
      PortMocks ports
    ) {
      Set<Operation> operations = definition.operations().supportedOperations();
      return new AdminCategoryRuntimePorts(
        optionalPort(operations, Set.of(Operation.LIST, Operation.GET), ports.categoryRead()),
        optionalPort(
          operations,
          Set.of(Operation.TREE, Operation.PARENT_OPTIONS, Operation.BREADCRUMB),
          ports.hierarchyRead()
        ),
        optionalPort(
          operations,
          Set.of(Operation.CREATE, Operation.REPLACE, Operation.PATCH, Operation.DELETE),
          ports.basicMutation()
        ),
        optionalPort(operations, Set.of(Operation.MOVE), ports.hierarchyMutation()),
        optionalPort(operations, Set.of(Operation.REORDER), ports.ordering()),
        optionalPort(
          operations,
          Set.of(Operation.ACTIVATE, Operation.DEACTIVATE),
          ports.lifecycle()
        )
      );
    }

    private static <T> Optional<T> optionalPort(
      Set<Operation> declared,
      Set<Operation> mappedOperations,
      T port
    ) {
      return declared.stream().anyMatch(mappedOperations::contains)
        ? Optional.of(port)
        : Optional.empty();
    }

    private static AdminCategoryPermissionMapping permissions(
      AdminCategorySystemDefinition definition
    ) {
      return new AdminCategoryPermissionMapping(
        definition
          .operations()
          .supportedOperations()
          .stream()
          .map(operation ->
            new Binding(
              operation,
              "admin:" +
                definition.systemKey() +
                "-categories:" +
                (READ_OPERATIONS.contains(operation) ? "read" : "write")
            )
          )
          .toList()
      );
    }

    private static AdminCategoryRuntimePolicyBindings policies(
      AdminCategorySystemDefinition definition,
      AdminCategoryDefinitionValidator policy
    ) {
      List<AdminCategoryRuntimePolicyBinding> bindings = definition
        .operations()
        .requiredPolicyObligations()
        .stream()
        .sorted()
        .map(obligation -> policyBinding(definition.systemKey(), obligation, policy))
        .toList();
      return new AdminCategoryRuntimePolicyBindings(bindings);
    }

    private static AdminCategoryRuntimePolicyBinding policyBinding(
      String systemKey,
      AdminCategoryPolicyObligation obligation,
      AdminCategoryDefinitionValidator policy
    ) {
      AdminCategoryPolicyReference reference = new AdminCategoryPolicyReference(
        obligation,
        new AdminCategoryLogicalPolicyId("dispatch-matrix", systemKey + "-" + obligation.code())
      );
      return switch (obligation) {
        case GENERAL_VALIDATION -> new GeneralValidationPolicyBinding(reference, policy);
        case QUERY -> new QueryPolicyBinding(reference, policy);
        case HIERARCHY -> new HierarchyPolicyBinding(reference, policy);
        case MUTATION_VALIDATION -> new MutationValidationPolicyBinding(reference, policy);
        case ORDERING -> new OrderingPolicyBinding(reference, policy);
        case DELETION -> new DeletionPolicyBinding(reference, policy);
        case LIFECYCLE -> new LifecyclePolicyBinding(reference, policy);
      };
    }

    private static AdminCategoryIdentifierCodec<Long> numericCodec() {
      return new AdminCategoryIdentifierCodec<>() {
        @Override
        public AdminCategoryIdentifierProfile identifierProfile() {
          return AdminCategoryIdentifierProfile.NUMERIC_LONG_V1;
        }

        @Override
        public Long parse(String value) {
          return Long.valueOf(value);
        }

        @Override
        public String serialize(Long value) {
          return value.toString();
        }
      };
    }

    private static void stubSuccesses(PortMocks ports) {
      AdminCategoryModels.Category category = category();
      AdminCategoryModels.CategoryNode node = new AdminCategoryModels.CategoryNode(
        category,
        0,
        false
      );
      PageResult<AdminCategoryModels.CategoryNode> page = new PageResult<>(
        List.of(node),
        0,
        20,
        1,
        1,
        true,
        true,
        SortCriteria.of("id", SortDirection.ASC)
      );
      when(ports.categoryRead().list(any())).thenReturn(page);
      when(ports.categoryRead().getNode(1L)).thenReturn(node);
      when(ports.hierarchyRead().tree(any())).thenReturn(page);
      when(ports.hierarchyRead().parentOptions(any())).thenReturn(
        new AdminCategoryModels.ParentOptionSlice(List.of(), false, null)
      );
      when(ports.hierarchyRead().breadcrumb(1L)).thenReturn(List.of());
      when(ports.basicMutation().create(any())).thenReturn(category);
      when(ports.basicMutation().replace(eq(1L), any())).thenReturn(category);
      when(ports.basicMutation().patch(eq(1L), any())).thenReturn(category);
      when(ports.ordering().reorder(eq(1L), any())).thenReturn(category);
      clearInvocations(ports.all().toArray());
    }

    private static AdminCategoryModels.Category category() {
      OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-16T00:00:00Z");
      return new AdminCategoryModels.Category(
        1L,
        null,
        null,
        "Name",
        "name",
        true,
        timestamp,
        timestamp
      );
    }
  }

  private record TestRuntime(
    AnnotationConfigApplicationContext context,
    AdminCategorySystemRegistry registry,
    AdminCategoryApplicationService service
  ) implements AutoCloseable {
    private static TestRuntime start(List<DispatchHarness> harnesses) {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      context.register(registrationConfiguration());
      harnesses.forEach(harness ->
        context.registerBean(
          harness.profile().code().toLowerCase() + "Registration",
          AdminCategorySystemRegistration.class,
          harness::registration
        )
      );
      context.refresh();
      AdminCategorySystemRegistry registry = spy(
        context.getBean(AdminCategorySystemRegistry.class)
      );
      clearInvocations(registry);
      return new TestRuntime(context, registry, new AdminCategoryApplicationService(registry));
    }

    @Override
    public void close() {
      context.close();
    }

    private static Class<?> registrationConfiguration() {
      try {
        return Class.forName(
          "com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationConfiguration"
        );
      } catch (ClassNotFoundException exception) {
        throw new AssertionError("admin-category registration configuration is missing", exception);
      }
    }
  }
}
