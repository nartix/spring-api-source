package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

public record MoveSubtreeEstimate(
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
