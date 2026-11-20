package com.ferozfaiz.uniapi.category.application.registration;

import java.util.Optional;

public final class AdminCategoryRegistrationValidationException extends RuntimeException {

  private final AdminCategoryValidationDiagnostic diagnostic;

  AdminCategoryRegistrationValidationException(AdminCategoryValidationDiagnostic diagnostic) {
    super(messageFor(diagnostic));
    this.diagnostic = diagnostic;
  }

  public AdminCategoryValidationDiagnostic diagnostic() {
    return diagnostic;
  }

  private static String messageFor(AdminCategoryValidationDiagnostic diagnostic) {
    if (diagnostic == null) {
      throw invalidInput();
    }
    return AdminCategoryRegistrationDiagnostics.renderSingle(diagnostic);
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
