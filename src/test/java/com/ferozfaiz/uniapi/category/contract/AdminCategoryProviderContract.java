package com.ferozfaiz.uniapi.category.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

public interface AdminCategoryProviderContract {
  AdminCategoryProviderContractProfile profile();

  AdminCategoryProviderContractFixture newFixture();

  @Test
  default void fixtureFactoryCreatesFreshFixtures() {
    AdminCategoryProviderContractFixture first = freshFixture();
    try (first) {
      AdminCategoryProviderContractFixture second = freshFixture();
      try (second) {
        assertNotSame(first, second, "newFixture must not reuse mutable contract state");
      }
    }
  }

  @TestFactory
  default Stream<DynamicTest> declaredOperationRegistrationSeams() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    return contractProfile
      .declaredOperations()
      .stream()
      .sorted(Comparator.comparingInt(Enum::ordinal))
      .map(operation ->
        DynamicTest.dynamicTest(operation.code(), () -> {
          try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
            AdminCategoryContractAssertions.assertRegistration(contractProfile, fixture, operation);
          }
        })
      );
  }

  @Test
  default void capabilitiesAreDeterministicAndUseNoProviderPort() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
      fixture.reset();
      fixture.seed(contractProfile.seedRecords());
      AdminCategoryProviderContractFixture.OperationObservation first = fixture.invoke(
        Operation.CAPABILITIES
      );
      AdminCategoryProviderContractFixture.InteractionLedger firstInteractions =
        fixture.interactions();

      fixture.reset();
      fixture.seed(contractProfile.seedRecords());
      AdminCategoryProviderContractFixture.OperationObservation second = fixture.invoke(
        Operation.CAPABILITIES
      );
      AdminCategoryProviderContractFixture.InteractionLedger secondInteractions =
        fixture.interactions();

      AdminCategoryContractAssertions.assertResult(
        contractProfile.operationExpectations().get(Operation.CAPABILITIES),
        first
      );
      AdminCategoryContractAssertions.assertDeterministic(first, second);
      AdminCategoryContractAssertions.assertNoInteractions(firstInteractions);
      AdminCategoryContractAssertions.assertNoInteractions(secondInteractions);
    }
  }

  @TestFactory
  default Stream<DynamicTest> unsupportedOperationApplicabilitySeams() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    return Arrays.stream(Operation.values())
      .filter(operation -> !contractProfile.declaredOperations().contains(operation))
      .map(operation ->
        DynamicTest.dynamicTest(operation.code(), () -> {
          try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
            AdminCategoryContractAssertions.assertUnsupportedSeam(
              contractProfile,
              fixture,
              operation
            );
          }
        })
      );
  }

  @TestFactory
  default Stream<DynamicTest> rollbackApplicabilitySeams() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    return contractProfile
      .rollbackApplicability()
      .entrySet()
      .stream()
      .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
      .map(entry ->
        DynamicTest.dynamicTest(entry.getKey().code(), () ->
          AdminCategoryContractAssertions.assertRollbackApplicability(
            contractProfile,
            entry.getKey(),
            entry.getValue()
          )
        )
      );
  }

  @Test
  default void operationApplicabilityCoversAllFourteenOperations() {
    AdminCategoryContractAssertions.assertCompleteOperationApplicability(contractProfile());
  }

  @TestFactory
  default Stream<DynamicTest> declaredOperationsUseOneCanonicalMappedCall() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    return contractProfile
      .declaredOperations()
      .stream()
      .sorted(Comparator.comparingInt(Enum::ordinal))
      .map(operation ->
        DynamicTest.dynamicTest(operation.code(), () -> {
          try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
            fixture.reset();
            fixture.seed(contractProfile.seedRecords());
            AdminCategoryContractAssertions.assertCanonicalInvocation(
              contractProfile,
              fixture,
              operation
            );
          }
        })
      );
  }

  @Test
  default void listIsDeterministicDuplicateFreeAndExactlyOrdered() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    assertTrue(contractProfile.declaredOperations().contains(Operation.LIST));
    try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
      fixture.reset();
      fixture.seed(contractProfile.seedRecords());
      AdminCategoryProviderContractFixture.OperationObservation first =
        AdminCategoryContractAssertions.assertCanonicalInvocation(
          contractProfile,
          fixture,
          Operation.LIST
        );
      AdminCategoryContractAssertions.assertExpectedListOrder(contractProfile, first);

      fixture.reset();
      fixture.seed(contractProfile.seedRecords());
      AdminCategoryProviderContractFixture.OperationObservation second =
        AdminCategoryContractAssertions.assertCanonicalInvocation(
          contractProfile,
          fixture,
          Operation.LIST
        );
      AdminCategoryContractAssertions.assertExpectedListOrder(contractProfile, second);
      AdminCategoryContractAssertions.assertDeterministic(first, second);
    }
  }

  @Test
  default void listAndGetAgreeOrGetHasExecutableUnsupportedEvidence() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
      fixture.reset();
      fixture.seed(contractProfile.seedRecords());
      AdminCategoryProviderContractFixture.OperationObservation listed =
        AdminCategoryContractAssertions.assertCanonicalInvocation(
          contractProfile,
          fixture,
          Operation.LIST
        );

      if (!contractProfile.declaredOperations().contains(Operation.GET)) {
        AdminCategoryContractAssertions.assertUnsupportedSeam(
          contractProfile,
          fixture,
          Operation.GET
        );
        return;
      }

      AdminCategoryProviderContractFixture.OperationObservation selected =
        AdminCategoryContractAssertions.assertCanonicalInvocation(
          contractProfile,
          fixture,
          Operation.GET
        );
      AdminCategoryContractAssertions.assertListGetAgreement(listed, selected);

      fixture.reset();
      AdminCategoryProviderContractFixture.OperationObservation missing = fixture.invoke(
        Operation.GET
      );
      AdminCategoryContractAssertions.assertStableMissingGet(
        contractProfile.notFoundExpectation(),
        missing
      );
      AdminCategoryContractAssertions.assertInteractions(
        AdminCategoryProviderContractProfile.canonicalCall(Operation.GET).stream().toList(),
        contractProfile.interactionExpectation().forbiddenInteractions(),
        fixture.interactions()
      );
      AdminCategoryContractAssertions.assertExactProbeCorpusSafe(missing.output());
    }
  }

  @TestFactory
  default Stream<DynamicTest> rollbackHooksExecuteOrHaveExecutableNotApplicableEvidence() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    return contractProfile
      .rollbackApplicability()
      .entrySet()
      .stream()
      .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
      .map(entry ->
        DynamicTest.dynamicTest(entry.getKey().code(), () -> {
          Operation operation = entry.getKey();
          AdminCategoryProviderContractProfile.Applicability applicability = entry.getValue();
          try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
            fixture.reset();
            fixture.seed(contractProfile.seedRecords());
            if (!applicability.applicable()) {
              AdminCategoryProviderContractFixture.OperationObservation observation =
                AdminCategoryContractAssertions.assertCanonicalInvocation(
                  contractProfile,
                  fixture,
                  operation
                );
              assertEquals(0, fixture.transactionObservation().rollbacks());
              AdminCategoryContractAssertions.assertExactProbeCorpusSafe(observation.output());
              return;
            }

            AdminCategoryProviderContractFixture.StateSnapshot before = fixture.snapshot();
            AdminCategoryProviderContractFixture.StateSnapshot providerBefore =
              fixture.providerState();
            AdminCategoryProviderContractFixture.OperationObservation observation =
              fixture.invokeThenFailAfterWrite(operation);
            AdminCategoryContractAssertions.assertRollback(
              before,
              fixture.snapshot(),
              providerBefore,
              fixture.providerState(),
              fixture.transactionObservation()
            );
            AdminCategoryContractAssertions.assertInteractions(
              AdminCategoryProviderContractProfile.canonicalCall(operation).stream().toList(),
              contractProfile.interactionExpectation().forbiddenInteractions(),
              fixture.interactions()
            );
            assertTrue(observation.stableError().isPresent(), applicability.evidence());
            AdminCategoryContractAssertions.assertExactProbeCorpusSafe(observation.output());
          }
        })
      );
  }

  @TestFactory
  default Stream<DynamicTest> everyOperationKeepsPublicLogAndCauseOutputProbeSafe() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    return Arrays.stream(Operation.values()).map(operation ->
      DynamicTest.dynamicTest(operation.code(), () -> {
        try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
          fixture.reset();
          fixture.seed(contractProfile.seedRecords());
          AdminCategoryProviderContractFixture.OperationObservation observation = fixture.invoke(
            operation
          );
          AdminCategoryContractAssertions.assertResult(
            contractProfile.expectation(operation),
            observation
          );
          List<String> expectedCalls = contractProfile.declaredOperations().contains(operation)
            ? AdminCategoryProviderContractProfile.canonicalCall(operation).stream().toList()
            : List.of();
          AdminCategoryContractAssertions.assertInteractions(
            expectedCalls,
            contractProfile.interactionExpectation().forbiddenInteractions(),
            fixture.interactions()
          );
          AdminCategoryContractAssertions.assertExactProbeCorpusSafe(observation.output());
        }
      })
    );
  }

  @TestFactory
  default Stream<DynamicTest> queryObservationsHonorApplicabilityAndStatementBounds() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    if (contractProfile.queryCountExpectations().isEmpty()) {
      return Stream.of(
        DynamicTest.dynamicTest("not-applicable", () -> {
          try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
            fixture.reset();
            fixture.seed(contractProfile.seedRecords());
            AdminCategoryContractAssertions.assertCanonicalInvocation(
              contractProfile,
              fixture,
              Operation.LIST
            );
            assertTrue(
              fixture.queryObservation().isEmpty(),
              "profile declares no query-count observation"
            );
          }
        })
      );
    }
    return contractProfile
      .queryCountExpectations()
      .stream()
      .map(expectation ->
        DynamicTest.dynamicTest(expectation.observationId(), () -> {
          AdminCategoryContractAssertions.assertQueryExpectationMetadata(expectation);
          try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
            fixture.reset();
            fixture.seedCardinality(expectation.smallCardinality(), contractProfile.seedRecords());
            AdminCategoryContractAssertions.assertCanonicalInvocation(
              contractProfile,
              fixture,
              Operation.LIST
            );
            java.util.Optional<
              AdminCategoryProviderContractFixture.QueryObservation
            > smallCardinality = fixture.queryObservation();
            AdminCategoryContractAssertions.assertQuerySafety(expectation, smallCardinality);

            fixture.reset();
            fixture.seedCardinality(expectation.largeCardinality(), contractProfile.seedRecords());
            AdminCategoryContractAssertions.assertCanonicalInvocation(
              contractProfile,
              fixture,
              Operation.LIST
            );
            java.util.Optional<
              AdminCategoryProviderContractFixture.QueryObservation
            > largeCardinality = fixture.queryObservation();
            AdminCategoryContractAssertions.assertQuerySafety(expectation, largeCardinality);
            if (expectation.applicability().applicable()) {
              AdminCategoryContractAssertions.assertEqualQueryCounts(
                smallCardinality.orElseThrow(),
                largeCardinality.orElseThrow()
              );
            }
          }
        })
      );
  }

  @Test
  default void resetAndFreshFixturesIsolateStateObservationsAndOutputs() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    try (
      AdminCategoryProviderContractFixture first = freshFixture();
      AdminCategoryProviderContractFixture second = freshFixture()
    ) {
      first.reset();
      first.seed(contractProfile.seedRecords());
      second.reset();
      second.seed(contractProfile.seedRecords());
      AdminCategoryProviderContractFixture.StateSnapshot expectedPublic = second.snapshot();
      AdminCategoryProviderContractFixture.StateSnapshot expectedProvider = second.providerState();
      assertEquals(expectedPublic, first.snapshot());
      assertEquals(expectedProvider, first.providerState());

      Operation isolationOperation = contractProfile
        .rollbackApplicability()
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue().applicable())
        .map(java.util.Map.Entry::getKey)
        .findFirst()
        .orElse(Operation.LIST);
      AdminCategoryContractAssertions.assertCanonicalInvocation(
        contractProfile,
        first,
        isolationOperation
      );
      assertEquals(expectedPublic, second.snapshot(), "one fixture leaked into another");
      assertEquals(expectedProvider, second.providerState(), "provider state crossed fixtures");

      first.reset();
      first.seed(contractProfile.seedRecords());
      assertEquals(expectedPublic, first.snapshot(), "reset did not restore public state");
      assertEquals(expectedProvider, first.providerState(), "reset did not restore provider state");
      AdminCategoryContractAssertions.assertNoInteractions(first.interactions());
      assertTrue(first.queryObservation().isEmpty(), "reset retained query observations");
      assertEquals(
        new AdminCategoryProviderContractFixture.OutputObservation(List.of(), List.of(), List.of()),
        first.outputObservation(),
        "reset retained public, log, or cause output"
      );
    }
  }

  @Test
  default void registryAndProfileSnapshotsAreImmutableAndDeterministic() {
    AdminCategoryProviderContractProfile contractProfile = contractProfile();
    try (AdminCategoryProviderContractFixture fixture = freshFixture()) {
      List<?> first = fixture.registry().registrations();
      List<?> second = fixture.registry().registrations();
      assertEquals(first, second, "registry enumeration changed between reads");
      assertTrue(first.contains(fixture.registration()));
      assertSame(
        fixture.registration(),
        fixture.registry().findRegistration(contractProfile.definition().systemKey()).orElseThrow()
      );
      assertEquals(
        first.size(),
        fixture
          .registry()
          .registrations()
          .stream()
          .map(registration -> registration.definition().orElseThrow().systemKey())
          .distinct()
          .count()
      );
      assertThrows(UnsupportedOperationException.class, first::clear);
      assertThrows(
        UnsupportedOperationException.class,
        contractProfile.declaredOperations()::clear
      );
      assertThrows(UnsupportedOperationException.class, contractProfile.seedRecords()::clear);
      assertThrows(
        UnsupportedOperationException.class,
        contractProfile.operationExpectations()::clear
      );
      assertThrows(
        UnsupportedOperationException.class,
        contractProfile.definition().operations().supportedOperations()::clear
      );
      assertFalse(contractProfile.profileId().isBlank());
    }
  }

  private AdminCategoryProviderContractProfile contractProfile() {
    return Objects.requireNonNull(profile(), "profile");
  }

  private AdminCategoryProviderContractFixture freshFixture() {
    return Objects.requireNonNull(newFixture(), "newFixture");
  }
}
