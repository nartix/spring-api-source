package com.ferozfaiz.uniapi.data.domain.exception;

public class CategoryHierarchyPolicyViolationException extends RuntimeException {

  private final String messageKey;
  private final Object[] messageArgs;

  private CategoryHierarchyPolicyViolationException(String messageKey, Object... messageArgs) {
    super(messageKey);
    this.messageKey = messageKey;
    this.messageArgs = messageArgs.clone();
  }

  public static CategoryHierarchyPolicyViolationException leafOnlyMoveRequiresLeaf(
    long subtreeSize
  ) {
    return new CategoryHierarchyPolicyViolationException(
      "data.domain.category.hierarchy.leaf-only",
      subtreeSize
    );
  }

  public static CategoryHierarchyPolicyViolationException moveSubtreeTooLarge(
    long subtreeSize,
    long maxSubtreeSize
  ) {
    return new CategoryHierarchyPolicyViolationException(
      "data.domain.category.hierarchy.move.subtree-size",
      subtreeSize,
      maxSubtreeSize
    );
  }

  public static CategoryHierarchyPolicyViolationException moveAffectedRowsTooLarge(
    long estimatedRows,
    long maxRows
  ) {
    return new CategoryHierarchyPolicyViolationException(
      "data.domain.category.hierarchy.move.affected-rows",
      estimatedRows,
      maxRows
    );
  }

  public static CategoryHierarchyPolicyViolationException moveDepthTooLarge(
    int resultingDepth,
    int maxDepth
  ) {
    return new CategoryHierarchyPolicyViolationException(
      "data.domain.category.hierarchy.move.depth",
      resultingDepth,
      maxDepth
    );
  }

  public static CategoryHierarchyPolicyViolationException deleteSubtreeTooLarge(
    long subtreeSize,
    long maxSubtreeSize
  ) {
    return new CategoryHierarchyPolicyViolationException(
      "data.domain.category.hierarchy.delete.subtree-size",
      subtreeSize,
      maxSubtreeSize
    );
  }

  public static CategoryHierarchyPolicyViolationException readTooLarge(
    long requestedNodes,
    long maxNodes
  ) {
    return new CategoryHierarchyPolicyViolationException(
      "data.domain.category.hierarchy.read.node-count",
      requestedNodes,
      maxNodes
    );
  }

  public static CategoryHierarchyPolicyViolationException rankRebalanceLimitExceeded(
    long affectedRows,
    long maxRows
  ) {
    return new CategoryHierarchyPolicyViolationException(
      "data.domain.category.ranking.rebalance-limit",
      affectedRows,
      maxRows
    );
  }

  public String messageKey() {
    return messageKey;
  }

  public Object[] messageArgs() {
    return messageArgs.clone();
  }
}
