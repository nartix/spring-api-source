package com.ferozfaiz.uniapi.platform.web;

import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.error.ApiFieldError;
import com.ferozfaiz.uniapi.shared.i18n.LocalizedMessage;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

  private final MessageResolver messages;

  public RestExceptionHandler(MessageResolver messages) {
    this.messages = messages;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidationFailure(
    MethodArgumentNotValidException exception,
    HttpServletRequest request
  ) {
    log
      .atDebug()
      .addKeyValue("http.path", request.getRequestURI())
      .addKeyValue("validation.error_count", exception.getBindingResult().getFieldErrorCount())
      .log("request validation failed");

    List<ApiFieldError> fieldErrors = exception
      .getBindingResult()
      .getFieldErrors()
      .stream()
      .map(this::toApiFieldError)
      .toList();

    return ResponseEntity.badRequest().body(
      ApiErrorResponse.withFieldErrors(
        "VALIDATION_FAILED",
        messages.get("platform.api.validation.failed"),
        request.getRequestURI(),
        fieldErrors
      )
    );
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiErrorResponse> handleBindFailure(
    BindException exception,
    HttpServletRequest request
  ) {
    log
      .atDebug()
      .addKeyValue("http.path", request.getRequestURI())
      .addKeyValue("validation.error_count", exception.getBindingResult().getFieldErrorCount())
      .log("request binding failed");

    List<ApiFieldError> fieldErrors = exception
      .getBindingResult()
      .getFieldErrors()
      .stream()
      .map(this::toApiFieldError)
      .toList();

    return ResponseEntity.badRequest().body(
      ApiErrorResponse.withFieldErrors(
        "VALIDATION_FAILED",
        messages.get("platform.api.validation.failed"),
        request.getRequestURI(),
        fieldErrors
      )
    );
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
    HttpMessageNotReadableException exception,
    HttpServletRequest request
  ) {
    log
      .atWarn()
      .addKeyValue("http.path", request.getRequestURI())
      .addKeyValue("error.type", exception.getClass().getSimpleName())
      .log("malformed request body rejected");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
      ApiErrorResponse.of(
        "MALFORMED_REQUEST",
        messages.get("platform.api.request.malformed"),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
    IllegalArgumentException exception,
    HttpServletRequest request
  ) {
    log
      .atWarn()
      .addKeyValue("http.path", request.getRequestURI())
      .addKeyValue("error.type", exception.getClass().getSimpleName())
      .log("invalid request rejected");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
      ApiErrorResponse.of(
        "INVALID_REQUEST",
        resolveExceptionMessage(exception),
        request.getRequestURI()
      )
    );
  }

  private ApiFieldError toApiFieldError(FieldError fieldError) {
    return new ApiFieldError(fieldError.getField(), fieldError.getDefaultMessage());
  }

  private String resolveExceptionMessage(IllegalArgumentException exception) {
    if (exception instanceof LocalizedMessage localized) {
      return messages.get(localized.messageKey(), localized.messageArgs());
    }
    return messages.get("platform.api.request.invalid");
  }
}
