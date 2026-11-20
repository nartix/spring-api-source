package com.ferozfaiz.uniapi.category.application.registration;

import java.util.List;
import java.util.Optional;

public final class AdminCategoryRegistrationAggregateValidationException extends RuntimeException {

  private final List<AdminCategoryValidationDiagnostic> diagnostics;
  private final long omittedCount;

  AdminCategoryRegistrationAggregateValidationException(
    List<AdminCategoryValidationDiagnostic> diagnostics,
    long omittedCount
  ) {
    this(validate(diagnostics, omittedCount));
  }

  private AdminCategoryRegistrationAggregateValidationException(Validated validated) {
    super(
      AdminCategoryRegistrationDiagnostics.renderAggregate(
        validated.diagnostics(),
        validated.omittedCount()
      )
    );
    this.diagnostics = validated.diagnostics();
    this.omittedCount = validated.omittedCount();
  }

  public List<AdminCategoryValidationDiagnostic> diagnostics() {
    return diagnostics;
  }

  public long omittedCount() {
    return omittedCount;
  }

  private static Validated validate(
    List<AdminCategoryValidationDiagnostic> diagnostics,
    long omittedCount
  ) {
    if (
      diagnostics == null ||
      diagnostics.isEmpty() ||
      diagnostics.size() > AdminCategoryRegistrationDiagnostics.RETAINED_LIMIT ||
      omittedCount < 0
    ) {
      throw invalidInput();
    }
    for (int index = 0; index < diagnostics.size(); index++) {
      AdminCategoryValidationDiagnostic diagnostic = diagnostics.get(index);
      if (diagnostic == null) {
        throw invalidInput();
      }
      if (
        index > 0 &&
        AdminCategoryRegistrationDiagnostics.ORDER.compare(diagnostics.get(index - 1), diagnostic) >
          0
      ) {
        throw invalidInput();
      }
    }
    return new Validated(List.copyOf(diagnostics), omittedCount);
  }

  private static AdminCategoryRegistrationValidationException invalidInput() {
    return new AdminCategoryRegistrationValidationException(
      new AdminCategoryValidationDiagnostic(
        AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
        AdminCategoryValidationCategory.INVALID_COLLECTION,
        Optional.of(AdminCategoryValidationComponent.REGISTRATION),
        Optional.empty(),
        Optional.empty()
      )
    );
  }

  private record Validated(
    List<AdminCategoryValidationDiagnostic> diagnostics,
    long omittedCount
  ) {}
}
