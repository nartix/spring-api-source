package com.ferozfaiz.uniapi.data.api.dto;

public record CategoryMoveEstimateResponse(
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
