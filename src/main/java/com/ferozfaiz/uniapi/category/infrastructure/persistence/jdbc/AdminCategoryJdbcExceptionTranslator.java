package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.HierarchyCycleException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

final class AdminCategoryJdbcExceptionTranslator {

  private AdminCategoryJdbcExceptionTranslator() {}

  static RuntimeException duplicateSlug(String slug, RuntimeException exception) {
    if (
      exception instanceof DuplicateKeyException ||
      exception instanceof DataIntegrityViolationException
    ) {
      return AdminCategoryErrors.duplicateSlug(slug);
    }
    return exception;
  }

  static RuntimeException invalidPlacement(RuntimeException exception) {
    if (exception instanceof IllegalArgumentException) {
      return AdminCategoryErrors.invalidPlacementTarget();
    }
    return exception;
  }

  static RuntimeException invalidMove(Long id, Long parentId, RuntimeException exception) {
    if (exception instanceof HierarchyCycleException) {
      return AdminCategoryErrors.invalidMove(id, parentId);
    }
    return exception;
  }
}
