package com.ferozfaiz.uniapi.iam.api;

import com.ferozfaiz.uniapi.iam.domain.exception.DuplicateUserException;
import com.ferozfaiz.uniapi.iam.domain.exception.UserNotFoundException;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = { UserController.class, AdminUserController.class })
public class IamApiExceptionHandler {

  private final MessageResolver messages;

  public IamApiExceptionHandler(MessageResolver messages) {
    this.messages = messages;
  }

  @ExceptionHandler(DuplicateUserException.class)
  public ResponseEntity<ApiErrorResponse> handleDuplicateUser(
    DuplicateUserException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      ApiErrorResponse.of(
        "USER_ALREADY_EXISTS",
        messages.get(exception.messageKey()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleUserNotFound(
    UserNotFoundException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
      ApiErrorResponse.of(
        "USER_NOT_FOUND",
        messages.get(exception.messageKey()),
        request.getRequestURI()
      )
    );
  }
}
