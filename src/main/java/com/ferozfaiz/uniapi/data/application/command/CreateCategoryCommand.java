package com.ferozfaiz.uniapi.data.application.command;

import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import java.util.Objects;
import org.springframework.lang.Nullable;

public record CreateCategoryCommand(
  @Nullable Long parentId,
  String slug,
  String name,
  @Nullable String description,
  @Nullable Boolean active,
  SparseOrderTarget orderTarget
) {
  public CreateCategoryCommand {
    Objects.requireNonNull(orderTarget, "orderTarget");
  }

  public CreateCategoryCommand(
    @Nullable Long parentId,
    String slug,
    String name,
    @Nullable String description,
    @Nullable Boolean active
  ) {
    this(parentId, slug, name, description, active, SparseOrderTarget.append());
  }
}
