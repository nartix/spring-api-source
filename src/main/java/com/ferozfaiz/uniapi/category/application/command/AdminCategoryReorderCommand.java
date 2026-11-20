package com.ferozfaiz.uniapi.category.application.command;

import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import java.util.Objects;

public record AdminCategoryReorderCommand(Long parentId, SparseOrderTarget orderTarget) {
  public AdminCategoryReorderCommand {
    Objects.requireNonNull(orderTarget, "orderTarget must not be null");
  }
}
