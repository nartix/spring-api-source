package com.ferozfaiz.uniapi.category.application.command;

import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import java.util.Objects;

public record AdminCategoryMoveCommand(Long newParentId, SparseOrderTarget orderTarget) {
  public AdminCategoryMoveCommand {
    Objects.requireNonNull(orderTarget, "orderTarget must not be null");
  }
}
