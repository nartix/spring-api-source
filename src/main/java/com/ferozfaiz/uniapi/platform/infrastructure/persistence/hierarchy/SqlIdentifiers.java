package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.Objects;
import java.util.regex.Pattern;

final class SqlIdentifiers {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile(
    "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?"
  );

  private SqlIdentifiers() {}

  static String requireIdentifier(String value, String fieldName) {
    String identifier = Objects.requireNonNull(value, fieldName).trim();
    if (!IDENTIFIER.matcher(identifier).matches()) {
      throw new IllegalArgumentException(fieldName + " must be an unquoted SQL identifier");
    }
    return identifier;
  }

  static String requireQualifiedIdentifier(String value, String fieldName) {
    String identifier = Objects.requireNonNull(value, fieldName).trim();
    if (!QUALIFIED_IDENTIFIER.matcher(identifier).matches()) {
      throw new IllegalArgumentException(
        fieldName + " must be an unquoted SQL identifier or schema-qualified identifier"
      );
    }
    return identifier;
  }

  static String requireText(String value, String fieldName) {
    String text = Objects.requireNonNull(value, fieldName).trim();
    if (text.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return text;
  }
}
