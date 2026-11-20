package com.ferozfaiz.uniapi.category.contract;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public interface AdminCategoryProviderContractFixture extends AutoCloseable {
  AdminCategorySystemRegistry registry();

  AdminCategoryApplicationService applicationService();

  AdminCategorySystemRegistration registration();

  AdminCategoryRuntimePorts ports();

  AuthorizationFixture authorization();

  void reset();

  void seed(List<AdminCategoryProviderContractProfile.SeedRecord> records);

  default void seedCardinality(
    int cardinality,
    List<AdminCategoryProviderContractProfile.SeedRecord> prototypes
  ) {
    if (cardinality < 0) {
      throw new IllegalArgumentException("cardinality must be non-negative");
    }
    List<AdminCategoryProviderContractProfile.SeedRecord> seeds = List.copyOf(
      Objects.requireNonNull(prototypes, "prototypes")
    );
    if (cardinality == 0) {
      seed(List.of());
      return;
    }
    if (seeds.isEmpty()) {
      throw new IllegalArgumentException("positive cardinality requires a seed prototype");
    }
    java.util.ArrayList<AdminCategoryProviderContractProfile.SeedRecord> expanded =
      new java.util.ArrayList<>(seeds.subList(0, Math.min(cardinality, seeds.size())));
    long nextIdentifier;
    try {
      nextIdentifier =
        seeds
          .stream()
          .mapToLong(seed -> Long.parseLong(seed.recordId()))
          .max()
          .orElse(0L) + 1L;
    } catch (NumberFormatException failure) {
      throw new IllegalStateException(
        "non-numeric profiles must override seedCardinality",
        failure
      );
    }
    AdminCategoryProviderContractProfile.SeedRecord prototype = seeds.get(0);
    while (expanded.size() < cardinality) {
      String identifier = Long.toString(nextIdentifier++);
      LinkedHashMap<String, String> attributes = new LinkedHashMap<>(prototype.attributes());
      attributes.computeIfPresent("name", (ignored, value) -> value + " " + identifier);
      attributes.computeIfPresent("slug", (ignored, value) -> value + "-" + identifier);
      expanded.add(new AdminCategoryProviderContractProfile.SeedRecord(identifier, attributes));
    }
    seed(List.copyOf(expanded));
  }

  OperationObservation invoke(Operation operation);

  OperationObservation invokeThenFailAfterWrite(Operation operation);

  StateSnapshot snapshot();

  InteractionLedger interactions();

  TransactionObservation transactionObservation();

  OutputObservation outputObservation();

  Optional<QueryObservation> queryObservation();

  StateSnapshot providerState();

  @Override
  default void close() {}

  interface AuthorizationFixture {
    void unauthenticated();

    void authenticated(Set<String> authorities);
  }

  record OperationObservation(
    String resultId,
    Optional<String> stableError,
    StateSnapshot publicResult,
    OutputObservation output
  ) {
    public OperationObservation {
      resultId = requireText(resultId, "resultId");
      stableError = Objects.requireNonNull(stableError, "stableError").map(value ->
        requireText(value, "stableError value")
      );
      publicResult = Objects.requireNonNull(publicResult, "publicResult");
      output = Objects.requireNonNull(output, "output");
    }
  }

  record StateSnapshot(Map<String, String> facts) {
    public StateSnapshot {
      facts = immutableTextMap(facts, "facts");
    }
  }

  record InteractionLedger(List<String> calls, List<String> forbiddenInteractions) {
    public InteractionLedger {
      calls = immutableTextList(calls, "calls");
      forbiddenInteractions = immutableTextList(forbiddenInteractions, "forbiddenInteractions");
    }
  }

  record TransactionObservation(int commits, int rollbacks, boolean activeDuringInvocation) {
    public TransactionObservation {
      if (commits < 0 || rollbacks < 0) {
        throw new IllegalArgumentException("transaction counts must be non-negative");
      }
    }
  }

  record OutputObservation(
    List<String> publicValues,
    List<String> logValues,
    List<String> attachedCauses
  ) {
    public OutputObservation {
      publicValues = immutableTextList(publicValues, "publicValues");
      logValues = immutableTextList(logValues, "logValues");
      attachedCauses = immutableTextList(attachedCauses, "attachedCauses");
    }
  }

  record QueryObservation(
    int statementCount,
    List<String> statementKinds,
    List<String> normalizedStatementFingerprints
  ) {
    public QueryObservation {
      if (statementCount < 0) {
        throw new IllegalArgumentException("statementCount must be non-negative");
      }
      statementKinds = immutableTextList(statementKinds, "statementKinds");
      normalizedStatementFingerprints = immutableTextList(
        normalizedStatementFingerprints,
        "normalizedStatementFingerprints"
      );
      if (
        statementKinds.size() != statementCount ||
        normalizedStatementFingerprints.size() != statementCount
      ) {
        throw new IllegalArgumentException(
          "statement count, kinds, and normalized fingerprints must have equal cardinality"
        );
      }
    }
  }

  private static Map<String, String> immutableTextMap(Map<String, String> values, String name) {
    Objects.requireNonNull(values, name);
    java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
    values.forEach((key, value) ->
      result.put(requireText(key, name + " key"), requireText(value, name + " value"))
    );
    return Map.copyOf(result);
  }

  private static List<String> immutableTextList(List<String> values, String name) {
    Objects.requireNonNull(values, name);
    return values
      .stream()
      .map(value -> requireText(value, name + " entry"))
      .toList();
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
