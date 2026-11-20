package com.ferozfaiz.uniapi.shared.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
  String code,
  String message,
  String path,
  Instant timestamp,
  List<ApiFieldError> fieldErrors
) {
  public static ApiErrorResponse of(String code, String message, String path) {
    return new ApiErrorResponse(code, message, path, Instant.now(), List.of());
  }

  public static ApiErrorResponse withFieldErrors(
    String code,
    String message,
    String path,
    List<ApiFieldError> fieldErrors
  ) {
    return new ApiErrorResponse(code, message, path, Instant.now(), List.copyOf(fieldErrors));
  }
}
