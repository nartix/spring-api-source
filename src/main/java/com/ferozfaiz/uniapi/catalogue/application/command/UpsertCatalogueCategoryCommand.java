package com.ferozfaiz.uniapi.catalogue.application.command;

import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import java.util.Objects;

public record UpsertCatalogueCategoryCommand(
  Long id,
  Long parentId,
  String name,
  String slug,
  boolean active,
  SparseOrderTarget orderTarget
) {
  public UpsertCatalogueCategoryCommand {
    Objects.requireNonNull(orderTarget, "orderTarget");
  }

  public UpsertCatalogueCategoryCommand(
    Long id,
    Long parentId,
    String name,
    String slug,
    boolean active
  ) {
    this(id, parentId, name, slug, active, SparseOrderTarget.append());
  }
}
