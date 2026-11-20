package com.ferozfaiz.uniapi.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ch.qos.logback.classic.Level;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
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
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
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
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryContractAssertions;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryOperationMatrixCase;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
class AdminCategoryCrossSystemIsolationContractTest {

  private static final long ROOT_ID = 91_001L;
  private static final long CHILD_ID = 91_002L;
  private static final long PEER_ID = 91_003L;
  private static final long SELECTED_ID = 91_004L;
  private static final long OTHER_ONLY_ID = 91_005L;
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
  private static final Set<Operation> MUTATIONS = Set.of(
    Operation.CREATE,
    Operation.REPLACE,
    Operation.PATCH,
    Operation.DELETE,
    Operation.MOVE,
    Operation.REORDER,
    Operation.ACTIVATE,
    Operation.DEACTIVATE
  );
  private static final List<Direction> DIRECTIONS = List.of(
    new Direction("catalogue", "descriptor-a"),
    new Direction("descriptor-a", "catalogue")
  );
  private static final Set<String> ALL_DIRECTIONAL_CALLS = allDirectionalCalls();
  private static final List<AdminCategoryOperationMatrixCase> MATRIX =
    AdminCategoryOperationMatrixCase.generateDeterministic(
      DIRECTIONS,
      OPERATIONS,
      AdminCategoryCrossSystemIsolationContractTest::matrixCase
    );
  private static final List<IsolationObservation> MANIFEST = IntStream.range(0, MATRIX.size())
    .mapToObj(index -> new IsolationObservation("ISO-%03d".formatted(index + 1), MATRIX.get(index)))
    .toList();

  private RecordingFixture catalogue;
  private RecordingFixture descriptor;
  private TestRuntime runtime;
  private StructuredLogCapture logs;

  @BeforeAll
  void startRealTwoRegistrationRuntime() {
    assertExactManifest();
    catalogue = RecordingFixture.create("catalogue", "catalogue", true, 92_001L);
    descriptor = RecordingFixture.create("descriptor-a", "descriptor", false, 93_001L);
    runtime = TestRuntime.start(List.of(catalogue, descriptor));
    logs = StructuredLogCapture.attach(AdminCategoryApplicationService.class);
  }

  @AfterAll
  void closeRealTwoRegistrationRuntime() {
    if (logs != null) {
      logs.close();
    }
    if (runtime != null) {
      runtime.close();
    }
  }

  @Test
  void manifestSelfCountsFourteenOperationsAndTwentyEightDirections() {
    assertExactManifest();
  }

  @Test
  void fixtureUsesOverlappingIdentifiersAndSlugsWithDistinctSystemValues() {
    catalogue.reset();
    descriptor.reset();

    assertThat(catalogue.provider().snapshot().nodes().keySet()).contains(
      ROOT_ID,
      CHILD_ID,
      PEER_ID,
      SELECTED_ID,
      OTHER_ONLY_ID
    );
    assertThat(descriptor.provider().snapshot().nodes().keySet())
      .contains(ROOT_ID, CHILD_ID, PEER_ID, SELECTED_ID)
      .doesNotContain(OTHER_ONLY_ID);
    for (long id : List.of(ROOT_ID, CHILD_ID, PEER_ID, SELECTED_ID)) {
      AdminCategoryModels.Category catalogueNode = catalogue.provider().node(id);
      AdminCategoryModels.Category descriptorNode = descriptor.provider().node(id);
      assertThat(catalogueNode.slug()).isEqualTo(descriptorNode.slug());
      assertThat(catalogueNode.name()).isNotEqualTo(descriptorNode.name());
    }
  }

  @TestFactory
  List<DynamicTest> exactDirectionalIsolationObservations() {
    assertExactManifest();
    return MANIFEST.stream()
      .map(observation ->
        DynamicTest.dynamicTest(
          observation.observationId() +
            " " +
            observation.row().profileOrSystem() +
            " " +
            observation.row().operation(),
          () -> assertDirectionalIsolation(observation)
        )
      )
      .toList();
  }

