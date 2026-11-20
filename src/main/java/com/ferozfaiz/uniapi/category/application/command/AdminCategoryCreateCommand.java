package com.ferozfaiz.uniapi.category.application.command;

import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import java.util.Objects;

public record AdminCategoryCreateCommand(
  Long parentId,
  String name,
  String slug,
  boolean active,
  SparseOrderTarget orderTarget
) {
  public AdminCategoryCreateCommand {
    Objects.requireNonNull(orderTarget, "orderTarget must not be null");
  }
}
