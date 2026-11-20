package com.ferozfaiz.uniapi.category.application.registration;

import java.util.Optional;
import java.util.regex.Pattern;

final class StandardNumericV1AdminCategoryIdentifierCodec
  implements AdminCategoryIdentifierCodec<Long>
{

  private static final Pattern CANONICAL_IDENTIFIER = Pattern.compile("[1-9][0-9]*");

  StandardNumericV1AdminCategoryIdentifierCodec() {}

  @Override
  public AdminCategoryIdentifierProfile identifierProfile() {
    return AdminCategoryIdentifierProfile.NUMERIC_LONG_V1;
  }

  @Override
  public Long parse(String value) {
    if (value == null || !CANONICAL_IDENTIFIER.matcher(value).matches()) {
      throw invalidIdentifier();
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException failure) {
      throw invalidIdentifier();
    }
  }

  @Override
  public String serialize(Long value) {
    if (value == null || value <= 0) {
      throw invalidIdentifier();
    }
    return Long.toString(value);
  }

  private static AdminCategoryRegistrationValidationException invalidIdentifier() {
    return AdminCategoryRegistrationDiagnostics.single(
      AdminCategoryRegistrationDiagnostics.diagnostic(
        AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
        AdminCategoryValidationCategory.INVALID_IDENTIFIER,
        Optional.of(AdminCategoryValidationComponent.IDENTIFIER_CODEC),
        Optional.empty(),
        Optional.empty()
      )
    );
  }
}