  private void assertDirectionalIsolation(IsolationObservation observation) {
    AdminCategoryOperationMatrixCase row = observation.row();
    Direction direction = direction(row.profileOrSystem());
    RecordingFixture selected = fixture(direction.selectedKey());
    RecordingFixture nonSelected = fixture(direction.nonSelectedKey());
    catalogue.reset();
    descriptor.reset();
    clearInvocations(runtime.registry());
    logs.clear();

    StoreSnapshot selectedBefore = selected.provider().snapshot();
    StoreSnapshot nonSelectedBefore = nonSelected.provider().snapshot();

    String actualResult = invoke(runtime.service(), selected, row.operation());

    assertThat(actualResult).isEqualTo(row.expectedStatusOrResult());
    assertThat(selected.provider().calls()).containsExactlyElementsOf(row.expectedPortCalls());
    assertThat(nonSelected.provider().calls()).isEmpty();
    assertThat(combinedCalls()).doesNotContainAnyElementsOf(row.forbiddenInteractions());
    assertThat(nonSelected.provider().snapshot()).isEqualTo(nonSelectedBefore);
    assertThat(nonSelected.provider().ledger()).isEqualTo(InteractionLedger.empty());
    assertThat(selected.provider().ledger().errorTouches()).isZero();
    assertThat(selected.provider().ledger().fallbackTouches()).isZero();
    assertSelectedLedger(row.operation(), selected.provider().ledger());
    if (MUTATIONS.contains(row.operation())) {
      assertThat(selected.provider().snapshot()).isNotEqualTo(selectedBefore);
    } else {
      assertThat(selected.provider().snapshot()).isEqualTo(selectedBefore);
    }
    if (row.operation() == Operation.CAPABILITIES) {
      assertThat(selected.provider().calls()).isEmpty();
    } else {
      assertThat(actualResult)
        .contains(selected.valuePrefix())
        .doesNotContain(nonSelected.valuePrefix());
    }
    verify(runtime.registry()).findRegistration(direction.selectedKey());
    verifyNoMoreInteractions(runtime.registry());
    assertLogs(row.operation(), direction);
  }

  private static void assertSelectedLedger(Operation operation, InteractionLedger ledger) {
    if (operation == Operation.CAPABILITIES) {
      assertThat(ledger).isEqualTo(InteractionLedger.empty());
      return;
    }
    if (READ_OPERATIONS.contains(operation)) {
      assertThat(ledger.readTouches()).isEqualTo(1);
      assertThat(ledger.writeTouches()).isZero();
      assertThat(ledger.transactionTouches()).isZero();
    } else {
      assertThat(ledger.readTouches()).isZero();
      assertThat(ledger.writeTouches()).isEqualTo(1);
      assertThat(ledger.transactionTouches()).isEqualTo(1);
    }
    int cursorExpected = operation == Operation.PARENT_OPTIONS ? 1 : 0;
    assertThat(ledger.cursorTouches()).isEqualTo(cursorExpected);
    assertThat(ledger.hydrationTouches()).isEqualTo(cursorExpected);
    assertThat(ledger.errorTouches()).isZero();
    assertThat(ledger.fallbackTouches()).isZero();
  }

  private void assertLogs(Operation operation, Direction direction) {
    if (!MUTATIONS.contains(operation)) {
      assertThat(logs.events()).isEmpty();
      return;
    }
    StructuredLogCapture.CapturedEvent event = logs.singleEvent();
    Map<String, String> fields = structuredFields(event);
    assertThat(event.level()).isSameAs(Level.INFO);
    assertThat(event.attachedThrowable()).isEmpty();
    assertThat(fields)
      .containsEntry("systemKey", direction.selectedKey())
      .containsEntry("outcome", "succeeded")
      .doesNotContainValue(direction.nonSelectedKey());
    assertThat(event.searchableSurfaces()).noneMatch(surface ->
      surface.contains(valuePrefix(direction.nonSelectedKey()))
    );
  }

