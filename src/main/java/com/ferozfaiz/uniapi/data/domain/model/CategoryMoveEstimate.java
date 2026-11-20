package com.ferozfaiz.uniapi.data.domain.model;

public record CategoryMoveEstimate(
  long subtreeSize,
  long oldAncestorCount,
  long newAncestorCount,
  long estimatedDeleteRows,
  long estimatedInsertRows,
  long estimatedTotalRows,
  int subtreeMaxDepth,
  int newParentDepth,
  int resultingMaxDepth,
  boolean parentChangeRequired
) {}
