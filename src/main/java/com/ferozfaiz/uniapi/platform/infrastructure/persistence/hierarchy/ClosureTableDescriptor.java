package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.Objects;
import java.util.regex.Pattern;

public record ClosureTableDescriptor(
  String nodeTable,
  String closureTable,
  String nodeIdColumn,
  String parentIdColumn,
  String ancestorColumn,
  String descendantColumn,
  String depthColumn
) {
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile(
    "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?"
  );

  public ClosureTableDescriptor {
    nodeTable = requireQualifiedIdentifier(nodeTable, "nodeTable");
    closureTable = requireQualifiedIdentifier(closureTable, "closureTable");
    nodeIdColumn = requireIdentifier(nodeIdColumn, "nodeIdColumn");
    parentIdColumn = requireIdentifier(parentIdColumn, "parentIdColumn");
    ancestorColumn = requireIdentifier(ancestorColumn, "ancestorColumn");
    descendantColumn = requireIdentifier(descendantColumn, "descendantColumn");
    depthColumn = requireIdentifier(depthColumn, "depthColumn");
  }

  public static ClosureTableDescriptor of(String nodeTable, String closureTable) {
    return new ClosureTableDescriptor(
      nodeTable,
      closureTable,
      "id",
      "parent_id",
      "ancestor_id",
      "descendant_id",
      "depth"
    );
  }

  private static String requireIdentifier(String value, String fieldName) {
    String identifier = Objects.requireNonNull(value, fieldName).trim();
    if (!IDENTIFIER.matcher(identifier).matches()) {
      throw new IllegalArgumentException(fieldName + " must be an unquoted SQL identifier");
    }
    return identifier;
  }

  private static String requireQualifiedIdentifier(String value, String fieldName) {
    String identifier = Objects.requireNonNull(value, fieldName).trim();
    if (!QUALIFIED_IDENTIFIER.matcher(identifier).matches()) {
      throw new IllegalArgumentException(
        fieldName + " must be an unquoted SQL identifier or schema-qualified identifier"
      );
    }
    return identifier;
  }
}
