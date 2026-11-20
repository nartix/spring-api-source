package com.ferozfaiz.uniapi.category.application.registration.internal;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationCategory;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationComponent;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationDiagnostic;
import java.util.Optional;

/** Category-internal construction boundary for safely redacted registration failures. */
public final class AdminCategoryRegistrationFailures {

  private AdminCategoryRegistrationFailures() {}

  public static AdminCategoryRegistrationValidationException failure(
    String safeSystemKey,
    AdminCategoryValidationCategory category,
    AdminCategoryValidationComponent component
  ) {
    AdminCategoryValidationDiagnostic diagnostic = new AdminCategoryValidationDiagnostic(
      safeSystemKey,
      category,
      Optional.of(component),
      Optional.empty(),
      Optional.empty()
    );
    return diagnostic.asException();
  }
}
