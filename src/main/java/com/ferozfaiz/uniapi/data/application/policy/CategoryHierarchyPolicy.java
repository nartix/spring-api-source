package com.ferozfaiz.uniapi.data.application.policy;

import com.ferozfaiz.uniapi.data.domain.exception.CategoryHierarchyPolicyViolationException;
import com.ferozfaiz.uniapi.data.domain.model.CategoryDeleteEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveMode;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CategoryHierarchyPolicy {

  private final CategoryHierarchyPolicyProperties properties;

  public CategoryHierarchyPolicy(CategoryHierarchyPolicyProperties properties) {
    this.properties = properties;
  }

  public void enforceMove(CategoryMoveEstimate estimate, CategoryMoveMode moveMode) {
    if (moveMode == CategoryMoveMode.LEAF_ONLY && estimate.subtreeSize() > 1) {
      throw CategoryHierarchyPolicyViolationException.leafOnlyMoveRequiresLeaf(
        estimate.subtreeSize()
      );
    }

    if (estimate.subtreeSize() > properties.getMaxSyncMoveSubtreeNodes()) {
      throw CategoryHierarchyPolicyViolationException.moveSubtreeTooLarge(
        estimate.subtreeSize(),
        properties.getMaxSyncMoveSubtreeNodes()
      );
    }

    if (estimate.estimatedTotalRows() > properties.getMaxSyncMoveAffectedRows()) {
      throw CategoryHierarchyPolicyViolationException.moveAffectedRowsTooLarge(
        estimate.estimatedTotalRows(),
        properties.getMaxSyncMoveAffectedRows()
      );
    }

    if (estimate.resultingMaxDepth() > properties.getMaxDepth()) {
      throw CategoryHierarchyPolicyViolationException.moveDepthTooLarge(
        estimate.resultingMaxDepth(),
        properties.getMaxDepth()
      );
    }
  }

  public void enforceDelete(CategoryDeleteEstimate estimate) {
    if (estimate.subtreeSize() > properties.getMaxSyncDeleteSubtreeNodes()) {
      throw CategoryHierarchyPolicyViolationException.deleteSubtreeTooLarge(
        estimate.subtreeSize(),
        properties.getMaxSyncDeleteSubtreeNodes()
      );
    }
  }

  public void enforceTreeRead(long requestedNodes) {
    if (requestedNodes > properties.getMaxTreeReadNodes()) {
      throw CategoryHierarchyPolicyViolationException.readTooLarge(
        requestedNodes,
        properties.getMaxTreeReadNodes()
      );
    }
  }

  public Duration syncStatementTimeout() {
    return properties.getSyncStatementTimeout();
  }
}
