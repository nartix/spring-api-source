package com.ferozfaiz.uniapi.category.contract;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public record AdminCategoryOperationMatrixCase(
  String profileOrSystem,
  Operation operation,
  boolean declaredCapability,
  String authenticationState,
  String authorityState,
  String inputCondition,
  String expectedStatusOrResult,
  String expectedStableError,
  List<String> expectedPortCalls,
  List<String> forbiddenInteractions,
  String expectedLogOutcome,
  String applicabilityEvidence
) {
  public AdminCategoryOperationMatrixCase {
    profileOrSystem = requireText(profileOrSystem, "profileOrSystem");
    operation = Objects.requireNonNull(operation, "operation");
    authenticationState = requireText(authenticationState, "authenticationState");
    authorityState = requireText(authorityState, "authorityState");
    inputCondition = requireText(inputCondition, "inputCondition");
    expectedStatusOrResult = requireText(expectedStatusOrResult, "expectedStatusOrResult");
    expectedStableError = requireText(expectedStableError, "expectedStableError");
    expectedPortCalls = immutableTextList(expectedPortCalls, "expectedPortCalls");
    forbiddenInteractions = immutableTextList(forbiddenInteractions, "forbiddenInteractions");
    expectedLogOutcome = requireText(expectedLogOutcome, "expectedLogOutcome");
    applicabilityEvidence = requireText(applicabilityEvidence, "applicabilityEvidence");
    if (
      expectedPortCalls.stream().anyMatch(AdminCategoryOperationMatrixCase::isNotApplicable) ||
      forbiddenInteractions.stream().anyMatch(AdminCategoryOperationMatrixCase::isNotApplicable) ||
      isNotApplicable(expectedLogOutcome)
    ) {
      throw new IllegalArgumentException(
        "port, forbidden-interaction, and log expectations must remain executable"
      );
    }
    if (
      (isNotApplicable(expectedStatusOrResult) || isNotApplicable(expectedStableError)) &&
      isNotApplicable(applicabilityEvidence)
    ) {
      throw new IllegalArgumentException("N/A requires concrete applicability evidence");
    }
  }

  public String caseId() {
    return (
      profileOrSystem +
      ":" +
      operation.name().toLowerCase(Locale.ROOT) +
      ":" +
      authenticationState +
      ":" +
      authorityState +
      ":" +
      inputCondition
    );
  }

  public static <D> List<AdminCategoryOperationMatrixCase> generateDeterministic(
    List<D> dimensions,
    Function<D, AdminCategoryOperationMatrixCase> rowFactory
  ) {
    List<D> dimensionSnapshot = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
    Function<D, AdminCategoryOperationMatrixCase> factory = Objects.requireNonNull(
      rowFactory,
      "rowFactory"
    );
    List<AdminCategoryOperationMatrixCase> rows = new ArrayList<>(dimensionSnapshot.size());
    for (D dimension : dimensionSnapshot) {
      rows.add(Objects.requireNonNull(factory.apply(dimension), "generated matrix row"));
    }
    return immutableUniqueRows(rows);
  }

  public static <R, C> List<AdminCategoryOperationMatrixCase> generateDeterministic(
    List<R> rowDimensions,
    List<C> columnDimensions,
    BiFunction<R, C, AdminCategoryOperationMatrixCase> rowFactory
  ) {
    List<R> rowSnapshot = List.copyOf(Objects.requireNonNull(rowDimensions, "rowDimensions"));
    List<C> columnSnapshot = List.copyOf(
      Objects.requireNonNull(columnDimensions, "columnDimensions")
    );
    BiFunction<R, C, AdminCategoryOperationMatrixCase> factory = Objects.requireNonNull(
      rowFactory,
      "rowFactory"
    );
    List<AdminCategoryOperationMatrixCase> rows = new ArrayList<>(
      Math.multiplyExact(rowSnapshot.size(), columnSnapshot.size())
    );
    for (R rowDimension : rowSnapshot) {
      for (C columnDimension : columnSnapshot) {
        rows.add(
          Objects.requireNonNull(
            factory.apply(rowDimension, columnDimension),
            "generated matrix row"
          )
        );
      }
    }
    return immutableUniqueRows(rows);
  }

  private static boolean isNotApplicable(String value) {
    return "N/A".equalsIgnoreCase(value.trim());
  }

  private static List<AdminCategoryOperationMatrixCase> immutableUniqueRows(
    List<AdminCategoryOperationMatrixCase> rows
  ) {
    Set<String> caseIds = new HashSet<>();
    for (AdminCategoryOperationMatrixCase row : rows) {
      if (!caseIds.add(row.caseId())) {
        throw new IllegalArgumentException("matrix generator produced duplicate case ID");
      }
    }
    return List.copyOf(rows);
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