  private static void assertExactManifest() {
    AdminCategoryContractAssertions.assertMatrixManifest("cross-system-isolation", MATRIX, 28);
    assertThat(OPERATIONS).hasSize(14).containsExactly(Operation.values());
    assertThat(MANIFEST).hasSize(28);
    assertThat(MANIFEST)
      .extracting(IsolationObservation::observationId)
      .containsExactlyElementsOf(
        IntStream.rangeClosed(1, 28)
          .mapToObj(index -> "ISO-%03d".formatted(index))
          .toList()
      )
      .doesNotHaveDuplicates();
    assertThat(MANIFEST)
      .extracting(observation -> observation.row().caseId())
      .doesNotHaveDuplicates();
    assertThat(MANIFEST)
      .extracting(
        observation -> observation.row().profileOrSystem() + ":" + observation.row().operation()
      )
      .doesNotHaveDuplicates();
    DIRECTIONS.forEach(direction -> {
      List<AdminCategoryOperationMatrixCase> rows = MATRIX.stream()
        .filter(row -> direction.directionId().equals(row.profileOrSystem()))
        .toList();
      assertThat(rows).hasSize(14);
      assertThat(rows)
        .extracting(AdminCategoryOperationMatrixCase::operation)
        .containsExactlyElementsOf(OPERATIONS);
    });
    OPERATIONS.forEach(operation ->
      assertThat(MATRIX.stream().filter(row -> row.operation() == operation)).hasSize(2)
    );
    assertThat(MATRIX).allSatisfy(row -> {
      assertThat(row.declaredCapability()).isTrue();
      assertThat(row.expectedStableError()).isEqualTo("none");
      assertThat(row.expectedLogOutcome()).isNotBlank().isNotEqualToIgnoringCase("N/A");
      assertThat(row.forbiddenInteractions()).contains("provider.fallback");
      if (row.operation() == Operation.CAPABILITIES) {
        assertThat(row.expectedPortCalls()).isEmpty();
      } else {
        assertThat(row.expectedPortCalls()).hasSize(1);
      }
    });
  }

  private static AdminCategoryOperationMatrixCase matrixCase(
    Direction direction,
    Operation operation
  ) {
    Optional<String> selectedCall = AdminCategoryProviderContractProfile.canonicalCall(
      operation
    ).map(call -> direction.selectedKey() + "/" + call);
    List<String> expectedCalls = selectedCall.stream().toList();
    LinkedHashSet<String> forbidden = new LinkedHashSet<>(ALL_DIRECTIONAL_CALLS);
    forbidden.removeAll(expectedCalls);
    forbidden.add("wrong-system.read");
    forbidden.add("wrong-system.write");
    forbidden.add("wrong-system.error");
    forbidden.add("wrong-system.transaction");
    forbidden.add("wrong-system.cursor");
    forbidden.add("wrong-system.hydration");
    forbidden.add("provider.fallback");
    return new AdminCategoryOperationMatrixCase(
      direction.directionId(),
      operation,
      true,
      "authenticated",
      permission(direction.selectedKey(), operation),
      "overlapping ids 91001-91004 and shared slugs",
      expectedResult(direction.selectedKey(), operation),
      "none",
      expectedCalls,
      List.copyOf(forbidden),
      MUTATIONS.contains(operation)
        ? "selected-system mutation success log"
        : "no generic application log",
      "both full-capability registrations declare " + operation.code()
    );
  }

  private static Set<String> allDirectionalCalls() {
    LinkedHashSet<String> calls = new LinkedHashSet<>();
    DIRECTIONS.forEach(direction ->
      OPERATIONS.forEach(operation ->
        AdminCategoryProviderContractProfile.canonicalCall(operation).ifPresent(call ->
          calls.add(direction.selectedKey() + "/" + call)
        )
      )
    );
    return Set.copyOf(calls);
  }

