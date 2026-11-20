package com.ferozfaiz.uniapi.data.application.command;

import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveMode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import java.util.Objects;
import org.springframework.lang.Nullable;

public record MoveCategoryCommand(
  @Nullable Long parentId,
  CategoryMoveMode mode,
  SparseOrderTarget orderTarget
) {
  public MoveCategoryCommand {
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(orderTarget, "orderTarget");
  }

  public MoveCategoryCommand(@Nullable Long parentId, CategoryMoveMode mode) {
    this(parentId, mode, SparseOrderTarget.append());
  }
}
