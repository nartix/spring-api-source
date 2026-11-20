package com.ferozfaiz.uniapi.category.application.registration;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class AdminCategoryRegistrationDiagnostics {

  static final String UNAVAILABLE_SYSTEM_KEY = "<unavailable>";
  static final int RETAINED_LIMIT = 100;
  static final Comparator<AdminCategoryValidationDiagnostic> ORDER = Comparator.comparing(
    AdminCategoryValidationDiagnostic::safeSystemKey
  )
    .thenComparing(diagnostic -> diagnostic.category().code())
    .thenComparing(
      AdminCategoryValidationDiagnostic::component,
      optionalComparator(Comparator.comparing(AdminCategoryValidationComponent::code))
    )
    .thenComparing(
      AdminCategoryValidationDiagnostic::capability,
      optionalComparator(Comparator.comparing(AdminCategoryValidationCapability::code))
    )
    .thenComparing(
      AdminCategoryValidationDiagnostic::policyObligation,
      optionalComparator(Comparator.comparing(AdminCategoryPolicyObligation::code))
    );

  private AdminCategoryRegistrationDiagnostics() {}

  static AdminCategoryValidationDiagnostic diagnostic(
    String safeSystemKey,
    AdminCategoryValidationCategory category,
    Optional<AdminCategoryValidationComponent> component,
    Optional<AdminCategoryValidationCapability> capability,
    Optional<AdminCategoryPolicyReference> policyReference
  ) {
    return new AdminCategoryValidationDiagnostic(
      safeSystemKey,
      category,
      component,
      capability,
      policyReference
    );
  }

  static AdminCategoryRegistrationValidationException single(
    AdminCategoryValidationDiagnostic diagnostic
  ) {
    return new AdminCategoryRegistrationValidationException(diagnostic);
  }

  static String renderSingle(AdminCategoryValidationDiagnostic diagnostic) {
    return "Admin category registration validation failed: " + renderDiagnostic(diagnostic);
  }

  static String renderAggregate(
    List<AdminCategoryValidationDiagnostic> diagnostics,
    long omittedCount
  ) {
    StringBuilder message = new StringBuilder(
      "Admin category registration validation failed with "
    );
    message.append(diagnostics.size());
    message.append(" retained diagnostic(s)");
    if (omittedCount > 0) {
      message.append(" and ").append(omittedCount).append(" omitted diagnostic(s)");
    }
    message.append(": ");
    for (int index = 0; index < diagnostics.size(); index++) {
      if (index > 0) {
        message.append("; ");
      }
      message.append(renderDiagnostic(diagnostics.get(index)));
    }
    return message.toString();
  }

  private static String renderDiagnostic(AdminCategoryValidationDiagnostic diagnostic) {
    StringBuilder rendered = new StringBuilder();
    rendered.append("systemKey=").append(diagnostic.safeSystemKey());
    rendered.append(",category=").append(diagnostic.category().code());
    diagnostic
      .component()
      .ifPresent(component -> rendered.append(",component=").append(component.code()));
    diagnostic
      .capability()
      .ifPresent(capability -> rendered.append(",capability=").append(capability.code()));
    diagnostic
      .policyObligation()
      .ifPresent(obligation -> rendered.append(",policyObligation=").append(obligation.code()));
    return rendered.toString();
  }

  private static <T> Comparator<Optional<T>> optionalComparator(Comparator<T> valueOrder) {
    return (left, right) -> {
      if (left.isEmpty()) {
        return right.isEmpty() ? 0 : -1;
      }
      if (right.isEmpty()) {
        return 1;
      }
      return valueOrder.compare(left.orElseThrow(), right.orElseThrow());
    };
  }
}
