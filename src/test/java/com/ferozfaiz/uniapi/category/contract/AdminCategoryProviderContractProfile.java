package com.ferozfaiz.uniapi.category.contract;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public record AdminCategoryProviderContractProfile(
  String profileId,
  AdminCategorySystemDefinition definition,
  Supplier<AdminCategorySystemRegistration> registrationFactory,
  Set<Operation> declaredOperations,
  Map<Operation, PortSlot> expectedOperationPorts,
  List<SeedRecord> seedRecords,
  List<String> expectedDeterministicListOrder,
  Map<Operation, OperationExpectation> operationExpectations,
  StableErrorExpectation notFoundExpectation,
  Map<Operation, Applicability> rollbackApplicability,
  InteractionExpectation interactionExpectation,
  List<QueryCountExpectation> queryCountExpectations
) {
  public AdminCategoryProviderContractProfile {
    profileId = requireText(profileId, "profileId");
    definition = Objects.requireNonNull(definition, "definition");
    registrationFactory = Objects.requireNonNull(registrationFactory, "registrationFactory");
    declaredOperations = Set.copyOf(
      Objects.requireNonNull(declaredOperations, "declaredOperations")
    );
    Set<Operation> classifiedOperations = declaredOperations;
    expectedOperationPorts = Map.copyOf(
      Objects.requireNonNull(expectedOperationPorts, "expectedOperationPorts")
    );
    seedRecords = List.copyOf(Objects.requireNonNull(seedRecords, "seedRecords"));
    expectedDeterministicListOrder = immutableTextList(
      expectedDeterministicListOrder,
      "expectedDeterministicListOrder"
    );
    operationExpectations = Map.copyOf(
      Objects.requireNonNull(operationExpectations, "operationExpectations")
    );
    notFoundExpectation = Objects.requireNonNull(notFoundExpectation, "notFoundExpectation");
    rollbackApplicability = Map.copyOf(
      Objects.requireNonNull(rollbackApplicability, "rollbackApplicability")
    );
    interactionExpectation = Objects.requireNonNull(
      interactionExpectation,
      "interactionExpectation"
    );
    queryCountExpectations = List.copyOf(
      Objects.requireNonNull(queryCountExpectations, "queryCountExpectations")
    );
    if (!declaredOperations.contains(Operation.CAPABILITIES)) {
      throw new IllegalArgumentException("every profile must declare capabilities");
    }
    if (!definition.operations().supportedOperations().equals(declaredOperations)) {
      throw new IllegalArgumentException(
        "declaredOperations must equal the immutable definition operations"
      );
    }
    if (!expectedOperationPorts.keySet().equals(declaredOperations)) {
      throw new IllegalArgumentException(
        "expectedOperationPorts must cover declared operations exactly"
      );
    }
    expectedOperationPorts.forEach((operation, port) -> {
      PortSlot canonicalPort = portFor(operation);
      if (port != canonicalPort) {
        throw new IllegalArgumentException(
          operation + " must map to " + canonicalPort + " instead of " + port
        );
      }
    });
    if (!operationExpectations.keySet().equals(Set.of(Operation.values()))) {
      throw new IllegalArgumentException("operationExpectations must cover all operations exactly");
    }
    operationExpectations.forEach((operation, expectation) -> {
      if (!expectation.applicability().applicable()) {
        throw new IllegalArgumentException(
          "every operation must be classified as declared success or unsupported rejection"
        );
      }
      if (classifiedOperations.contains(operation)) {
        if (
          !AdminCategoryContractAssertions.NO_STABLE_ERROR.equals(expectation.expectedStableError())
        ) {
          throw new IllegalArgumentException(
            "declared operations must have a successful stable-error expectation"
          );
        }
      } else if (
        !"409 CATEGORY_CAPABILITY_UNSUPPORTED".equals(expectation.expectedResultId()) ||
        !"CATEGORY_CAPABILITY_UNSUPPORTED".equals(expectation.expectedStableError()) ||
        !"capability.unsupported".equals(expectation.expectedLogOutcome())
      ) {
        throw new IllegalArgumentException(
          "undeclared operations must use the exact stable unsupported result, error, and log"
        );
      }
    });
    if (!rollbackApplicability.keySet().equals(declaredOperations)) {
      throw new IllegalArgumentException(
        "rollback applicability must cover declared operations exactly"
      );
    }
    Set<String> expectedAllowedInteractions = declaredOperations
      .stream()
      .map(AdminCategoryProviderContractProfile::canonicalCall)
      .flatMap(java.util.Optional::stream)
      .collect(Collectors.toUnmodifiableSet());
    if (!interactionExpectation.allowedInteractions().equals(expectedAllowedInteractions)) {
      throw new IllegalArgumentException(
        "allowed interactions must equal the canonical calls for declared operations"
      );
    }
    Set<String> expectedForbiddenCanonicalCalls = Arrays.stream(Operation.values())
      .filter(operation -> !classifiedOperations.contains(operation))
      .map(AdminCategoryProviderContractProfile::canonicalCall)
      .flatMap(java.util.Optional::stream)
      .collect(Collectors.toUnmodifiableSet());
    if (
      !interactionExpectation
        .forbiddenInteractions()
        .containsAll(expectedForbiddenCanonicalCalls) ||
      !interactionExpectation.forbiddenInteractions().contains("provider.fallback")
    ) {
      throw new IllegalArgumentException(
        "forbidden interactions must cover undeclared canonical calls and provider fallback"
      );
    }
    long distinctQueryObservations = queryCountExpectations
      .stream()
      .map(QueryCountExpectation::observationId)
      .distinct()
      .count();
    if (distinctQueryObservations != queryCountExpectations.size()) {
      throw new IllegalArgumentException("query-count observation IDs must be unique");
    }
  }

  public AdminCategorySystemRegistration newRegistration() {
    return Objects.requireNonNull(registrationFactory.get(), "registrationFactory result");
  }

  public OperationExpectation expectation(Operation operation) {
    return Objects.requireNonNull(
      operationExpectations.get(Objects.requireNonNull(operation, "operation")),
      "operation expectation"
    );
  }

  public Applicability rollbackFor(Operation operation) {
    if (!declaredOperations.contains(Objects.requireNonNull(operation, "operation"))) {
      return new Applicability(
        false,
        "rollback is not applicable because the operation is undeclared"
      );
    }
    return Objects.requireNonNull(rollbackApplicability.get(operation), "rollback applicability");
  }

  public static java.util.Optional<String> canonicalCall(Operation operation) {
    return java.util.Optional.ofNullable(
      switch (Objects.requireNonNull(operation, "operation")) {
        case CAPABILITIES -> null;
        case LIST -> "category-read.list";
        case TREE -> "hierarchy-read.tree";
        case PARENT_OPTIONS -> "hierarchy-read.parentOptions";
        case GET -> "category-read.getNode";
        case BREADCRUMB -> "hierarchy-read.breadcrumb";
        case CREATE -> "basic-mutation.create";
        case REPLACE -> "basic-mutation.replace";
        case PATCH -> "basic-mutation.patch";
        case DELETE -> "basic-mutation.delete";
        case MOVE -> "hierarchy-mutation.move";
        case REORDER -> "ordering.reorder";
        case ACTIVATE -> "lifecycle.activate";
        case DEACTIVATE -> "lifecycle.deactivate";
      }
    );
  }

  public enum PortSlot {
    NONE,
    CATEGORY_READ,
    HIERARCHY_READ,
    BASIC_MUTATION,
    HIERARCHY_MUTATION,
    ORDERING,
    LIFECYCLE,
  }

  private static PortSlot portFor(Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> PortSlot.NONE;
      case LIST, GET -> PortSlot.CATEGORY_READ;
      case TREE, PARENT_OPTIONS, BREADCRUMB -> PortSlot.HIERARCHY_READ;
      case CREATE, REPLACE, PATCH, DELETE -> PortSlot.BASIC_MUTATION;
      case MOVE -> PortSlot.HIERARCHY_MUTATION;
      case REORDER -> PortSlot.ORDERING;
      case ACTIVATE, DEACTIVATE -> PortSlot.LIFECYCLE;
    };
  }

  public record SeedRecord(String recordId, Map<String, String> attributes) {
    public SeedRecord {
      recordId = requireText(recordId, "recordId");
      attributes = immutableTextMap(attributes, "attributes");
    }
  }

  public record OperationExpectation(
    String invocationId,
    String expectedResultId,
    String expectedStableError,
    String expectedLogOutcome,
    Applicability applicability
  ) {
    public OperationExpectation {
      invocationId = requireText(invocationId, "invocationId");
      expectedResultId = requireText(expectedResultId, "expectedResultId");
      expectedStableError = requireText(expectedStableError, "expectedStableError");
      expectedLogOutcome = requireText(expectedLogOutcome, "expectedLogOutcome");
      applicability = Objects.requireNonNull(applicability, "applicability");
    }
  }

  public record StableErrorExpectation(String code, String message, String applicabilityEvidence) {
    public StableErrorExpectation {
      code = requireText(code, "code");
      message = requireText(message, "message");
      applicabilityEvidence = requireText(applicabilityEvidence, "applicabilityEvidence");
    }
  }

  public record Applicability(boolean applicable, String evidence) {
    public Applicability {
      evidence = requireText(evidence, "evidence");
    }
  }

  public record InteractionExpectation(
    Set<String> allowedInteractions,
    Set<String> forbiddenInteractions
  ) {
    public InteractionExpectation {
      allowedInteractions = immutableTextSet(allowedInteractions, "allowedInteractions");
      forbiddenInteractions = immutableTextSet(forbiddenInteractions, "forbiddenInteractions");
      if (!java.util.Collections.disjoint(allowedInteractions, forbiddenInteractions)) {
        throw new IllegalArgumentException("allowed and forbidden interactions must be disjoint");
      }
    }
  }

  public record QueryCountExpectation(
    String observationId,
    int smallCardinality,
    int largeCardinality,
    int maximumStatements,
    Applicability applicability
  ) {
    public QueryCountExpectation {
      observationId = requireText(observationId, "observationId");
      if (smallCardinality < 0 || largeCardinality < smallCardinality || maximumStatements < 0) {
        throw new IllegalArgumentException("query-count bounds must be ordered and non-negative");
      }
      applicability = Objects.requireNonNull(applicability, "applicability");
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

  private static Set<String> immutableTextSet(Set<String> values, String name) {
    Objects.requireNonNull(values, name);
    return values
      .stream()
      .map(value -> requireText(value, name + " entry"))
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
