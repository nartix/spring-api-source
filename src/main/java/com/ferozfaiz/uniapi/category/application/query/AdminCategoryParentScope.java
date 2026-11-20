package com.ferozfaiz.uniapi.category.application.query;

import java.util.Objects;

public record AdminCategoryParentScope(Mode mode, Long parentId) {
  public AdminCategoryParentScope {
    Objects.requireNonNull(mode, "mode must not be null");
    if (mode == Mode.CHILDREN) {
      if (parentId == null || parentId <= 0) {
        throw new IllegalArgumentException("children scope requires a positive parentId");
      }
    } else if (parentId != null) {
      throw new IllegalArgumentException(mode + " scope must not carry parentId");
    }
  }

  public static AdminCategoryParentScope all() {
    return new AdminCategoryParentScope(Mode.ALL, null);
  }

  public static AdminCategoryParentScope roots() {
    return new AdminCategoryParentScope(Mode.ROOTS, null);
  }

  public static AdminCategoryParentScope children(Long parentId) {
    return new AdminCategoryParentScope(Mode.CHILDREN, parentId);
  }

  public boolean isSiblingScoped() {
    return mode == Mode.ROOTS || mode == Mode.CHILDREN;
  }

  public enum Mode {
    ALL,
    ROOTS,
    CHILDREN,
  }
}
