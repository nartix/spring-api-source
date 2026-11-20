package com.ferozfaiz.uniapi.catalogue.application.query;

import java.util.Objects;

public record CategoryParentScope(Mode mode, Long parentId) {
  public CategoryParentScope {
    Objects.requireNonNull(mode, "mode must not be null");
    if (mode == Mode.CHILDREN) {
      if (parentId == null || parentId <= 0) {
        throw new IllegalArgumentException("children scope requires a positive parentId");
      }
    } else if (parentId != null) {
      throw new IllegalArgumentException(mode + " scope must not carry parentId");
    }
  }

  public static CategoryParentScope all() {
    return new CategoryParentScope(Mode.ALL, null);
  }

  public static CategoryParentScope roots() {
    return new CategoryParentScope(Mode.ROOTS, null);
  }

  public static CategoryParentScope children(Long parentId) {
    return new CategoryParentScope(Mode.CHILDREN, parentId);
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
