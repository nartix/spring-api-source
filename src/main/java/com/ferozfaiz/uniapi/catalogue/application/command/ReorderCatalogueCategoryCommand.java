package com.ferozfaiz.uniapi.catalogue.application.command;

import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import java.util.Objects;
import org.springframework.lang.Nullable;

public record ReorderCatalogueCategoryCommand(
  @Nullable Long parentId,
  SparseOrderTarget orderTarget
) {
  public ReorderCatalogueCategoryCommand {
    Objects.requireNonNull(orderTarget, "orderTarget");
  }
}
