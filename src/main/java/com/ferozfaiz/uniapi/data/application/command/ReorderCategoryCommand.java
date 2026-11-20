package com.ferozfaiz.uniapi.data.application.command;

import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import java.util.Objects;
import org.springframework.lang.Nullable;

public record ReorderCategoryCommand(@Nullable Long parentId, SparseOrderTarget orderTarget) {
  public ReorderCategoryCommand {
    Objects.requireNonNull(orderTarget, "orderTarget");
  }
}