  private String invoke(
    AdminCategoryApplicationService service,
    RecordingFixture selected,
    Operation operation
  ) {
    AdminCategorySystemDefinition definition = selected.definition();
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
          25,
          systemKey + "-cursor",
          List.of(Long.toString(SELECTED_ID), Long.toString(SELECTED_ID), Long.toString(CHILD_ID)),
          null,
          "false"
        )
      );
      case GET -> service.get(systemKey, CHILD_ID);
      case BREADCRUMB -> service.breadcrumb(systemKey, CHILD_ID);
      case CREATE -> service.create(
        systemKey,
        new AdminCategoryCreateValidationInput(
          definition,
          null,
          null,
          null,
          null,
          selected.valuePrefix() + "-created",
          "same-new-slug",
          true
        )
      );
      case REPLACE -> service.update(
        systemKey,
        CHILD_ID,
        new AdminCategoryReplaceValidationInput(
          definition,
          CHILD_ID,
          ROOT_ID,
          selected.valuePrefix() + "-replaced",
          "shared-child-replaced",
          false
        )
      );
      case PATCH -> service.patch(
        systemKey,
        CHILD_ID,
        new AdminCategoryPatchValidationInput(
          definition,
          CHILD_ID,
          null,
          false,
          selected.valuePrefix() + "-patched",
          true,
          null,
          false,
          null,
          false
        )
      );
      case DELETE -> {
        service.delete(systemKey, PEER_ID);
        yield null;
      }
      case MOVE -> {
        service.move(
          systemKey,
          CHILD_ID,
          new AdminCategoryPlacementValidationInput(
            definition,
            Operation.MOVE,
            PEER_ID,
            null,
            null,
            null
          )
        );
        yield null;
      }
      case REORDER -> service.reorder(
        systemKey,
        CHILD_ID,
        new AdminCategoryPlacementValidationInput(
          definition,
          Operation.REORDER,
          ROOT_ID,
          null,
          null,
          0
        )
      );
      case ACTIVATE -> {
        service.activate(systemKey, SELECTED_ID);
        yield null;
      }
      case DEACTIVATE -> {
        service.deactivate(systemKey, CHILD_ID);
        yield null;
      }
    };
    assertSelectedResult(selected, operation, result);
    return expectedResult(systemKey, operation);
  }

  private static void assertSelectedResult(
    RecordingFixture selected,
    Operation operation,
    Object result
  ) {
    String selectedValue = selected.valuePrefix();
    switch (operation) {
      case CAPABILITIES -> assertThat(result)
        .isSameAs(selected.definition())
        .extracting(value -> ((AdminCategorySystemDefinition) value).systemKey())
        .isEqualTo(selected.systemKey());
      case LIST, TREE -> {
        PageResult<?> page = (PageResult<?>) result;
        assertThat(page.content()).isNotEmpty();
        assertThat(resultValues(page.content())).allMatch(value -> value.startsWith(selectedValue));
      }
      case PARENT_OPTIONS -> {
        AdminCategoryModels.ParentOptionSlice slice =
          (AdminCategoryModels.ParentOptionSlice) result;
        assertThat(slice.data())
          .extracting(option -> option.category().name())
          .allMatch(value -> value.startsWith(selectedValue));
        assertThat(slice.nextCursor()).startsWith(selected.systemKey());
      }
      case GET, CREATE, REPLACE, PATCH, REORDER -> assertThat(
        ((AdminCategoryModels.Category) result).name()
      ).startsWith(selectedValue);
      case BREADCRUMB -> {
        List<?> breadcrumb = (List<?>) result;
        assertThat(breadcrumb)
          .extracting(item -> ((AdminCategoryModels.BreadcrumbItem) item).category().name())
          .allMatch(value -> value.startsWith(selectedValue));
      }
      case DELETE -> assertThat(selected.provider().snapshot().nodes()).doesNotContainKey(PEER_ID);
      case MOVE -> assertThat(selected.provider().node(CHILD_ID).parentId()).isEqualTo(PEER_ID);
      case ACTIVATE -> assertThat(selected.provider().node(SELECTED_ID).active()).isTrue();
      case DEACTIVATE -> assertThat(selected.provider().node(CHILD_ID).active()).isFalse();
    }
  }

  private static List<String> resultValues(List<?> values) {
    return values
      .stream()
      .map(value -> ((AdminCategoryModels.CategoryNode) value).category().name())
      .toList();
  }

  private List<String> combinedCalls() {
    List<String> calls = new ArrayList<>(catalogue.provider().calls());
    calls.addAll(descriptor.provider().calls());
    return List.copyOf(calls);
  }

  private RecordingFixture fixture(String systemKey) {
    if (catalogue.systemKey().equals(systemKey)) {
      return catalogue;
    }
    if (descriptor.systemKey().equals(systemKey)) {
      return descriptor;
    }
    throw new IllegalArgumentException("unknown recording fixture");
  }

  private static Direction direction(String directionId) {
    return DIRECTIONS.stream()
      .filter(direction -> direction.directionId().equals(directionId))
      .findFirst()
      .orElseThrow();
  }

  private static String expectedResult(String selectedKey, Operation operation) {
    return "success selected=" + selectedKey + " operation=" + operation.code();
  }

  private static String permission(String systemKey, Operation operation) {
    return (
      "admin:" +
      systemKey +
      "-categories:" +
      (READ_OPERATIONS.contains(operation) ? "read" : "write")
    );
  }

  private static String valuePrefix(String systemKey) {
    return "catalogue".equals(systemKey) ? "catalogue" : "descriptor";
  }

  private static Map<String, String> structuredFields(StructuredLogCapture.CapturedEvent event) {
    LinkedHashMap<String, String> fields = new LinkedHashMap<>();
    event.structuredFields().forEach(field -> fields.put(field.key(), field.value()));
    return Map.copyOf(fields);
  }

  private record Direction(String selectedKey, String nonSelectedKey) {
    private String directionId() {
      return selectedKey + "->" + nonSelectedKey;
    }
  }

  private record IsolationObservation(String observationId, AdminCategoryOperationMatrixCase row) {}

  private record InteractionLedger(
    int readTouches,
    int writeTouches,
    int transactionTouches,
    int cursorTouches,
    int hydrationTouches,
    int errorTouches,
    int fallbackTouches
  ) {
    private static InteractionLedger empty() {
      return new InteractionLedger(0, 0, 0, 0, 0, 0, 0);
    }
  }

  private record StoreSnapshot(Map<Long, AdminCategoryModels.Category> nodes) {
    private StoreSnapshot {
      nodes = Map.copyOf(nodes);
    }
  }

  private record RecordingFixture(
    String systemKey,
    String valuePrefix,
    AdminCategorySystemDefinition definition,
    RecordingProvider provider,
    AdminCategorySystemRegistration registration
  ) {
    private static RecordingFixture create(
      String systemKey,
      String valuePrefix,
      boolean includeOtherOnly,
      long nextId
    ) {
      AdminCategorySystemDefinition definition = AdminCategoryRuntimeTestFixture.completeDefinition(
        systemKey,
        Set.of(Operation.values())
      );
      RecordingProvider provider = new RecordingProvider(
        systemKey,
        valuePrefix,
        includeOtherOnly,
        nextId
      );
      AdminCategoryRuntimeBinding binding = new AdminCategoryRuntimeBinding(
        numericCodec(),
        permissions(definition),
        policies(definition),
        new AdminCategoryRuntimePorts(
          Optional.of(provider),
          Optional.of(provider),
          Optional.of(provider),
          Optional.of(provider),
          Optional.of(provider),
          Optional.of(provider)
        )
      );
      return new RecordingFixture(
        systemKey,
        valuePrefix,
        definition,
        provider,
        AdminCategorySystemRegistration.of(definition, binding)
      );
    }

    private void reset() {
      provider.reset();
    }

    private static AdminCategoryPermissionMapping permissions(
      AdminCategorySystemDefinition definition
    ) {
      return new AdminCategoryPermissionMapping(
        definition
          .operations()
          .supportedOperations()
          .stream()
          .map(operation -> new Binding(operation, permission(definition.systemKey(), operation)))
          .toList()
      );
    }

    private static AdminCategoryRuntimePolicyBindings policies(
      AdminCategorySystemDefinition definition
    ) {
      AdminCategoryDefinitionValidator policy = new AdminCategoryDefinitionValidator();
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
        new AdminCategoryLogicalPolicyId("isolation-contract", systemKey + "-" + obligation.code())
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
  }

  private static final class RecordingProvider
    implements
      AdminCategoryReadPort,
      AdminCategoryHierarchyReadPort,
      AdminCategoryBasicMutationPort,
      AdminCategoryHierarchyMutationPort,
      AdminCategoryOrderingPort,
      AdminCategoryLifecyclePort
  {

    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-07-16T00:00:00Z");

    private final String systemKey;
    private final String valuePrefix;
    private final boolean includeOtherOnly;
    private final long initialNextId;
    private final LinkedHashMap<Long, AdminCategoryModels.Category> nodes = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private long nextId;
    private int readTouches;
    private int writeTouches;
    private int transactionTouches;
    private int cursorTouches;
    private int hydrationTouches;
    private int errorTouches;
    private int fallbackTouches;

    private RecordingProvider(
      String systemKey,
      String valuePrefix,
      boolean includeOtherOnly,
      long initialNextId
    ) {
      this.systemKey = systemKey;
      this.valuePrefix = valuePrefix;
      this.includeOtherOnly = includeOtherOnly;
      this.initialNextId = initialNextId;
      reset();
    }

    private void reset() {
      nodes.clear();
      nodes.put(ROOT_ID, category(ROOT_ID, null, 10L, valuePrefix + "-root", "shared-root", true));
      nodes.put(
        CHILD_ID,
        category(CHILD_ID, ROOT_ID, 20L, valuePrefix + "-child", "shared-child", true)
      );
      nodes.put(
        PEER_ID,
        category(PEER_ID, ROOT_ID, 30L, valuePrefix + "-peer", "shared-peer", true)
      );
      nodes.put(
        SELECTED_ID,
        category(SELECTED_ID, ROOT_ID, 40L, valuePrefix + "-selected", "shared-selected", false)
      );
      if (includeOtherOnly) {
        nodes.put(
          OTHER_ONLY_ID,
          category(OTHER_ONLY_ID, ROOT_ID, 50L, valuePrefix + "-other-only", "other-only", true)
        );
      }
      calls.clear();
      nextId = initialNextId;
      readTouches = 0;
      writeTouches = 0;
      transactionTouches = 0;
      cursorTouches = 0;
      hydrationTouches = 0;
      errorTouches = 0;
      fallbackTouches = 0;
    }

    @Override
    public PageResult<AdminCategoryModels.CategoryNode> list(AdminCategoryListQuery query) {
      read("category-read.list");
      return page(query.page().page(), query.page().size(), query.sort());
    }

    @Override
    public AdminCategoryModels.CategoryNode getNode(Long id) {
      read("category-read.getNode");
      return node(id);
    }

    @Override
    public AdminCategoryModels.Category get(Long id) {
      read("category-read.get");
      return required(id);
    }

    @Override
    public PageResult<AdminCategoryModels.CategoryNode> tree(AdminCategoryTreeQuery query) {
      read("hierarchy-read.tree");
      return page(query.page().page(), query.page().size(), query.sort());
    }

    @Override
    public List<AdminCategoryModels.BreadcrumbItem> breadcrumb(Long id) {
      read("hierarchy-read.breadcrumb");
      AdminCategoryModels.Category selected = required(id);
      return List.of(
        new AdminCategoryModels.BreadcrumbItem(required(ROOT_ID), 0),
        new AdminCategoryModels.BreadcrumbItem(selected, 1)
      );
    }

    @Override
    public AdminCategoryModels.ParentOptionSlice parentOptions(
      AdminCategoryParentOptionsQuery query
    ) {
      read("hierarchy-read.parentOptions");
      if (query.cursor() != null) {
        cursorTouches++;
      }
      if (!query.includeIds().isEmpty()) {
        hydrationTouches++;
      }
      List<AdminCategoryModels.ParentOption> options = query
        .includeIds()
        .stream()
        .distinct()
        .map(this::parentOption)
        .toList();
      return new AdminCategoryModels.ParentOptionSlice(options, false, systemKey + "-next-cursor");
    }

    @Override
    public AdminCategoryModels.Category create(AdminCategoryCreateCommand command) {
      write("basic-mutation.create");
      AdminCategoryModels.Category created = category(
        nextId++,
        command.parentId(),
        60L,
        command.name(),
        command.slug(),
        command.active()
      );
      nodes.put(created.id(), created);
      return created;
    }

    @Override
    public AdminCategoryModels.Category replace(Long id, AdminCategoryUpdateCommand command) {
      write("basic-mutation.replace");
      AdminCategoryModels.Category previous = required(id);
      AdminCategoryModels.Category replaced = category(
        id,
        command.parentId(),
        previous.sortRank(),
        command.name(),
        command.slug(),
        command.active()
      );
      nodes.put(id, replaced);
      return replaced;
    }

    @Override
    public AdminCategoryModels.Category patch(Long id, AdminCategoryPatchCommand command) {
      write("basic-mutation.patch");
      AdminCategoryModels.Category previous = required(id);
      AdminCategoryModels.Category patched = category(
        id,
        command.parentIdPresent() ? command.parentId() : previous.parentId(),
        previous.sortRank(),
        command.namePresent() ? command.name() : previous.name(),
        command.slugPresent() ? command.slug() : previous.slug(),
        command.activePresent() ? Boolean.TRUE.equals(command.active()) : previous.active()
      );
      nodes.put(id, patched);
      return patched;
    }

    @Override
    public void delete(Long id) {
      write("basic-mutation.delete");
      if (nodes.remove(id) == null) {
        errorTouches++;
        throw new IllegalArgumentException("selected node missing");
      }
    }

    @Override
    public void move(Long id, AdminCategoryMoveCommand command) {
      write("hierarchy-mutation.move");
      AdminCategoryModels.Category previous = required(id);
      nodes.put(
        id,
        category(
          id,
          command.newParentId(),
          previous.sortRank(),
          previous.name(),
          previous.slug(),
          previous.active()
        )
      );
    }

    @Override
    public AdminCategoryModels.Category reorder(Long id, AdminCategoryReorderCommand command) {
      write("ordering.reorder");
      AdminCategoryModels.Category previous = required(id);
      AdminCategoryModels.Category reordered = category(
        id,
        command.parentId(),
        1L,
        previous.name(),
        previous.slug(),
        previous.active()
      );
      nodes.put(id, reordered);
      return reordered;
    }

    @Override
    public void activate(Long id) {
      write("lifecycle.activate");
      setActive(id, true);
    }

    @Override
    public void deactivate(Long id) {
      write("lifecycle.deactivate");
      setActive(id, false);
    }

    private void setActive(Long id, boolean active) {
      AdminCategoryModels.Category previous = required(id);
      nodes.put(
        id,
        category(
          id,
          previous.parentId(),
          previous.sortRank(),
          previous.name(),
          previous.slug(),
          active
        )
      );
    }

    private PageResult<AdminCategoryModels.CategoryNode> page(
      int page,
      int size,
      com.ferozfaiz.uniapi.shared.pagination.SortCriteria sort
    ) {
      List<AdminCategoryModels.CategoryNode> content = nodes
        .values()
        .stream()
        .sorted(java.util.Comparator.comparing(AdminCategoryModels.Category::id))
        .map(category ->
          new AdminCategoryModels.CategoryNode(category, category.parentId() == null ? 0 : 1, false)
        )
        .toList();
      return new PageResult<>(content, page, size, content.size(), 1, true, true, sort);
    }

    private AdminCategoryModels.ParentOption parentOption(Long id) {
      AdminCategoryModels.Category selected = required(id);
      List<AdminCategoryModels.BreadcrumbItem> path =
        selected.parentId() == null
          ? List.of(new AdminCategoryModels.BreadcrumbItem(selected, 0))
          : List.of(
              new AdminCategoryModels.BreadcrumbItem(required(ROOT_ID), 0),
              new AdminCategoryModels.BreadcrumbItem(selected, 1)
            );
      return new AdminCategoryModels.ParentOption(
        selected,
        selected.parentId() == null ? 0 : 1,
        selected.name(),
        path,
        true,
        null
      );
    }

    private AdminCategoryModels.CategoryNode node(Long id) {
      AdminCategoryModels.Category selected = required(id);
      return new AdminCategoryModels.CategoryNode(
        selected,
        selected.parentId() == null ? 0 : 1,
        false
      );
    }

    private AdminCategoryModels.Category required(Long id) {
      AdminCategoryModels.Category selected = nodes.get(id);
      if (selected == null) {
        errorTouches++;
        throw new IllegalArgumentException("selected node missing");
      }
      return selected;
    }

    private void read(String call) {
      calls.add(systemKey + "/" + call);
      readTouches++;
    }

    private void write(String call) {
      calls.add(systemKey + "/" + call);
      writeTouches++;
      transactionTouches++;
    }

    private StoreSnapshot snapshot() {
      return new StoreSnapshot(nodes);
    }

    private InteractionLedger ledger() {
      return new InteractionLedger(
        readTouches,
        writeTouches,
        transactionTouches,
        cursorTouches,
        hydrationTouches,
        errorTouches,
        fallbackTouches
      );
    }

    private List<String> calls() {
      return List.copyOf(calls);
    }

    private AdminCategoryModels.Category node(long id) {
      return required(id);
    }

    private static AdminCategoryModels.Category category(
      long id,
      Long parentId,
      Long rank,
      String name,
      String slug,
      boolean active
    ) {
      return new AdminCategoryModels.Category(
        id,
        parentId,
        rank,
        name,
        slug,
        active,
        FIXED_TIME,
        FIXED_TIME
      );
    }
  }

  private record TestRuntime(
    AnnotationConfigApplicationContext context,
    AdminCategorySystemRegistry registry,
    AdminCategoryApplicationService service
  ) implements AutoCloseable {
    private static TestRuntime start(List<RecordingFixture> fixtures) {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      context.register(registrationConfiguration());
      fixtures.forEach(fixture ->
        context.registerBean(
          fixture.systemKey() + "Registration",
          AdminCategorySystemRegistration.class,
          fixture::registration
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
