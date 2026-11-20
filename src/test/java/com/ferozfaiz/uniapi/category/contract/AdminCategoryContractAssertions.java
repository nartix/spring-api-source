package com.ferozfaiz.uniapi.category.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryProviderCapability;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.InteractionLedger;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.OperationObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.OutputObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.QueryObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.StateSnapshot;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.TransactionObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.Applicability;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.OperationExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.PortSlot;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.QueryCountExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.StableErrorExpectation;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdminCategoryContractAssertions {

  public static final String NO_STABLE_ERROR = "none";
  public static final String NO_LOG_OUTCOME = "none";

  private AdminCategoryContractAssertions() {}

  public static void assertMatrixManifest(
    String manifestName,
    List<AdminCategoryOperationMatrixCase> rows,
    int expectedCardinality
  ) {
    String selectedManifestName = Objects.requireNonNull(manifestName, "manifestName").trim();
    assertFalse(selectedManifestName.isBlank(), "manifestName must not be blank");
    assertTrue(expectedCardinality >= 0, "expectedCardinality must be non-negative");
    List<AdminCategoryOperationMatrixCase> selectedRows = List.copyOf(
      Objects.requireNonNull(rows, "rows")
    );
    assertEquals(
      expectedCardinality,
      selectedRows.size(),
      selectedManifestName + " cardinality mismatch"
    );

    Set<String> caseIds = new HashSet<>();
    for (AdminCategoryOperationMatrixCase row : selectedRows) {
      Objects.requireNonNull(row, "matrix row");
      assertTrue(
        caseIds.add(row.caseId()),
        selectedManifestName + " contains duplicate case ID " + row.caseId()
      );
      assertFalse(row.expectedStatusOrResult().isBlank(), row.caseId() + " result is blank");
      assertFalse(row.expectedStableError().isBlank(), row.caseId() + " error is blank");
      assertFalse(row.expectedLogOutcome().isBlank(), row.caseId() + " log outcome is blank");
      assertFalse(
        row.expectedPortCalls().stream().anyMatch(AdminCategoryContractAssertions::isNotApplicable),
        row.caseId() + " has an unexplained N/A port call"
      );
      assertFalse(
        row
          .forbiddenInteractions()
          .stream()
          .anyMatch(AdminCategoryContractAssertions::isNotApplicable),
        row.caseId() + " has an unexplained N/A forbidden interaction"
      );
      assertFalse(
        isNotApplicable(row.expectedLogOutcome()),
        row.caseId() + " has an unexplained N/A log outcome"
      );
      if (
        isNotApplicable(row.expectedStatusOrResult()) || isNotApplicable(row.expectedStableError())
      ) {
        assertFalse(
          isNotApplicable(row.applicabilityEvidence()),
          row.caseId() + " has unexplained N/A outcome evidence"
        );
        assertFalse(
          row.forbiddenInteractions().isEmpty(),
          row.caseId() + " N/A outcome lacks an executable forbidden-interaction ledger"
        );
      }
    }
  }

  public static void assertRegistration(
    AdminCategoryProviderContractProfile profile,
    AdminCategoryProviderContractFixture fixture,
    Operation operation
  ) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(fixture, "fixture");
    Objects.requireNonNull(operation, "operation");
    AdminCategorySystemRegistration registration = selectedRegistration(profile, fixture);
    assertEquals(profile.definition(), registration.definition().orElseThrow());
    assertEquals(
      profile.declaredOperations(),
      registration.definition().orElseThrow().operations().supportedOperations()
    );
    assertTrue(profile.declaredOperations().contains(operation));

    var selectedPorts = registration.runtimeBinding().orElseThrow().ports();
    assertSame(selectedPorts, fixture.ports());
    assertExactPortCoverage(profile, selectedPorts);

    AdminCategorySystemRegistration factoryRegistration = profile.newRegistration();
    assertEquals(profile.definition(), factoryRegistration.definition().orElseThrow());
    assertExactPortCoverage(profile, factoryRegistration.runtimeBinding().orElseThrow().ports());

    List<AdminCategorySystemRegistration> firstSnapshot = fixture.registry().registrations();
    List<AdminCategorySystemRegistration> secondSnapshot = fixture.registry().registrations();
    assertTrue(firstSnapshot.equals(secondSnapshot), "registry enumeration is not deterministic");
    assertTrue(
      firstSnapshot.contains(registration),
      "selected registration is absent from registry"
    );
    List<String> systemKeys = firstSnapshot
      .stream()
      .map(candidate -> candidate.definition().orElseThrow().systemKey())
      .toList();
    assertEquals(systemKeys.size(), systemKeys.stream().distinct().count());
    assertThrows(UnsupportedOperationException.class, () -> firstSnapshot.add(registration));
  }

  public static void assertUnsupportedSeam(
    AdminCategoryProviderContractProfile profile,
    AdminCategoryProviderContractFixture fixture,
    Operation operation
  ) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(fixture, "fixture");
    Objects.requireNonNull(operation, "operation");
    AdminCategorySystemRegistration registration = selectedRegistration(profile, fixture);
    assertFalse(profile.declaredOperations().contains(operation));
    assertFalse(
      registration.definition().orElseThrow().operations().supportedOperations().contains(operation)
    );

    fixture.reset();
    fixture.seed(profile.seedRecords());
    StateSnapshot publicBefore = fixture.snapshot();
    StateSnapshot providerBefore = fixture.providerState();

    OperationObservation observation = fixture.invoke(operation);

    assertResult(profile.expectation(operation), observation);
    assertNoInteractions(fixture.interactions());
    assertTrue(fixture.queryObservation().isEmpty(), "unsupported operation performed query work");
    assertEquals(publicBefore, fixture.snapshot(), "unsupported operation changed public state");
    assertEquals(
      providerBefore,
      fixture.providerState(),
      "unsupported operation changed provider state"
    );
    assertEquals(
      observation.output(),
      fixture.outputObservation(),
      "fixture did not expose the observed unsupported output"
    );
  }

  public static void assertCompleteOperationApplicability(
    AdminCategoryProviderContractProfile profile
  ) {
    Objects.requireNonNull(profile, "profile");
    assertEquals(14, Operation.values().length, "logical operation inventory drifted");
    assertEquals(
      Set.of(Operation.values()),
      profile.operationExpectations().keySet(),
      "operation applicability must classify all fourteen operations"
    );
    for (Operation operation : Operation.values()) {
      OperationExpectation expectation = profile.expectation(operation);
      assertTrue(expectation.applicability().applicable(), expectation.applicability().evidence());
      assertFalse(expectation.applicability().evidence().isBlank());
      assertEquals(
        profile.declaredOperations().contains(operation),
        !"CATEGORY_CAPABILITY_UNSUPPORTED".equals(expectation.expectedStableError()),
        "operation applicability classification mismatch for " + operation.code()
      );
      if (profile.declaredOperations().contains(operation)) {
        assertEquals(
          AdminCategoryProviderContractProfile.canonicalCall(operation).isPresent(),
          profile.expectedOperationPorts().get(operation) != PortSlot.NONE,
          "canonical call/port applicability mismatch for " + operation.code()
        );
      }
    }
  }

  public static OperationObservation assertCanonicalInvocation(
    AdminCategoryProviderContractProfile profile,
    AdminCategoryProviderContractFixture fixture,
    Operation operation
  ) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(fixture, "fixture");
    Objects.requireNonNull(operation, "operation");
    assertTrue(profile.declaredOperations().contains(operation));
    OperationObservation observation = fixture.invoke(operation);
    assertResult(profile.expectation(operation), observation);
    assertInteractions(
      AdminCategoryProviderContractProfile.canonicalCall(operation).stream().toList(),
      profile.interactionExpectation().forbiddenInteractions(),
      fixture.interactions()
    );
    assertEquals(
      observation.output(),
      fixture.outputObservation(),
      "fixture output observation did not describe the invocation"
    );
    return observation;
  }

  public static void assertExpectedListOrder(
    AdminCategoryProviderContractProfile profile,
    OperationObservation listObservation
  ) {
    Objects.requireNonNull(profile, "profile");
    assertEquals(
      profile.expectedDeterministicListOrder(),
      resultIds(listObservation),
      "list result order did not match the profile's deterministic order"
    );
    assertEquals(
      profile.expectedDeterministicListOrder().size(),
      profile.expectedDeterministicListOrder().stream().distinct().count(),
      "expected list order contains duplicate identifiers"
    );
  }

  public static void assertListGetAgreement(
    OperationObservation listObservation,
    OperationObservation getObservation
  ) {
    List<String> listedIds = resultIds(listObservation);
    String selectedId = Objects.requireNonNull(
      getObservation.publicResult().facts().get("id"),
      "GET result id fact"
    );
    assertTrue(listedIds.contains(selectedId), "GET result was absent from the deterministic list");
  }

  public static void assertStableMissingGet(
    StableErrorExpectation expectation,
    OperationObservation observation
  ) {
    Objects.requireNonNull(expectation, "expectation");
    Objects.requireNonNull(observation, "observation");
    assertTrue(observation.stableError().filter(expectation.code()::equals).isPresent());
    assertTrue(
      observation.publicResult().facts().containsValue(expectation.message()) ||
        observation.output().publicValues().contains(expectation.message()),
      expectation.applicabilityEvidence()
    );
    assertNoInteractionsForMissingGet(observation);
  }

  public static void assertExactProbeCorpusSafe(OutputObservation output) {
    Objects.requireNonNull(output, "output");
    List<String> surfaces = StreamSupport.values(output);
    InformationSafetyProbeCorpus.assertNoCaseSensitiveLeaks(surfaces);
    InformationSafetyProbeCorpus.assertNoCaseInsensitiveLeaks(surfaces);
    assertOutputSafe(output, InformationSafetyProbeCorpus.prohibitedLiterals());
  }

  public static void assertQueryExpectationMetadata(QueryCountExpectation expectation) {
    Objects.requireNonNull(expectation, "expectation");
    assertFalse(expectation.observationId().isBlank());
    assertTrue(expectation.smallCardinality() <= expectation.largeCardinality());
    assertFalse(expectation.applicability().evidence().isBlank());
    if (expectation.applicability().applicable()) {
      assertTrue(expectation.maximumStatements() > 0, expectation.applicability().evidence());
    }
  }

  public static void assertRollbackApplicability(
    AdminCategoryProviderContractProfile profile,
    Operation operation,
    Applicability applicability
  ) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(applicability, "applicability");
    assertTrue(profile.declaredOperations().contains(operation));
    assertSame(applicability, profile.rollbackApplicability().get(operation));
    assertFalse(applicability.evidence().isBlank());
  }

  public static void assertResult(
    OperationExpectation expectation,
    OperationObservation observation
  ) {
    Objects.requireNonNull(expectation, "expectation");
    Objects.requireNonNull(observation, "observation");
    assertTrue(expectation.applicability().applicable(), expectation.applicability().evidence());
    assertTrue(
      expectation.expectedResultId().equals(observation.resultId()),
      "operation result did not match its bounded expectation"
    );
    assertStableError(expectation.expectedStableError(), observation);
    if (NO_LOG_OUTCOME.equalsIgnoreCase(expectation.expectedLogOutcome())) {
      assertTrue(observation.output().logValues().isEmpty(), "unexpected contract log outcome");
    } else {
      assertTrue(
        observation.output().logValues().contains(expectation.expectedLogOutcome()),
        "expected contract log outcome was absent"
      );
    }
  }

  public static void assertStableError(
    String expectedStableError,
    OperationObservation observation
  ) {
    Objects.requireNonNull(expectedStableError, "expectedStableError");
    Objects.requireNonNull(observation, "observation");
    if (NO_STABLE_ERROR.equalsIgnoreCase(expectedStableError)) {
      assertTrue(observation.stableError().isEmpty(), "unexpected stable error");
      return;
    }
    assertTrue(
      observation.stableError().filter(expectedStableError::equals).isPresent(),
      "stable error did not match its bounded expectation"
    );
  }

  public static void assertStableError(
    StableErrorExpectation expectation,
    String actualCode,
    String actualMessage
  ) {
    Objects.requireNonNull(expectation, "expectation");
    assertTrue(expectation.code().equals(actualCode), "stable error code mismatch");
    assertTrue(expectation.message().equals(actualMessage), "stable error message mismatch");
  }

  public static void assertDeterministic(OperationObservation first, OperationObservation second) {
    Objects.requireNonNull(first, "first");
    Objects.requireNonNull(second, "second");
    assertTrue(first.equals(second), "operation observations are not deterministic");
  }

  public static void assertInteractions(
    List<String> expectedCalls,
    Collection<String> forbiddenInteractions,
    InteractionLedger actual
  ) {
    Objects.requireNonNull(expectedCalls, "expectedCalls");
    Objects.requireNonNull(forbiddenInteractions, "forbiddenInteractions");
    Objects.requireNonNull(actual, "actual");
    assertTrue(expectedCalls.equals(actual.calls()), "selected port-call ledger mismatch");
    assertTrue(actual.forbiddenInteractions().isEmpty(), "a forbidden interaction was observed");
    forbiddenInteractions.forEach(forbidden ->
      assertFalse(actual.calls().contains(forbidden), "a forbidden interaction was called")
    );
  }

  public static void assertNoInteractions(InteractionLedger actual) {
    assertInteractions(List.of(), List.of(), actual);
  }

  public static void assertTransaction(
    int expectedCommits,
    int expectedRollbacks,
    boolean expectedActiveDuringInvocation,
    TransactionObservation actual
  ) {
    Objects.requireNonNull(actual, "actual");
    assertEquals(expectedCommits, actual.commits());
    assertEquals(expectedRollbacks, actual.rollbacks());
    assertEquals(expectedActiveDuringInvocation, actual.activeDuringInvocation());
  }

  public static void assertRollback(
    StateSnapshot before,
    StateSnapshot after,
    StateSnapshot providerBefore,
    StateSnapshot providerAfter,
    TransactionObservation transaction
  ) {
    assertEquals(Objects.requireNonNull(before, "before"), after);
    assertEquals(Objects.requireNonNull(providerBefore, "providerBefore"), providerAfter);
    assertTransaction(0, 1, true, transaction);
  }

  public static void assertOutputSafe(
    OutputObservation output,
    Collection<String> prohibitedValues
  ) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(prohibitedValues, "prohibitedValues");
    List<String> surfaces = StreamSupport.values(output);
    int corpusIndex = 0;
    for (String prohibitedValue : prohibitedValues) {
      String normalizedProbe = Objects.requireNonNull(
        prohibitedValue,
        "prohibited value"
      ).toLowerCase(Locale.ROOT);
      for (String surface : surfaces) {
        assertFalse(
          surface.toLowerCase(Locale.ROOT).contains(normalizedProbe),
          "prohibited output value at corpus index " + corpusIndex
        );
      }
      corpusIndex++;
    }
  }

  public static void assertQuerySafety(
    QueryCountExpectation expectation,
    Optional<QueryObservation> observation
  ) {
    Objects.requireNonNull(expectation, "expectation");
    Objects.requireNonNull(observation, "observation");
    if (!expectation.applicability().applicable()) {
      assertTrue(observation.isEmpty(), expectation.applicability().evidence());
      return;
    }
    QueryObservation query = observation.orElseThrow();
    assertTrue(query.statementCount() <= expectation.maximumStatements());
    assertEquals(query.statementCount(), query.statementKinds().size());
    assertEquals(query.statementCount(), query.normalizedStatementFingerprints().size());
  }

  public static void assertEqualQueryCounts(
    QueryObservation smallCardinality,
    QueryObservation largeCardinality
  ) {
    Objects.requireNonNull(smallCardinality, "smallCardinality");
    Objects.requireNonNull(largeCardinality, "largeCardinality");
    assertEquals(smallCardinality.statementCount(), largeCardinality.statementCount());
  }

  private static List<String> resultIds(OperationObservation observation) {
    Objects.requireNonNull(observation, "observation");
    String encoded = Objects.requireNonNull(
      observation.publicResult().facts().get("ids"),
      "LIST result ids fact"
    ).trim();
    if (encoded.startsWith("[") && encoded.endsWith("]")) {
      encoded = encoded.substring(1, encoded.length() - 1);
    }
    if (encoded.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(encoded.split(","))
      .map(String::trim)
      .filter(value -> !value.isEmpty())
      .toList();
  }

  private static boolean isNotApplicable(String value) {
    return "N/A".equalsIgnoreCase(Objects.requireNonNull(value, "value").trim());
  }

  private static void assertNoInteractionsForMissingGet(OperationObservation observation) {
    assertTrue(
      observation.output().attachedCauses().isEmpty(),
      "stable missing GET exposed an attached cause"
    );
  }

  private static AdminCategorySystemRegistration selectedRegistration(
    AdminCategoryProviderContractProfile profile,
    AdminCategoryProviderContractFixture fixture
  ) {
    AdminCategorySystemRegistration fixtureRegistration = Objects.requireNonNull(
      fixture.registration(),
      "fixture registration"
    );
    AdminCategorySystemRegistration selected = fixture
      .registry()
      .findRegistration(profile.definition().systemKey())
      .orElseThrow();
    assertSame(selected, fixtureRegistration);
    return selected;
  }

  private static void assertExpectedPortIdentity(
    PortSlot expectedPort,
    Operation operation,
    AdminCategoryRuntimePorts ports
  ) {
    if (expectedPort == PortSlot.NONE) {
      assertTrue(ports.portFor(operation).isEmpty());
      return;
    }
    Object typedPort = switch (expectedPort) {
      case CATEGORY_READ -> ports.categoryRead().orElseThrow();
      case HIERARCHY_READ -> ports.hierarchyRead().orElseThrow();
      case BASIC_MUTATION -> ports.basicMutation().orElseThrow();
      case HIERARCHY_MUTATION -> ports.hierarchyMutation().orElseThrow();
      case ORDERING -> ports.ordering().orElseThrow();
      case LIFECYCLE -> ports.lifecycle().orElseThrow();
      case NONE -> throw new IllegalStateException("NONE was handled above");
    };
    assertSame(typedPort, ports.portFor(operation).orElseThrow());
  }

  private static void assertExactPortCoverage(
    AdminCategoryProviderContractProfile profile,
    AdminCategoryRuntimePorts ports
  ) {
    Set<AdminCategoryProviderCapability> expectedCapabilities = profile
      .expectedOperationPorts()
      .values()
      .stream()
      .filter(port -> port != PortSlot.NONE)
      .map(port -> AdminCategoryProviderCapability.valueOf(port.name()))
      .collect(Collectors.toUnmodifiableSet());
    assertEquals(expectedCapabilities, ports.populatedCapabilities());
    profile
      .expectedOperationPorts()
      .forEach((declaredOperation, expectedPort) ->
        assertExpectedPortIdentity(expectedPort, declaredOperation, ports)
      );
  }

  private static final class StreamSupport {

    private StreamSupport() {}

    private static List<String> values(OutputObservation output) {
      return java.util.stream.Stream.of(
        output.publicValues(),
        output.logValues(),
        output.attachedCauses()
      )
        .flatMap(Collection::stream)
        .toList();
    }
  }
}
