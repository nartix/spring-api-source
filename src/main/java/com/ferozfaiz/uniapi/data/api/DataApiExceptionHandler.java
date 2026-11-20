package com.ferozfaiz.uniapi.data.api;

import com.ferozfaiz.uniapi.data.domain.exception.CategoryHasChildrenException;
import com.ferozfaiz.uniapi.data.domain.exception.CategoryHierarchyCycleException;
import com.ferozfaiz.uniapi.data.domain.exception.CategoryHierarchyPolicyViolationException;
import com.ferozfaiz.uniapi.data.domain.exception.CategoryNotFoundException;
import com.ferozfaiz.uniapi.data.domain.exception.DuplicateCategoryException;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CategoryController.class)
public class DataApiExceptionHandler {

  private final MessageResolver messages;

  public DataApiExceptionHandler(MessageResolver messages) {
    this.messages = messages;
  }

  @ExceptionHandler(CategoryNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleCategoryNotFound(
    CategoryNotFoundException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
      ApiErrorResponse.of(
        "CATEGORY_NOT_FOUND",
        messages.get(exception.messageKey()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(DuplicateCategoryException.class)
  public ResponseEntity<ApiErrorResponse> handleDuplicateCategory(
    DuplicateCategoryException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      ApiErrorResponse.of(
        "CATEGORY_ALREADY_EXISTS",
        messages.get(exception.messageKey()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(CategoryHasChildrenException.class)
  public ResponseEntity<ApiErrorResponse> handleCategoryHasChildren(
    CategoryHasChildrenException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      ApiErrorResponse.of(
        "CATEGORY_HAS_CHILDREN",
        messages.get(exception.messageKey()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(CategoryHierarchyCycleException.class)
  public ResponseEntity<ApiErrorResponse> handleCategoryHierarchyCycle(
    CategoryHierarchyCycleException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      ApiErrorResponse.of(
        "CATEGORY_HIERARCHY_CYCLE",
        messages.get(exception.messageKey()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(CategoryHierarchyPolicyViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleCategoryHierarchyPolicyViolation(
    CategoryHierarchyPolicyViolationException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      ApiErrorResponse.of(
        "CATEGORY_HIERARCHY_POLICY_VIOLATION",
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }
}
