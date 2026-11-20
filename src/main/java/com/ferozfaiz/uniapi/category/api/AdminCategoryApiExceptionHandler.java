package com.ferozfaiz.uniapi.category.api;

import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = { AdminCategoryController.class })
public class AdminCategoryApiExceptionHandler {

  private static final String SYSTEM_PATH_PREFIX = "/category-systems/";
  private static final String CATEGORY_PATH_SEGMENT = "/categories";
  private static final Pattern SAFE_DIAGNOSTIC_SYSTEM_KEY = Pattern.compile(
    "[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*"
  );

  private final MessageResolver messages;

  public AdminCategoryApiExceptionHandler(MessageResolver messages) {
    this.messages = messages;
  }

  @ExceptionHandler(AdminCategoryException.class)
  public ResponseEntity<ApiErrorResponse> handleAdminCategory(
    AdminCategoryException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(exception.status()).body(
      ApiErrorResponse.of(
        exception.code(),
        messages.get(exception.messageKey(), exception.messageArgs()),
        safeAdminCategoryPath(exception, request.getRequestURI())
      )
    );
  }

  private static String safeAdminCategoryPath(
    AdminCategoryException exception,
    String requestPath
  ) {
    if (!"UNKNOWN_CATEGORY_SYSTEM".equals(exception.code())) {
      return requestPath;
    }
    int keyStart = requestPath.indexOf(SYSTEM_PATH_PREFIX);
    if (keyStart < 0) {
      return requestPath;
    }
    keyStart += SYSTEM_PATH_PREFIX.length();
    int keyEnd = requestPath.indexOf(CATEGORY_PATH_SEGMENT, keyStart);
    if (keyEnd < 0) {
      return requestPath;
    }
    String systemKey = requestPath.substring(keyStart, keyEnd);
    if (systemKey.length() <= 63 && SAFE_DIAGNOSTIC_SYSTEM_KEY.matcher(systemKey).matches()) {
      return requestPath;
    }
    return (requestPath.substring(0, keyStart) + "<unavailable>" + requestPath.substring(keyEnd));
  }

  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
    InvalidRequestException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.badRequest().body(
      ApiErrorResponse.of(
        codeFor(exception),
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }

  private static String codeFor(InvalidRequestException exception) {
    if (exception.messageKey().contains("parent-options.cursor")) {
      return "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID";
    }
    if (exception.messageKey().contains("sort")) {
      return "INVALID_SORT_FIELD";
    }
    if (exception.messageKey().contains("placement")) {
      return "INVALID_CATEGORY_PLACEMENT_TARGET";
    }
    return "VALIDATION_FAILED";
  }
}
