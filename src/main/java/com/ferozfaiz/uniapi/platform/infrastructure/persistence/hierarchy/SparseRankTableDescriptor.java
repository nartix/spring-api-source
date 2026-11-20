package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

public record SparseRankTableDescriptor(
  String tableName,
  String idColumn,
  String parentIdColumn,
  String rankColumn
) {
  public SparseRankTableDescriptor {
    tableName = SqlIdentifiers.requireQualifiedIdentifier(tableName, "tableName");
    idColumn = SqlIdentifiers.requireIdentifier(idColumn, "idColumn");
    parentIdColumn = SqlIdentifiers.requireIdentifier(parentIdColumn, "parentIdColumn");
    rankColumn = SqlIdentifiers.requireIdentifier(rankColumn, "rankColumn");
  }

  public static SparseRankTableDescriptor of(
    String tableName,
    String parentIdColumn,
    String rankColumn
  ) {
    return new SparseRankTableDescriptor(tableName, "id", parentIdColumn, rankColumn);
  }
}
