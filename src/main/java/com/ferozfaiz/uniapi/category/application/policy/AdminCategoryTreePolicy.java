package com.ferozfaiz.uniapi.category.application.policy;

import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.data.category.hierarchy")
public class AdminCategoryTreePolicy {

  private int maxTreeReadNodes = 10_000;

  public int maxTreeReadNodes() {
    return maxTreeReadNodes;
  }

  public void setMaxTreeReadNodes(int maxTreeReadNodes) {
    if (maxTreeReadNodes < 1) {
      throw new IllegalArgumentException("maxTreeReadNodes must be at least 1");
    }
    this.maxTreeReadNodes = maxTreeReadNodes;
  }

  public PageCriteria completeTreePage() {
    return new PageCriteria(0, maxTreeReadNodes);
  }

  public PageCriteria resolvePage(Integer page, Integer size) {
    int resolvedPage = page == null ? 0 : page;
    int resolvedSize = size == null ? maxTreeReadNodes : size;
    if (resolvedSize > maxTreeReadNodes) {
      throw AdminCategoryErrors.treeTooLarge(resolvedSize, maxTreeReadNodes);
    }
    return new PageCriteria(resolvedPage, resolvedSize);
  }

  public void enforceCompleteTree(long totalNodes) {
    if (totalNodes > maxTreeReadNodes) {
      throw AdminCategoryErrors.treeTooLarge(totalNodes, maxTreeReadNodes);
    }
  }

  public void enforceCompleteTree(long totalNodes, int requestedSize) {
    if (totalNodes > maxTreeReadNodes || totalNodes > requestedSize) {
      throw AdminCategoryErrors.treeTooLarge(totalNodes, requestedSize, maxTreeReadNodes);
    }
  }
}
