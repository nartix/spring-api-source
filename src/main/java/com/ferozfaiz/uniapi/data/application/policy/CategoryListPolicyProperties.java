package com.ferozfaiz.uniapi.data.application.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.data.category.list")
public class CategoryListPolicyProperties {

  private int maxPageSize = 100;
  private boolean enforceMaxPageSize = true;

  public int getMaxPageSize() {
    return maxPageSize;
  }

  public void setMaxPageSize(int maxPageSize) {
    if (maxPageSize < 1) {
      throw new IllegalArgumentException("maxPageSize must be at least 1");
    }
    this.maxPageSize = maxPageSize;
  }

  public boolean isEnforceMaxPageSize() {
    return enforceMaxPageSize;
  }

  public void setEnforceMaxPageSize(boolean enforceMaxPageSize) {
    this.enforceMaxPageSize = enforceMaxPageSize;
  }
}
