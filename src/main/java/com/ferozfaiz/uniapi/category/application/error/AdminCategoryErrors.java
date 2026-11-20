package com.ferozfaiz.uniapi.category.application.error;

import com.ferozfaiz.uniapi.shared.i18n.LocalizedMessage;
import org.springframework.http.HttpStatus;

public final class AdminCategoryErrors {

  private AdminCategoryErrors() {}

  public static AdminCategoryException unknownSystem(String systemKey) {
    return new AdminCategoryException(
      "UNKNOWN_CATEGORY_SYSTEM",
      HttpStatus.NOT_FOUND,
      "category.application.system.unknown",
      "category system was not found",
      systemKey
    );
  }

  public static AdminCategoryException capabilityUnsupported(String operation, String systemKey) {
    return new AdminCategoryException(
      "CATEGORY_CAPABILITY_UNSUPPORTED",
      HttpStatus.CONFLICT,
      "category.application.capability.unsupported",
      "operation is not supported for category system",
      operation,
      systemKey
    );
  }

  public static AdminCategoryException categoryNotFound(Long id) {
    return new AdminCategoryException(
      "CATEGORY_NOT_FOUND",
      HttpStatus.NOT_FOUND,
      "category.application.category.not-found",
      "category was not found",
      id
    );
  }

  public static AdminCategoryException parentNotFound(Long id) {
    return new AdminCategoryException(
      "CATEGORY_PARENT_NOT_FOUND",
      HttpStatus.NOT_FOUND,
      "category.application.parent.not-found",
      "category parent was not found",
      id
    );
  }

  public static AdminCategoryException duplicateSlug(String slug) {
    return new AdminCategoryException(
      "DUPLICATE_CATEGORY_SLUG",
      HttpStatus.CONFLICT,
      "category.application.slug.duplicate",
      "category slug already exists",
      slug
    );
  }

  public static AdminCategoryException deleteBlocked(Long id) {
    return new AdminCategoryException(
      "CATEGORY_DELETE_BLOCKED",
      HttpStatus.CONFLICT,
      "category.application.delete.blocked",
      "category cannot be deleted because dependent data exists",
      id
    );
  }

  public static AdminCategoryException invalidMove(Long id, Long parentId) {
    return new AdminCategoryException(
      "INVALID_CATEGORY_MOVE",
      HttpStatus.BAD_REQUEST,
      "category.application.move.invalid",
      "category move is invalid",
      id,
      parentId
    );
  }

  public static AdminCategoryException invalidPlacementTarget() {
    return new AdminCategoryException(
      "INVALID_CATEGORY_PLACEMENT_TARGET",
      HttpStatus.BAD_REQUEST,
      "category.application.placement.target.invalid",
      "placement target is invalid"
    );
  }

  public static AdminCategoryException treeTooLarge(Object... args) {
    return new AdminCategoryException(
      "CATEGORY_TREE_TOO_LARGE",
      HttpStatus.CONFLICT,
      "category.application.tree.too-large",
      "category tree is too large",
      args
    );
  }

  public static AdminCategoryException parentOptionsCursorInvalid() {
    return new AdminCategoryException(
      "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID",
      HttpStatus.BAD_REQUEST,
      "category.application.parent-options.cursor.invalid",
      "parent-options cursor is invalid"
    );
  }

  public static class AdminCategoryException extends RuntimeException implements LocalizedMessage {

    private final String code;
    private final HttpStatus status;
    private final String messageKey;
    private final Object[] messageArgs;

    public AdminCategoryException(
      String code,
      HttpStatus status,
      String messageKey,
      String defaultMessage,
      Object... messageArgs
    ) {
      super(defaultMessage);
      this.code = code;
      this.status = status;
      this.messageKey = messageKey;
      this.messageArgs = messageArgs.clone();
    }

    public String code() {
      return code;
    }

    public HttpStatus status() {
      return status;
    }

    @Override
    public String messageKey() {
      return messageKey;
    }

    @Override
    public Object[] messageArgs() {
      return messageArgs.clone();
    }
  }
}
