package com.ferozfaiz.uniapi.category.application.registration;

import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

final class BoundedRegistrationDiagnosticAccumulator {

  private final PriorityQueue<AdminCategoryValidationDiagnostic> retained;
  private long totalOccurrences;

  BoundedRegistrationDiagnosticAccumulator() {
    retained = new PriorityQueue<>(
      AdminCategoryRegistrationDiagnostics.RETAINED_LIMIT,
      AdminCategoryRegistrationDiagnostics.ORDER.reversed()
    );
  }

  void add(AdminCategoryValidationDiagnostic diagnostic) {
    if (diagnostic == null) {
      throw invalidInput();
    }
    try {
      totalOccurrences = Math.incrementExact(totalOccurrences);
    } catch (ArithmeticException failure) {
      throw invalidInput();
    }
    if (retained.size() < AdminCategoryRegistrationDiagnostics.RETAINED_LIMIT) {
      retained.add(diagnostic);
      return;
    }
    if (AdminCategoryRegistrationDiagnostics.ORDER.compare(diagnostic, retained.element()) < 0) {
      retained.remove();
      retained.add(diagnostic);
    }
  }

  boolean isEmpty() {
    return totalOccurrences == 0;
  }

  List<AdminCategoryValidationDiagnostic> retainedInOrder() {
    return retained.stream().sorted(AdminCategoryRegistrationDiagnostics.ORDER).toList();
  }

  long omittedCount() {
    try {
      return Math.subtractExact(totalOccurrences, retained.size());
    } catch (ArithmeticException failure) {
      throw invalidInput();
    }
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
}
