package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record AdminCategoryNodeTableDescriptor(
  String systemKey,
  String nodeTable,
  String idColumn,
  String parentIdColumn,
  String nameColumn,
  String slugColumn,
  String activeColumn,
  String createdAtColumn,
  String updatedAtColumn,
  Optional<String> sortRankColumn,
  AdminCategoryJdbcProviderProperties.SlugUniqueness slugUniqueness,
  AdminCategoryJdbcProviderProperties.IdGeneration idGeneration,
  AdminCategoryJdbcProviderProperties.TimestampStrategy timestampStrategy,
  String updatedAtExpression
) {
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile(
    "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?"
  );
  private static final String CURRENT_TIMESTAMP = "CURRENT_TIMESTAMP";

  public AdminCategoryNodeTableDescriptor {
    systemKey = requireText(systemKey, "systemKey");
    nodeTable = requireQualifiedIdentifier(nodeTable, "nodeTable");
    idColumn = requireIdentifier(idColumn, "idColumn");
    parentIdColumn = requireIdentifier(parentIdColumn, "parentIdColumn");
    nameColumn = requireIdentifier(nameColumn, "nameColumn");
    slugColumn = requireIdentifier(slugColumn, "slugColumn");
    activeColumn = requireIdentifier(activeColumn, "activeColumn");
    createdAtColumn = requireIdentifier(createdAtColumn, "createdAtColumn");
    updatedAtColumn = requireIdentifier(updatedAtColumn, "updatedAtColumn");
    sortRankColumn = Objects.requireNonNull(sortRankColumn, "sortRankColumn").map(column ->
      requireIdentifier(column, "sortRankColumn")
    );
    slugUniqueness = requireSupported(
      slugUniqueness,
      AdminCategoryJdbcProviderProperties.SlugUniqueness.GLOBAL,
      "slugUniqueness"
    );
    idGeneration = requireSupported(
      idGeneration,
      AdminCategoryJdbcProviderProperties.IdGeneration.DATABASE_GENERATED,
      "idGeneration"
    );
    timestampStrategy = requireSupported(
      timestampStrategy,
      AdminCategoryJdbcProviderProperties.TimestampStrategy.DATABASE_MANAGED,
      "timestampStrategy"
    );
    updatedAtExpression = requireCurrentTimestamp(updatedAtExpression);
  }

  public static AdminCategoryNodeTableDescriptor from(
    CategorySystemDescriptor categorySystem,
    AdminCategoryJdbcProviderProperties.SystemProperties properties
  ) {
    Objects.requireNonNull(categorySystem, "categorySystem");
    Objects.requireNonNull(properties, "properties");
    ClosureTableDescriptor closure = categorySystem.closureTableDescriptor();
    Optional<String> sortRankColumn = categorySystem.ranking().rankingEnabled()
      ? Optional.ofNullable(categorySystem.ranking().columnName())
      : Optional.empty();

    return new AdminCategoryNodeTableDescriptor(
      categorySystem.systemKey(),
      closure.nodeTable(),
      closure.nodeIdColumn(),
      closure.parentIdColumn(),
      properties.getNameColumn(),
      properties.getSlugColumn(),
      properties.getActiveColumn(),
      properties.getCreatedAtColumn(),
      properties.getUpdatedAtColumn(),
      sortRankColumn,
      properties.getSlugUniqueness(),
      properties.getIdGeneration(),
      properties.getTimestamps().getStrategy(),
      properties.getTimestamps().getUpdateExpression()
    );
  }

  private static String requireIdentifier(String value, String fieldName) {
    String identifier = requireText(value, fieldName);
    if (!IDENTIFIER.matcher(identifier).matches()) {
      throw new IllegalArgumentException(fieldName + " must be an unquoted SQL identifier");
    }
    return identifier;
  }

  private static String requireQualifiedIdentifier(String value, String fieldName) {
    String identifier = requireText(value, fieldName);
    if (!QUALIFIED_IDENTIFIER.matcher(identifier).matches()) {
      throw new IllegalArgumentException(
        fieldName + " must be an unquoted SQL identifier or schema-qualified identifier"
      );
    }
    return identifier;
  }

  private static String requireText(String value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    String text = value.trim();
    if (text.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return text;
  }

  private static String requireCurrentTimestamp(String value) {
    String expression = requireText(value, "updatedAtExpression");
    if (!CURRENT_TIMESTAMP.equals(expression)) {
      throw new IllegalArgumentException("updatedAtExpression must be CURRENT_TIMESTAMP");
    }
    return expression;
  }

  private static <T> T requireSupported(T value, T supported, String fieldName) {
    if (value != supported) {
      throw new IllegalArgumentException(fieldName + " must be " + supported);
    }
    return value;
  }
}
