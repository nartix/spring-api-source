package com.ferozfaiz.uniapi.data.application.policy;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.data.category.hierarchy")
public class CategoryHierarchyPolicyProperties {

  private long maxSyncMoveAffectedRows = 100_000;
  private long maxSyncMoveSubtreeNodes = 10_000;
  private int maxDepth = 100;
  private long maxSyncDeleteSubtreeNodes = 1;
  private long maxTreeReadNodes = 10_000;
  private Duration syncStatementTimeout = Duration.ofSeconds(25);

  public long getMaxSyncMoveAffectedRows() {
    return maxSyncMoveAffectedRows;
  }

  public void setMaxSyncMoveAffectedRows(long maxSyncMoveAffectedRows) {
    this.maxSyncMoveAffectedRows = maxSyncMoveAffectedRows;
  }

  public long getMaxSyncMoveSubtreeNodes() {
    return maxSyncMoveSubtreeNodes;
  }

  public void setMaxSyncMoveSubtreeNodes(long maxSyncMoveSubtreeNodes) {
    this.maxSyncMoveSubtreeNodes = maxSyncMoveSubtreeNodes;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public long getMaxSyncDeleteSubtreeNodes() {
    return maxSyncDeleteSubtreeNodes;
  }

  public void setMaxSyncDeleteSubtreeNodes(long maxSyncDeleteSubtreeNodes) {
    this.maxSyncDeleteSubtreeNodes = maxSyncDeleteSubtreeNodes;
  }

  public long getMaxTreeReadNodes() {
    return maxTreeReadNodes;
  }

  public void setMaxTreeReadNodes(long maxTreeReadNodes) {
    this.maxTreeReadNodes = maxTreeReadNodes;
  }

  public Duration getSyncStatementTimeout() {
    return syncStatementTimeout;
  }

  public void setSyncStatementTimeout(Duration syncStatementTimeout) {
    this.syncStatementTimeout = syncStatementTimeout;
  }
}
