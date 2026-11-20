package com.ferozfaiz.uniapi.category.contract;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryProviderCapability;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.PortSlot;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AdminCategoryContractInfrastructureTest {

  private static final String EXPECTED_DISPATCH_FINGERPRINT =
    "be27333a56754d76ad4de4720e4e9526ff60dce5cf912e63402dd8db799c8b47";
  private static final String EXPECTED_AUTHORIZATION_FINGERPRINT =
    "ac19bc2e3e468cf880c15ef20b9f6718cc67030aadc08a4730090e02b347bfc9";
  private static final String EXPECTED_RESTRICTED_FINGERPRINT =
    "bd3d631cf2a285603404d0ab1e98375326311a73a5374d59f6e999d99c6e1ca2";
  private static final String EXPECTED_SYSTEM_KEY_FINGERPRINT =
    "5037ae8ec9e87a71442c3830da9a9d2f5ad4d748de8ed0bd4f162d709c7f647d";
  private static final String EXPECTED_ISOLATION_PAIR_FINGERPRINT =
    "1f8e2f0e81261d14e2177a42b199a2922298fda1ca01d4e247943cef0958b322";
  private static final String EXPECTED_ISOLATION_DIRECTION_FINGERPRINT =
    "dff4aa82f224d06d8170d5c1af5a0f32b840c1a09aef11667e2e8b5ffa1db215";

  private static final List<String> MATRIX_DIMENSIONS = List.of(
    "profileOrSystem",
    "operation",
    "declaredCapability",
    "authenticationState",
    "authorityState",
    "inputCondition",
    "expectedStatusOrResult",
    "expectedStableError",
    "expectedPortCalls",
    "forbiddenInteractions",
    "expectedLogOutcome",
    "applicabilityEvidence"
  );

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

  private static final List<OperationOracle> OPERATION_ORACLE = List.of(
    new OperationOracle(
      Operation.CAPABILITIES,
      PortSlot.NONE,
      "none",
      "200 AdminCategoryCapabilityResponse"
    ),
    new OperationOracle(
      Operation.LIST,
      PortSlot.CATEGORY_READ,
      "category-read.list",
      "200 PageResponse<AdminCategoryListItemResponse>"
    ),
    new OperationOracle(
      Operation.TREE,
      PortSlot.HIERARCHY_READ,
      "hierarchy-read.tree",
      "200 PageResponse<AdminCategoryTreeItemResponse>"
    ),
    new OperationOracle(
      Operation.PARENT_OPTIONS,
      PortSlot.HIERARCHY_READ,
      "hierarchy-read.parentOptions",
      "200 AdminCategoryParentOptionSliceResponse"
    ),
    new OperationOracle(
      Operation.GET,
      PortSlot.CATEGORY_READ,
      "category-read.getNode",
      "200 AdminCategoryDetailResponse"
    ),
    new OperationOracle(
      Operation.BREADCRUMB,
      PortSlot.HIERARCHY_READ,
      "hierarchy-read.breadcrumb",
      "200 root-to-selected breadcrumb array"
    ),
    new OperationOracle(
      Operation.CREATE,
      PortSlot.BASIC_MUTATION,
      "basic-mutation.create",
      "201 AdminCategoryDetailResponse plus absolute Location"
    ),
    new OperationOracle(
      Operation.REPLACE,
      PortSlot.BASIC_MUTATION,
      "basic-mutation.replace",
      "200 AdminCategoryDetailResponse"
    ),
    new OperationOracle(
      Operation.PATCH,
      PortSlot.BASIC_MUTATION,
      "basic-mutation.patch",
      "200 AdminCategoryDetailResponse"
    ),
    new OperationOracle(
      Operation.DELETE,
      PortSlot.BASIC_MUTATION,
      "basic-mutation.delete",
      "204 empty body"
    ),
    new OperationOracle(
      Operation.MOVE,
      PortSlot.HIERARCHY_MUTATION,
      "hierarchy-mutation.move",
      "204 empty body"
    ),
    new OperationOracle(
      Operation.REORDER,
      PortSlot.ORDERING,
      "ordering.reorder",
      "200 AdminCategoryDetailResponse"
    ),
    new OperationOracle(
      Operation.ACTIVATE,
      PortSlot.LIFECYCLE,
      "lifecycle.activate",
      "204 empty body"
    ),
    new OperationOracle(
      Operation.DEACTIVATE,
      PortSlot.LIFECYCLE,
      "lifecycle.deactivate",
      "204 empty body"
    )
  );

  private static final Map<Operation, OperationOracle> OPERATION_ORACLE_BY_OPERATION =
    OPERATION_ORACLE.stream().collect(
      Collectors.toUnmodifiableMap(
        OperationOracle::operation,
        Function.identity(),
        (left, right) -> {
          throw new IllegalArgumentException("duplicate operation oracle");
        }
      )
    );

  private static final List<String> PROFILES = List.of(
    "catalogue",
    "descriptor-a",
    "neutral-flat",
    "neutral-restricted"
  );

  private static final Set<Operation> EXPECTED_FULL_DECLARATIONS = Set.of(
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

  private static final Set<Operation> EXPECTED_FLAT_DECLARATIONS = Set.of(
    Operation.CAPABILITIES,
    Operation.LIST,
    Operation.GET
  );

  private static final Set<Operation> EXPECTED_RESTRICTED_DECLARATIONS = Set.of(
    Operation.CAPABILITIES,
    Operation.LIST,
    Operation.CREATE
  );

  private static final Set<Operation> READ_OPERATIONS = Set.of(
    Operation.CAPABILITIES,
    Operation.LIST,
    Operation.TREE,
    Operation.PARENT_OPTIONS,
    Operation.GET,
    Operation.BREADCRUMB
  );

  private static final List<String> AUTHORITY_STATES = List.of(
    "unauthenticated",
    "exact-selected-permission",
    "no-permission",
    "corresponding-other-system-permission",
    "selected-system-read-only",
    "selected-system-write-only",
    "legacy-global",
    "scope-only",
    "unrelated-role-permission",
    "role-admin-only",
    "role-moderator-only"
  );

  private static final List<String> RESTRICTED_PRECEDENCE_STATES = List.of(
    "unauthenticated",
    "missing-preliminary-permission",
    "sufficient-preliminary-permission"
  );

  private static final List<String> SYSTEM_KEY_INPUTS = List.of(
    "KEY-01:canonical catalogue while unauthenticated",
    "KEY-02:canonical catalogue without preliminary permission",
    "KEY-03:exact canonical catalogue",
    "KEY-04:whitespace-padded canonical catalogue",
    "KEY-05:canonical unknown missing-system",
    "KEY-06:uppercase CATALOGUE",
    "KEY-07:mixed-case Catalogue",
    "KEY-08:blank application-boundary key",
    "KEY-09:malformed unsafe key",
    "KEY-10:64 lowercase a characters",
    "KEY-11:omitted HTTP path segment",
    "KEY-12:null direct-application key"
  );

  private static final List<String> ISOLATION_SYSTEMS = List.of("catalogue", "descriptor-a");

  private static final List<String> SUCCESS_FORBIDDEN = List.of(
    "non-mapped-ports.0",
    "other-registrations.0",
    "legacy-and-storage-registries.0",
    "fallbacks.0"
  );

  private static final List<String> DENIED_FORBIDDEN = List.of(
    "application-dispatch.0",
    "capability-and-definition-work.0",
    "provider-ports.0",
    "persistence-and-events.0",
    "fallbacks.0"
  );

  private static final List<String> UNSUPPORTED_FORBIDDEN = List.of(
    "definition-validation.0",
    "identifier-and-cursor-work.0",
    "provider-ports.0",
    "persistence-lock-event-mutation.0",
    "legacy-other-registration-and-fallback.0"
  );

  private static final List<String> ISOLATION_FORBIDDEN = List.of(
    "non-selected-registration.0",
    "non-selected-ports.0",
    "non-selected-persistence.0",
    "alternate-port-and-fallback.0"
  );

  @Test
  void matrixSchemaOperationOrderAndLiteralOperationOracleAreFrozen() {
    List<String> actualDimensions = Arrays.stream(
      AdminCategoryOperationMatrixCase.class.getRecordComponents()
    )
      .map(component -> component.getName())
      .toList();

    assertAll(
      () -> assertEquals(MATRIX_DIMENSIONS, actualDimensions),
      () -> assertEquals(12, actualDimensions.size()),
      () -> assertEquals(OPERATIONS, Arrays.asList(Operation.values())),
      () -> assertEquals(14, OPERATIONS.size()),
      () ->
        assertEquals(
          OPERATIONS,
          OPERATION_ORACLE.stream().map(OperationOracle::operation).toList()
        ),
      () -> assertEquals(14, OPERATION_ORACLE_BY_OPERATION.size()),
      () ->
        OPERATION_ORACLE.forEach(oracle -> {
          String productionSlot = AdminCategoryProviderCapability.forOperation(oracle.operation())
            .map(Enum::name)
            .orElse(PortSlot.NONE.name());
          assertEquals(oracle.port().name(), productionSlot);
        })
    );
  }

  @Test
  void matrixCaseNormalizesAndDefensivelyCopiesItsInputs() {
    List<String> calls = new ArrayList<>(List.of(" selected-port "));
    List<String> forbidden = new ArrayList<>(List.of(" fallback "));
    AdminCategoryOperationMatrixCase row = matrixCase(
      " catalogue ",
      Operation.LIST,
      true,
      " success ",
      " none ",
      calls,
      forbidden,
      " safe ",
      " baseline evidence "
    );
    calls.add("late-call");
    forbidden.clear();

    assertAll(
      () -> assertEquals("catalogue", row.profileOrSystem()),
      () -> assertEquals("success", row.expectedStatusOrResult()),
      () -> assertEquals(List.of("selected-port"), row.expectedPortCalls()),
      () -> assertEquals(List.of("fallback"), row.forbiddenInteractions()),
      () -> assertEquals("safe", row.expectedLogOutcome()),
      () -> assertEquals("baseline evidence", row.applicabilityEvidence()),
      () ->
        assertThrows(UnsupportedOperationException.class, () ->
          row.expectedPortCalls().add("mutation")
        ),
      () ->
        assertThrows(UnsupportedOperationException.class, () ->
          row.forbiddenInteractions().add("mutation")
        )
    );
  }

  @Test
  void matrixCaseRejectsEveryUnsetReferenceDimension() {
    List<InvalidDimension> invalidDimensions = List.of(
      new InvalidDimension(0, null, IllegalArgumentException.class),
      new InvalidDimension(1, null, NullPointerException.class),
      new InvalidDimension(2, " ", IllegalArgumentException.class),
      new InvalidDimension(3, " ", IllegalArgumentException.class),
      new InvalidDimension(4, " ", IllegalArgumentException.class),
      new InvalidDimension(5, " ", IllegalArgumentException.class),
      new InvalidDimension(6, " ", IllegalArgumentException.class),
      new InvalidDimension(7, null, NullPointerException.class),
      new InvalidDimension(8, null, NullPointerException.class),
      new InvalidDimension(9, " ", IllegalArgumentException.class),
      new InvalidDimension(10, " ", IllegalArgumentException.class)
    );

    assertAll(
      invalidDimensions
        .stream()
        .map(
          invalid -> () ->
            assertThrows(invalid.exceptionType(), () ->
              matrixCaseWith(invalid.index(), invalid.value())
            )
        )
    );
  }

  @Test
  void notApplicableRowsRequireEvidenceForbiddenLedgerAndExecutableLog() {
    assertAll(
      () ->
        assertThrows(IllegalArgumentException.class, () ->
          matrixCase(
            "catalogue",
            Operation.CAPABILITIES,
            true,
            "SUCCESS",
            "none",
            List.of("N/A"),
            List.of("fallback.0"),
            "safe",
            "the operation is applicable"
          )
        ),
      () ->
        assertThrows(IllegalArgumentException.class, () ->
          matrixCase(
            "catalogue",
            Operation.CAPABILITIES,
            true,
            "SUCCESS",
            "none",
            List.of(),
            List.of("N/A"),
            "safe",
            "the operation is applicable"
          )
        ),
      () ->
        assertThrows(IllegalArgumentException.class, () ->
          matrixCase(
            "catalogue",
            Operation.CAPABILITIES,
            true,
            "N/A",
            "none",
            List.of(),
            List.of("fallback.0"),
            "transport-access-only",
            "N/A"
          )
        ),
      () ->
        assertThrows(IllegalArgumentException.class, () ->
          matrixCase(
            "no-selected-profile",
            Operation.CAPABILITIES,
            false,
            "NO_ROUTE",
            "N/A",
            List.of(),
            List.of("category-handler.0"),
            "transport-access-only",
            "N/A"
          )
        ),
      () -> {
        AdminCategoryOperationMatrixCase missingForbiddenLedger = matrixCase(
          "no-selected-profile",
          Operation.CAPABILITIES,
          false,
          "NO_ROUTE",
          "N/A",
          List.of(),
          List.of(),
          "transport-access-only",
          "no category handler owns an omitted path segment"
        );
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
          validateCompleteRows(List.of(missingForbiddenLedger))
        );
        assertEquals("N/A requires a forbidden-interaction ledger", failure.getMessage());
      },
      () ->
        assertThrows(IllegalArgumentException.class, () ->
          matrixCase(
            "catalogue",
            Operation.CAPABILITIES,
            true,
            "N/A",
            "none",
            List.of(),
            List.of("fallback.0"),
            "N/A",
            "the pinned baseline lacks this concern"
          )
        ),
      () -> {
        AdminCategoryOperationMatrixCase explainedErrorNotApplicable = matrixCase(
          "no-selected-profile",
          Operation.CAPABILITIES,
          false,
          "NO_ROUTE",
          "N/A",
          List.of(),
          List.of("category-controller.0", "category-application.0", "fallback.0"),
          "transport-access-only;no-generic-category-application-log",
          "stable category error is inapplicable because no category handler owns the route"
        );
        assertDoesNotThrow(() -> validateCompleteRows(List.of(explainedErrorNotApplicable)));
      }
    );
  }

  @Test
  void dispatchManifestHasExactDeclarationsAndThirtyFourToTwentyTwoAccounting() {
    List<AdminCategoryOperationMatrixCase> rows = dispatchManifest();

    assertDoesNotThrow(() -> validateDispatchManifest(rows));
    assertAll(
      () -> assertEquals(56, rows.size()),
      () ->
        assertEquals(
          34,
          rows.stream().filter(AdminCategoryOperationMatrixCase::declaredCapability).count()
        ),
      () ->
        assertEquals(
          22,
          rows
            .stream()
            .filter(row -> !row.declaredCapability())
            .count()
        )
    );
  }

  @Test
  void literalProfileDeclarationsRejectCountPreservingFlatAndRestrictedSwap() {
    assertAll(
      () -> assertEquals(EXPECTED_FULL_DECLARATIONS, expectedProfileDeclarations("catalogue")),
      () -> assertEquals(EXPECTED_FULL_DECLARATIONS, expectedProfileDeclarations("descriptor-a")),
      () -> assertEquals(EXPECTED_FLAT_DECLARATIONS, expectedProfileDeclarations("neutral-flat")),
      () ->
        assertEquals(
          EXPECTED_RESTRICTED_DECLARATIONS,
          expectedProfileDeclarations("neutral-restricted")
        )
    );

    List<AdminCategoryOperationMatrixCase> swapped = new ArrayList<>(dispatchManifest());
    replaceDispatchRow(swapped, dispatchCaseForDeclared("neutral-flat", Operation.GET, false));
    replaceDispatchRow(swapped, dispatchCaseForDeclared("neutral-flat", Operation.CREATE, true));
    replaceDispatchRow(swapped, dispatchCaseForDeclared("neutral-restricted", Operation.GET, true));
    replaceDispatchRow(
      swapped,
      dispatchCaseForDeclared("neutral-restricted", Operation.CREATE, false)
    );

    assertAll(
      () ->
        assertEquals(
          34,
          swapped.stream().filter(AdminCategoryOperationMatrixCase::declaredCapability).count()
        ),
      () ->
        assertEquals(
          22,
          swapped
            .stream()
            .filter(row -> !row.declaredCapability())
            .count()
        ),
      () -> assertThrows(IllegalArgumentException.class, () -> validateDispatchManifest(swapped))
    );
  }

  @Test
  void dispatchValidatorRejectsAuxiliaryGetMethodInPlaceOfGetNode() {
    List<AdminCategoryOperationMatrixCase> rows = new ArrayList<>(dispatchManifest());
    AdminCategoryOperationMatrixCase get = findRow(
      rows,
      row -> row.profileOrSystem().equals("catalogue") && row.operation() == Operation.GET
    );
    replaceRow(rows, get, withExpectedCalls(get, List.of("category-read.get")));

    assertThrows(IllegalArgumentException.class, () -> validateDispatchManifest(rows));
  }

  @Test
  void duplicateCaseIdsAndDispatchKeysFailTheirOwnGuards() {
    AdminCategoryOperationMatrixCase first = dispatchCase(
      "catalogue",
      Operation.CAPABILITIES,
      "V(CAPABILITIES)"
    );
    AdminCategoryOperationMatrixCase distinctIdSameKey = dispatchCase(
      "catalogue",
      Operation.CAPABILITIES,
      "V(CAPABILITIES)-variant"
    );
    assertNotEquals(first.caseId(), distinctIdSameKey.caseId());

    assertAll(
      () -> assertDuplicateGuard(List.of(first, first), AdminCategoryOperationMatrixCase::caseId),
      () ->
        assertDuplicateGuard(
          List.of(first, distinctIdSameKey),
          AdminCategoryContractInfrastructureTest::dispatchKey
        )
    );
  }

  @Test
  void missingDispatchCellFailsCoverageGuard() {
    List<AdminCategoryOperationMatrixCase> rows = new ArrayList<>(dispatchManifest());
    rows.remove(rows.size() - 1);

    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
      validateDispatchManifest(rows)
    );

    assertEquals("manifest is missing an expected key", failure.getMessage());
  }

  @Test
  void keyedAcceptanceManifestsDeriveEveryRequiredCardinality() {
    List<AdminCategoryOperationMatrixCase> authorization = authorizationManifest();
    List<AdminCategoryOperationMatrixCase> restricted = restrictedManifest();
    List<AdminCategoryOperationMatrixCase> systemKeys = systemKeyManifest();
    List<AdminCategoryOperationMatrixCase> isolationPairs = isolationPairs();
    List<AdminCategoryOperationMatrixCase> isolationDirections = isolationDirections();

    assertAll(
      () -> assertDoesNotThrow(() -> validateAuthorizationManifest(authorization)),
      () -> assertDoesNotThrow(() -> validateRestrictedManifest(restricted)),
      () -> assertDoesNotThrow(() -> validateSystemKeyManifest(systemKeys)),
      () -> assertDoesNotThrow(() -> validateIsolationPairs(isolationPairs)),
      () -> assertDoesNotThrow(() -> validateIsolationDirections(isolationDirections)),
      () -> assertEquals(154, authorization.size()),
      () -> assertEquals(33, restricted.size()),
      () -> assertEquals(12, systemKeys.size()),
      () -> assertEquals(14, isolationPairs.size()),
      () -> assertEquals(28, isolationDirections.size())
    );
  }

  @Test
  void orderedFullRowManifestsMatchIndependentFrozenFingerprints() {
    assertAll(
      () -> assertEquals(EXPECTED_DISPATCH_FINGERPRINT, fingerprint(dispatchManifest())),
      () -> assertEquals(EXPECTED_AUTHORIZATION_FINGERPRINT, fingerprint(authorizationManifest())),
      () -> assertEquals(EXPECTED_RESTRICTED_FINGERPRINT, fingerprint(restrictedManifest())),
      () -> assertEquals(EXPECTED_SYSTEM_KEY_FINGERPRINT, fingerprint(systemKeyManifest())),
      () -> assertEquals(EXPECTED_ISOLATION_PAIR_FINGERPRINT, fingerprint(isolationPairs())),
      () ->
        assertEquals(EXPECTED_ISOLATION_DIRECTION_FINGERPRINT, fingerprint(isolationDirections()))
    );
  }

  @Test
  void keyedManifestGuardSeparatesDuplicateMissingAndUnaccountedCells() {
    List<AdminCategoryOperationMatrixCase> complete = authorizationManifest();
    AdminCategoryOperationMatrixCase first = complete.get(0);
    List<AdminCategoryOperationMatrixCase> missing = new ArrayList<>(complete);
    missing.remove(missing.size() - 1);
    List<AdminCategoryOperationMatrixCase> unaccounted = new ArrayList<>(complete);
    unaccounted.add(withAuthorityState(first, "unexpected-state"));

    assertAll(
      () ->
        assertDuplicateGuard(
          List.of(first, first),
          AdminCategoryContractInfrastructureTest::authorizationKey
        ),
      () ->
        assertManifestFailure(
          "manifest is missing an expected key",
          missing,
          expectedAuthorizationKeys(),
          AdminCategoryContractInfrastructureTest::authorizationKey
        ),
      () ->
        assertManifestFailure(
          "manifest contains an unaccounted key",
          unaccounted,
          expectedAuthorizationKeys(),
          AdminCategoryContractInfrastructureTest::authorizationKey
        )
    );
  }

  @Test
  void exactAcceptanceValidatorsRejectIncorrectClassificationsCallsAndEvidence() {
    List<AdminCategoryOperationMatrixCase> authorization = new ArrayList<>(authorizationManifest());
    AdminCategoryOperationMatrixCase noPermission = findRow(
      authorization,
      row ->
        row.operation() == Operation.CAPABILITIES && row.authorityState().equals("no-permission")
    );
    replaceRow(authorization, noPermission, withExpectedOutcome(noPermission, "SUCCESS", "none"));

    List<AdminCategoryOperationMatrixCase> restricted = new ArrayList<>(restrictedManifest());
    AdminCategoryOperationMatrixCase sufficientPermission = findRow(
      restricted,
      row ->
        row.operation() == Operation.TREE &&
        row.authorityState().equals("sufficient-preliminary-permission")
    );
    replaceRow(
      restricted,
      sufficientPermission,
      withExpectedOutcome(sufficientPermission, "200 SUCCESS", "none")
    );

    List<AdminCategoryOperationMatrixCase> systemKeys = new ArrayList<>(systemKeyManifest());
    AdminCategoryOperationMatrixCase exactCanonical = systemKeys.get(2);
    replaceRow(
      systemKeys,
      exactCanonical,
      withExpectedOutcome(exactCanonical, "wrong-outcome", "none")
    );

    List<AdminCategoryOperationMatrixCase> wrongIsolationEvidence = new ArrayList<>(
      isolationPairs()
    );
    AdminCategoryOperationMatrixCase pair = wrongIsolationEvidence.get(1);
    replaceRow(wrongIsolationEvidence, pair, withEvidence(pair, "wrong-evidence"));

    List<AdminCategoryOperationMatrixCase> wrongIsolationCalls = new ArrayList<>(
      isolationDirections()
    );
    AdminCategoryOperationMatrixCase direction = findRow(
      wrongIsolationCalls,
      row -> row.profileOrSystem().equals("catalogue") && row.operation() == Operation.GET
    );
    replaceRow(
      wrongIsolationCalls,
      direction,
      withExpectedCalls(direction, List.of("category-read.get"))
    );

    assertAll(
      () ->
        assertThrows(IllegalArgumentException.class, () ->
          validateAuthorizationManifest(authorization)
        ),
      () ->
        assertThrows(IllegalArgumentException.class, () -> validateRestrictedManifest(restricted)),
      () ->
        assertThrows(IllegalArgumentException.class, () -> validateSystemKeyManifest(systemKeys)),
      () ->
        assertThrows(IllegalArgumentException.class, () ->
          validateIsolationPairs(wrongIsolationEvidence)
        ),
      () ->
        assertThrows(IllegalArgumentException.class, () ->
          validateIsolationDirections(wrongIsolationCalls)
        )
    );
  }

  private static void validateDispatchManifest(List<AdminCategoryOperationMatrixCase> rows) {
    validateExpectedRows(
      rows,
      expectedDispatchKeys(),
      AdminCategoryContractInfrastructureTest::dispatchKey,
      key -> expectedDispatchCase(key.profile(), key.operation()),
      "dispatch"
    );

    long declared = rows
      .stream()
      .filter(AdminCategoryOperationMatrixCase::declaredCapability)
      .count();
    if (declared != 34 || rows.size() - declared != 22) {
      throw new IllegalArgumentException("dispatch declaration accounting mismatch");
    }
  }

  private static void validateAuthorizationManifest(List<AdminCategoryOperationMatrixCase> rows) {
    validateExpectedRows(
      rows,
      expectedAuthorizationKeys(),
      AdminCategoryContractInfrastructureTest::authorizationKey,
      key -> expectedAuthorizationCase(key.operation(), key.authorityState()),
      "authorization"
    );
  }

  private static void validateRestrictedManifest(List<AdminCategoryOperationMatrixCase> rows) {
    validateExpectedRows(
      rows,
      expectedRestrictedKeys(),
      AdminCategoryContractInfrastructureTest::restrictedKey,
      key -> expectedRestrictedCase(key.operation(), key.precedenceState()),
      "restricted precedence"
    );
  }

  private static void validateSystemKeyManifest(List<AdminCategoryOperationMatrixCase> rows) {
    validateExpectedRows(
      rows,
      expectedSystemKeyKeys(),
      AdminCategoryContractInfrastructureTest::systemKey,
      key -> expectedSystemKeyCase(SYSTEM_KEY_INPUTS.indexOf(key.inputCondition()) + 1),
      "system key"
    );
  }

  private static void validateIsolationPairs(List<AdminCategoryOperationMatrixCase> rows) {
    validateExpectedRows(
      rows,
      Set.copyOf(OPERATIONS),
      AdminCategoryOperationMatrixCase::operation,
      AdminCategoryContractInfrastructureTest::expectedIsolationPair,
      "isolation pair"
    );
  }

  private static void validateIsolationDirections(List<AdminCategoryOperationMatrixCase> rows) {
    validateExpectedRows(
      rows,
      expectedIsolationDirectionKeys(),
      AdminCategoryContractInfrastructureTest::isolationDirectionKey,
      key -> expectedIsolationDirection(key.operation(), key.selectedSystem()),
      "isolation direction"
    );
  }

  private static <K> void validateExpectedRows(
    List<AdminCategoryOperationMatrixCase> rows,
    Set<K> expectedKeys,
    Function<AdminCategoryOperationMatrixCase, K> keyExtractor,
    Function<K, AdminCategoryOperationMatrixCase> expectedRow,
    String manifestName
  ) {
    validateCompleteRows(rows);
    validateExactManifest(rows, expectedKeys, keyExtractor);
    validateUniqueKeys(rows, AdminCategoryOperationMatrixCase::caseId);
    for (AdminCategoryOperationMatrixCase row : rows) {
      K key = keyExtractor.apply(row);
      if (!expectedRow.apply(key).equals(row)) {
        throw new IllegalArgumentException(manifestName + " row classification mismatch");
      }
    }
  }

  private static void validateCompleteRows(List<AdminCategoryOperationMatrixCase> rows) {
    Objects.requireNonNull(rows, "rows");
    for (AdminCategoryOperationMatrixCase row : rows) {
      Objects.requireNonNull(row, "row");
      requireText(row.profileOrSystem());
      Objects.requireNonNull(row.operation(), "operation");
      requireText(row.authenticationState());
      requireText(row.authorityState());
      requireText(row.inputCondition());
      requireText(row.expectedStatusOrResult());
      requireText(row.expectedStableError());
      requireExecutableList(row.expectedPortCalls());
      requireExecutableList(row.forbiddenInteractions());
      requireText(row.expectedLogOutcome());
      requireText(row.applicabilityEvidence());
      boolean notApplicable =
        isNotApplicable(row.expectedStatusOrResult()) || isNotApplicable(row.expectedStableError());
      if (notApplicable && isNotApplicable(row.applicabilityEvidence())) {
        throw new IllegalArgumentException("N/A requires baseline applicability evidence");
      }
      if (notApplicable && row.forbiddenInteractions().isEmpty()) {
        throw new IllegalArgumentException("N/A requires a forbidden-interaction ledger");
      }
      if (
        row
          .expectedPortCalls()
          .stream()
          .anyMatch(AdminCategoryContractInfrastructureTest::isNotApplicable) ||
        row
          .forbiddenInteractions()
          .stream()
          .anyMatch(AdminCategoryContractInfrastructureTest::isNotApplicable) ||
        isNotApplicable(row.expectedLogOutcome())
      ) {
        throw new IllegalArgumentException("matrix interaction and log fields must be executable");
      }
    }
  }

  private static <T, K> void validateExactManifest(
    List<T> rows,
    Set<K> expectedKeys,
    Function<T, K> keyExtractor
  ) {
    Objects.requireNonNull(rows, "rows");
    Objects.requireNonNull(expectedKeys, "expectedKeys");
    Set<K> actualKeys = validateUniqueKeys(rows, keyExtractor);
    if (!expectedKeys.containsAll(actualKeys)) {
      throw new IllegalArgumentException("manifest contains an unaccounted key");
    }
    if (!actualKeys.containsAll(expectedKeys)) {
      throw new IllegalArgumentException("manifest is missing an expected key");
    }
  }

  private static <T, K> Set<K> validateUniqueKeys(List<T> rows, Function<T, K> keyExtractor) {
    Set<K> observed = new HashSet<>();
    for (T row : rows) {
      K key = Objects.requireNonNull(keyExtractor.apply(Objects.requireNonNull(row, "row")), "key");
      if (!observed.add(key)) {
        throw new IllegalArgumentException("manifest contains a duplicate key");
      }
    }
    return Set.copyOf(observed);
  }

  private static List<AdminCategoryOperationMatrixCase> dispatchManifest() {
    return PROFILES.stream()
      .flatMap(profile ->
        OPERATIONS.stream().map(operation ->
          dispatchCase(profile, operation, "V(" + operation + ")")
        )
      )
      .toList();
  }

  private static AdminCategoryOperationMatrixCase dispatchCase(
    String profile,
    Operation operation,
    String inputCondition
  ) {
    boolean declared = manifestProfileDeclarations(profile).contains(operation);
    return dispatchCaseForDeclared(profile, operation, declared, inputCondition);
  }

  private static AdminCategoryOperationMatrixCase dispatchCaseForDeclared(
    String profile,
    Operation operation,
    boolean declared
  ) {
    return dispatchCaseForDeclared(profile, operation, declared, "V(" + operation + ")");
  }

  private static AdminCategoryOperationMatrixCase dispatchCaseForDeclared(
    String profile,
    Operation operation,
    boolean declared,
    String inputCondition
  ) {
    OperationOracle oracle = operationOracle(operation);
    return new AdminCategoryOperationMatrixCase(
      profile,
      operation,
      declared,
      "authenticated",
      declared ? "exact-selected-permission" : "sufficient-preliminary-permission",
      inputCondition,
      declared ? oracle.successResult() : "409 CATEGORY_CAPABILITY_UNSUPPORTED",
      declared ? "none" : "CATEGORY_CAPABILITY_UNSUPPORTED",
      declared ? successfulPortCalls(operation) : List.of(),
      declared ? SUCCESS_FORBIDDEN : UNSUPPORTED_FORBIDDEN,
      declared
        ? "no-unsupported-warn;preserve-frozen-operation-log"
        : "one-bounded-capability-unsupported-warn",
      declared
        ? "operation is present in the profile's literal declaration set"
        : "operation is absent from the profile's literal declaration set"
    );
  }

  private static AdminCategoryOperationMatrixCase expectedDispatchCase(
    String profile,
    Operation operation
  ) {
    return dispatchCaseForDeclared(
      profile,
      operation,
      expectedProfileDeclarations(profile).contains(operation)
    );
  }

  private static Set<Operation> manifestProfileDeclarations(String profile) {
    return switch (profile) {
      case "catalogue", "descriptor-a" -> Set.copyOf(OPERATIONS);
      case "neutral-flat" -> Set.of(Operation.CAPABILITIES, Operation.LIST, Operation.GET);
      case "neutral-restricted" -> Set.of(Operation.CAPABILITIES, Operation.LIST, Operation.CREATE);
      default -> throw new IllegalArgumentException("unknown manifest profile");
    };
  }

  private static Set<Operation> expectedProfileDeclarations(String profile) {
    return switch (profile) {
      case "catalogue" -> Set.of(
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
      case "descriptor-a" -> Set.of(
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
      case "neutral-flat" -> Set.of(Operation.CAPABILITIES, Operation.LIST, Operation.GET);
      case "neutral-restricted" -> Set.of(Operation.CAPABILITIES, Operation.LIST, Operation.CREATE);
      default -> throw new IllegalArgumentException("unknown expected profile");
    };
  }

  private static List<AdminCategoryOperationMatrixCase> authorizationManifest() {
    return OPERATIONS.stream()
      .flatMap(operation ->
        AUTHORITY_STATES.stream().map(state -> expectedAuthorizationCase(operation, state))
      )
      .toList();
  }

  private static AdminCategoryOperationMatrixCase expectedAuthorizationCase(
    Operation operation,
    String state
  ) {
    boolean success = authorizationSucceeds(operation, state);
    boolean unauthenticated = state.equals("unauthenticated");
    OperationOracle oracle = operationOracle(operation);
    return new AdminCategoryOperationMatrixCase(
      "catalogue",
      operation,
      true,
      unauthenticated ? "unauthenticated" : "authenticated",
      state,
      "V(" + operation + ")",
      unauthenticated ? "401 UNAUTHENTICATED" : success ? oracle.successResult() : "403 FORBIDDEN",
      unauthenticated ? "UNAUTHENTICATED" : success ? "none" : "FORBIDDEN",
      success ? successfulPortCalls(operation) : List.of(),
      success ? SUCCESS_FORBIDDEN : DENIED_FORBIDDEN,
      unauthenticated
        ? "no-generic-category-application-log;safe-access-security-only"
        : success
          ? "no-unsupported-warn;preserve-frozen-operation-log"
          : "no-generic-category-or-unsupported-log;safe-access-security-only",
      success
        ? "full profile declares the operation and the selected permission class is satisfied"
        : unauthenticated
          ? "authentication precedes registration disclosure and application work"
          : "the authority state does not satisfy the selected operation permission"
    );
  }

  private static boolean authorizationSucceeds(Operation operation, String state) {
    return switch (state) {
      case "exact-selected-permission" -> true;
      case "selected-system-read-only" -> READ_OPERATIONS.contains(operation);
      case "selected-system-write-only" -> !READ_OPERATIONS.contains(operation);
      case
        "unauthenticated",
        "no-permission",
        "corresponding-other-system-permission",
        "legacy-global",
        "scope-only",
        "unrelated-role-permission",
        "role-admin-only",
        "role-moderator-only" -> false;
      default -> throw new IllegalArgumentException("unknown authorization state");
    };
  }

  private static List<AdminCategoryOperationMatrixCase> restrictedManifest() {
    return OPERATIONS.stream()
      .filter(operation -> !EXPECTED_RESTRICTED_DECLARATIONS.contains(operation))
      .flatMap(operation ->
        RESTRICTED_PRECEDENCE_STATES.stream().map(state -> expectedRestrictedCase(operation, state))
      )
      .toList();
  }

  private static AdminCategoryOperationMatrixCase expectedRestrictedCase(
    Operation operation,
    String state
  ) {
    boolean unauthenticated = state.equals("unauthenticated");
    boolean preliminaryPermission = state.equals("sufficient-preliminary-permission");
    if (
      !unauthenticated && !preliminaryPermission && !state.equals("missing-preliminary-permission")
    ) {
      throw new IllegalArgumentException("unknown restricted precedence state");
    }
    return new AdminCategoryOperationMatrixCase(
      "neutral-restricted",
      operation,
      false,
      unauthenticated ? "unauthenticated" : "authenticated",
      state,
      restrictedCompetingInput(operation),
      unauthenticated
        ? "401 UNAUTHENTICATED"
        : preliminaryPermission
          ? "409 CATEGORY_CAPABILITY_UNSUPPORTED"
          : "403 FORBIDDEN",
      unauthenticated
        ? "UNAUTHENTICATED"
        : preliminaryPermission
          ? "CATEGORY_CAPABILITY_UNSUPPORTED"
          : "FORBIDDEN",
      List.of(),
      preliminaryPermission ? UNSUPPORTED_FORBIDDEN : DENIED_FORBIDDEN,
      unauthenticated
        ? "no-generic-category-application-log;safe-access-security-only"
        : preliminaryPermission
          ? "one-bounded-capability-unsupported-warn"
          : "no-generic-category-or-unsupported-log;safe-access-security-only",
      preliminaryPermission
        ? "operation is undeclared and capability rejection precedes competing input work"
        : unauthenticated
          ? "authentication precedes capability and competing input work"
          : "preliminary authorization precedes capability and competing input work"
    );
  }

  private static String restrictedCompetingInput(Operation operation) {
    return switch (operation) {
      case TREE -> "requested size 10001";
      case PARENT_OPTIONS -> "opaque cursor not-base64 with otherwise valid query";
      case GET, BREADCRUMB, DELETE, ACTIVATE, DEACTIVATE -> "numeric path identifier 0";
      case REPLACE -> "identifier 0 with syntactically valid replace input";
      case PATCH -> "identifier 0 with syntactically valid merge-patch input";
      case MOVE, REORDER -> "identifier 0 with two conflicting placement selectors";
      case CAPABILITIES, LIST, CREATE -> throw new IllegalArgumentException(
        "declared restricted operation has no precedence row"
      );
    };
  }

  private static List<AdminCategoryOperationMatrixCase> systemKeyManifest() {
    List<AdminCategoryOperationMatrixCase> rows = new ArrayList<>();
    for (int rowNumber = 1; rowNumber <= SYSTEM_KEY_INPUTS.size(); rowNumber++) {
      rows.add(expectedSystemKeyCase(rowNumber));
    }
    return List.copyOf(rows);
  }

  private static AdminCategoryOperationMatrixCase expectedSystemKeyCase(int rowNumber) {
    String input = SYSTEM_KEY_INPUTS.get(rowNumber - 1);
    return switch (rowNumber) {
      case 1 -> systemKeyCase(
        "catalogue",
        true,
        "unauthenticated",
        "no-authority",
        input,
        "401 UNAUTHENTICATED",
        "UNAUTHENTICATED",
        DENIED_FORBIDDEN,
        "no-generic-category-application-log;safe-access-security-only",
        "authentication precedes key lookup and registration disclosure"
      );
      case 2 -> systemKeyCase(
        "catalogue",
        true,
        "authenticated",
        "missing-preliminary-read-permission",
        input,
        "403 FORBIDDEN",
        "FORBIDDEN",
        DENIED_FORBIDDEN,
        "no-generic-category-or-unsupported-log;safe-access-security-only",
        "preliminary authorization precedes application lookup"
      );
      case 3 -> knownSystemKeySuccess(input, "exact-selected-read-permission", "exact lookup");
      case 4 -> knownSystemKeySuccess(
        input,
        "exact-selected-read-permission",
        "single trim then exact lookup"
      );
      case 5 -> unknownSystemKeyCase(
        "missing-system",
        input,
        "exact preliminary unknown-system read permission",
        "exact immutable lookup reports absent; no fallback"
      );
      case 6 -> unknownSystemKeyCase(
        "uppercase-key",
        input,
        "exact preliminary uppercase-key read permission",
        "exact immutable lookup only; no case folding"
      );
      case 7 -> unknownSystemKeyCase(
        "mixed-case-key",
        input,
        "exact preliminary mixed-case-key read permission",
        "exact immutable lookup only; no case folding"
      );
      case 8 -> unknownSystemKeyCase(
        "blank-key",
        input,
        "sufficient safe preliminary authority",
        "safe lookup translation; raw blank value remains undisclosed"
      );
      case 9 -> unknownSystemKeyCase(
        "malformed-key",
        input,
        "sufficient safely derived preliminary authority",
        "safe lookup translation; raw malformed value remains undisclosed"
      );
      case 10 -> unknownSystemKeyCase(
        "overlength-key",
        input,
        "sufficient safely derived preliminary authority",
        "safe lookup translation; raw overlength value remains undisclosed"
      );
      case 11 -> systemKeyCase(
        "no-selected-profile",
        false,
        "transport-owned",
        "authentication-and-authority-not-evaluated",
        input,
        "NO_ROUTE",
        "N/A",
        List.of("category-controller.0", "category-application.0", "fallback.0"),
        "transport-access-only;no-generic-category-application-log",
        "stable category error is inapplicable because no category handler owns the route"
      );
      case 12 -> systemKeyCase(
        "null-system-key",
        false,
        "direct-application-contract",
        "HTTP-authority-not-evaluated",
        input,
        "404 UNKNOWN_CATEGORY_SYSTEM",
        "UNKNOWN_CATEGORY_SYSTEM",
        List.of("construction-exception.0", "provider-and-storage-fallback.0", "persistence.0"),
        "safe-bounded-unknown-outcome;no-unsupported-warn",
        "direct null input translates to the stable unknown-system outcome"
      );
      default -> throw new IllegalArgumentException("unknown system-key row number");
    };
  }

  private static AdminCategoryOperationMatrixCase knownSystemKeySuccess(
    String input,
    String authority,
    String evidence
  ) {
    return systemKeyCase(
      "catalogue",
      true,
      "authenticated",
      authority,
      input,
      operationOracle(Operation.CAPABILITIES).successResult(),
      "none",
      SUCCESS_FORBIDDEN,
      "preserve-capability-access-log-shape;no-unsupported-warn",
      evidence + "; catalogue selected; provider ports remain zero"
    );
  }

  private static AdminCategoryOperationMatrixCase unknownSystemKeyCase(
    String profileClassification,
    String input,
    String authority,
    String evidence
  ) {
    return systemKeyCase(
      profileClassification,
      false,
      "authenticated",
      authority,
      input,
      "404 UNKNOWN_CATEGORY_SYSTEM",
      "UNKNOWN_CATEGORY_SYSTEM",
      List.of(
        "capability-and-definition-work.0",
        "provider-source-and-storage-fallback.0",
        "repository-sql-and-mutation.0"
      ),
      "safe-bounded-unknown-outcome;no-unsupported-warn",
      evidence
    );
  }

  private static AdminCategoryOperationMatrixCase systemKeyCase(
    String profileClassification,
    boolean declared,
    String authentication,
    String authority,
    String input,
    String result,
    String error,
    List<String> forbidden,
    String log,
    String evidence
  ) {
    return new AdminCategoryOperationMatrixCase(
      profileClassification,
      Operation.CAPABILITIES,
      declared,
      authentication,
      authority,
      input,
      result,
      error,
      List.of(),
      forbidden,
      log,
      evidence
    );
  }

  private static List<AdminCategoryOperationMatrixCase> isolationPairs() {
    return OPERATIONS.stream()
      .map(AdminCategoryContractInfrastructureTest::expectedIsolationPair)
      .toList();
  }

  private static AdminCategoryOperationMatrixCase expectedIsolationPair(Operation operation) {
    List<String> calls =
      operation == Operation.CAPABILITIES
        ? List.of()
        : List.of(operationOracle(operation).callToken(), operationOracle(operation).callToken());
    return new AdminCategoryOperationMatrixCase(
      "catalogue<->descriptor-a",
      operation,
      true,
      "authenticated",
      "both-selected-system-exact-permissions",
      "two directions with overlapping identifiers, slugs, and divergent selected values",
      "TWO_DIRECTIONS_VERIFIED",
      "none",
      calls,
      ISOLATION_FORBIDDEN,
      "selected-system-bounded-log-only;no-cross-system-values",
      isolationEvidence(operation) + "; both directional stores observed independently"
    );
  }

  private static List<AdminCategoryOperationMatrixCase> isolationDirections() {
    return OPERATIONS.stream()
      .flatMap(operation ->
        ISOLATION_SYSTEMS.stream().map(selected -> expectedIsolationDirection(operation, selected))
      )
      .toList();
  }

  private static AdminCategoryOperationMatrixCase expectedIsolationDirection(
    Operation operation,
    String selectedSystem
  ) {
    if (!ISOLATION_SYSTEMS.contains(selectedSystem)) {
      throw new IllegalArgumentException("unknown selected isolation system");
    }
    String otherSystem = selectedSystem.equals("catalogue") ? "descriptor-a" : "catalogue";
    return new AdminCategoryOperationMatrixCase(
      selectedSystem,
      operation,
      true,
      "authenticated",
      "selected-system-exact-permission",
      "selected=" + selectedSystem + ";other=" + otherSystem + ";overlapping fixture",
      operationOracle(operation).successResult(),
      "none",
      successfulPortCalls(operation),
      ISOLATION_FORBIDDEN,
      "selected-system-bounded-log-only;no-cross-system-values",
      isolationEvidence(operation) + "; non-selected " + otherSystem + " store unchanged"
    );
  }

  private static String isolationEvidence(Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> "selected capability bytes and zero provider ports";
      case LIST -> "selected roots children ordering and page metadata only";
      case TREE -> "selected root descendants depth and bound only";
      case PARENT_OPTIONS -> "selected slice paths hydration cursor and hasMore only";
      case GET -> "selected equal identifier through category-read.getNode only";
      case BREADCRUMB -> "selected root-to-node ancestor chain only";
      case CREATE -> "same slug creates independently in the selected store";
      case REPLACE -> "selected equal identifier fields hierarchy and rank only";
      case PATCH -> "selected merge-patch presence absence and explicit-null behavior only";
      case DELETE -> "selected deletable probe and selected deletion guards only";
      case MOVE -> "selected parent closure hierarchy and rank only";
      case REORDER -> "selected sibling order and rank only";
      case ACTIVATE -> "selected inactive row lifecycle state only";
      case DEACTIVATE -> "selected active row lifecycle state only";
    };
  }

  private static Set<DispatchKey> expectedDispatchKeys() {
    return crossProduct(PROFILES, OPERATIONS, DispatchKey::new);
  }

  private static Set<AuthorizationKey> expectedAuthorizationKeys() {
    return crossProduct(OPERATIONS, AUTHORITY_STATES, AuthorizationKey::new);
  }

  private static Set<RestrictedKey> expectedRestrictedKeys() {
    List<Operation> unsupported = OPERATIONS.stream()
      .filter(operation -> !EXPECTED_RESTRICTED_DECLARATIONS.contains(operation))
      .toList();
    return crossProduct(unsupported, RESTRICTED_PRECEDENCE_STATES, RestrictedKey::new);
  }

  private static Set<SystemKeyKey> expectedSystemKeyKeys() {
    return SYSTEM_KEY_INPUTS.stream()
      .map(SystemKeyKey::new)
      .collect(Collectors.toUnmodifiableSet());
  }

  private static Set<IsolationDirectionKey> expectedIsolationDirectionKeys() {
    return crossProduct(OPERATIONS, ISOLATION_SYSTEMS, (operation, system) ->
      new IsolationDirectionKey(system, operation)
    );
  }

  private static DispatchKey dispatchKey(AdminCategoryOperationMatrixCase row) {
    return new DispatchKey(row.profileOrSystem(), row.operation());
  }

  private static AuthorizationKey authorizationKey(AdminCategoryOperationMatrixCase row) {
    return new AuthorizationKey(row.operation(), row.authorityState());
  }

  private static RestrictedKey restrictedKey(AdminCategoryOperationMatrixCase row) {
    return new RestrictedKey(row.operation(), row.authorityState());
  }

  private static SystemKeyKey systemKey(AdminCategoryOperationMatrixCase row) {
    return new SystemKeyKey(row.inputCondition());
  }

  private static IsolationDirectionKey isolationDirectionKey(AdminCategoryOperationMatrixCase row) {
    return new IsolationDirectionKey(row.profileOrSystem(), row.operation());
  }

  private static OperationOracle operationOracle(Operation operation) {
    return Objects.requireNonNull(OPERATION_ORACLE_BY_OPERATION.get(operation), "operation oracle");
  }

  private static List<String> successfulPortCalls(Operation operation) {
    return operation == Operation.CAPABILITIES
      ? List.of()
      : List.of(operationOracle(operation).callToken());
  }

  private static <L, R, K> Set<K> crossProduct(
    List<L> left,
    List<R> right,
    java.util.function.BiFunction<L, R, K> keyFactory
  ) {
    return left
      .stream()
      .flatMap(leftValue ->
        right.stream().map(rightValue -> keyFactory.apply(leftValue, rightValue))
      )
      .collect(Collectors.toUnmodifiableSet());
  }

  private static String fingerprint(List<AdminCategoryOperationMatrixCase> rows) {
    MessageDigest digest = sha256();
    appendInt(digest, rows.size());
    for (AdminCategoryOperationMatrixCase row : rows) {
      appendString(digest, row.profileOrSystem());
      appendString(digest, row.operation().name());
      appendString(digest, Boolean.toString(row.declaredCapability()));
      appendString(digest, row.authenticationState());
      appendString(digest, row.authorityState());
      appendString(digest, row.inputCondition());
      appendString(digest, row.expectedStatusOrResult());
      appendString(digest, row.expectedStableError());
      appendStrings(digest, row.expectedPortCalls());
      appendStrings(digest, row.forbiddenInteractions());
      appendString(digest, row.expectedLogOutcome());
      appendString(digest, row.applicabilityEvidence());
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void appendStrings(MessageDigest digest, List<String> values) {
    appendInt(digest, values.size());
    values.forEach(value -> appendString(digest, value));
  }

  private static void appendString(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    appendInt(digest, bytes.length);
    digest.update(bytes);
  }

  private static void appendInt(MessageDigest digest, int value) {
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
  }

  private static AdminCategoryOperationMatrixCase matrixCase(
    String profile,
    Operation operation,
    boolean declared,
    String result,
    String error,
    List<String> calls,
    List<String> forbidden,
    String log,
    String evidence
  ) {
    return new AdminCategoryOperationMatrixCase(
      profile,
      operation,
      declared,
      "authenticated",
      "authorized",
      "default",
      result,
      error,
      calls,
      forbidden,
      log,
      evidence
    );
  }

  @SuppressWarnings("unchecked")
  private static AdminCategoryOperationMatrixCase matrixCaseWith(int index, Object value) {
    Object[] dimensions = {
      "catalogue",
      Operation.CAPABILITIES,
      "authenticated",
      "authorized",
      "default",
      "OK",
      "none",
      List.of(),
      List.of("fallback.0"),
      "safe",
      "baseline evidence",
    };
    dimensions[index] = value;
    return new AdminCategoryOperationMatrixCase(
      (String) dimensions[0],
      (Operation) dimensions[1],
      true,
      (String) dimensions[2],
      (String) dimensions[3],
      (String) dimensions[4],
      (String) dimensions[5],
      (String) dimensions[6],
      (List<String>) dimensions[7],
      (List<String>) dimensions[8],
      (String) dimensions[9],
      (String) dimensions[10]
    );
  }

  private static AdminCategoryOperationMatrixCase withExpectedOutcome(
    AdminCategoryOperationMatrixCase row,
    String result,
    String error
  ) {
    return copyRow(
      row,
      row.declaredCapability(),
      row.authenticationState(),
      row.authorityState(),
      result,
      error,
      row.expectedPortCalls(),
      row.applicabilityEvidence()
    );
  }

  private static AdminCategoryOperationMatrixCase withExpectedCalls(
    AdminCategoryOperationMatrixCase row,
    List<String> calls
  ) {
    return copyRow(
      row,
      row.declaredCapability(),
      row.authenticationState(),
      row.authorityState(),
      row.expectedStatusOrResult(),
      row.expectedStableError(),
      calls,
      row.applicabilityEvidence()
    );
  }

  private static AdminCategoryOperationMatrixCase withEvidence(
    AdminCategoryOperationMatrixCase row,
    String evidence
  ) {
    return copyRow(
      row,
      row.declaredCapability(),
      row.authenticationState(),
      row.authorityState(),
      row.expectedStatusOrResult(),
      row.expectedStableError(),
      row.expectedPortCalls(),
      evidence
    );
  }

  private static AdminCategoryOperationMatrixCase withAuthorityState(
    AdminCategoryOperationMatrixCase row,
    String authorityState
  ) {
    return copyRow(
      row,
      row.declaredCapability(),
      row.authenticationState(),
      authorityState,
      row.expectedStatusOrResult(),
      row.expectedStableError(),
      row.expectedPortCalls(),
      row.applicabilityEvidence()
    );
  }

  private static AdminCategoryOperationMatrixCase copyRow(
    AdminCategoryOperationMatrixCase row,
    boolean declared,
    String authentication,
    String authority,
    String result,
    String error,
    List<String> calls,
    String evidence
  ) {
    return new AdminCategoryOperationMatrixCase(
      row.profileOrSystem(),
      row.operation(),
      declared,
      authentication,
      authority,
      row.inputCondition(),
      result,
      error,
      calls,
      row.forbiddenInteractions(),
      row.expectedLogOutcome(),
      evidence
    );
  }

  private static void replaceDispatchRow(
    List<AdminCategoryOperationMatrixCase> rows,
    AdminCategoryOperationMatrixCase replacement
  ) {
    AdminCategoryOperationMatrixCase current = findRow(
      rows,
      row ->
        row.profileOrSystem().equals(replacement.profileOrSystem()) &&
        row.operation() == replacement.operation()
    );
    replaceRow(rows, current, replacement);
  }

  private static void replaceRow(
    List<AdminCategoryOperationMatrixCase> rows,
    AdminCategoryOperationMatrixCase current,
    AdminCategoryOperationMatrixCase replacement
  ) {
    int index = rows.indexOf(current);
    if (index < 0) {
      throw new IllegalArgumentException("row replacement target is absent");
    }
    rows.set(index, replacement);
  }

  private static AdminCategoryOperationMatrixCase findRow(
    List<AdminCategoryOperationMatrixCase> rows,
    java.util.function.Predicate<AdminCategoryOperationMatrixCase> predicate
  ) {
    return rows
      .stream()
      .filter(predicate)
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("expected manifest row is absent"));
  }

  private static <T, K> void assertDuplicateGuard(List<T> rows, Function<T, K> keyExtractor) {
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
      validateUniqueKeys(rows, keyExtractor)
    );
    assertEquals("manifest contains a duplicate key", failure.getMessage());
  }

  private static <T, K> void assertManifestFailure(
    String expectedMessage,
    List<T> rows,
    Set<K> expectedKeys,
    Function<T, K> keyExtractor
  ) {
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
      validateExactManifest(rows, expectedKeys, keyExtractor)
    );
    assertEquals(expectedMessage, failure.getMessage());
  }

  private static void requireText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("matrix field must not be blank");
    }
  }

  private static void requireExecutableList(List<String> values) {
    Objects.requireNonNull(values, "matrix list");
    values.forEach(AdminCategoryContractInfrastructureTest::requireText);
  }

  private static boolean isNotApplicable(String value) {
    return "N/A".equalsIgnoreCase(value.trim());
  }

  private record OperationOracle(
    Operation operation,
    PortSlot port,
    String callToken,
    String successResult
  ) {}

  private record InvalidDimension(
    int index,
    Object value,
    Class<? extends Throwable> exceptionType
  ) {}

  private record DispatchKey(String profile, Operation operation) {}

  private record AuthorizationKey(Operation operation, String authorityState) {}

  private record RestrictedKey(Operation operation, String precedenceState) {}

  private record SystemKeyKey(String inputCondition) {}

  private record IsolationDirectionKey(String selectedSystem, Operation operation) {}
}
