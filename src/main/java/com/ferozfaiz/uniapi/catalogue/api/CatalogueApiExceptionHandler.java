package com.ferozfaiz.uniapi.catalogue.api;

import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy.CatalogueInvalidRequestException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueCategoryPlacementTargetException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueCategoryRankingException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateProductIdentifierException;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
  assignableTypes = {
    CatalogueController.class,
    CatalogueCategoryController.class,
    CatalogueReferenceDataController.class,
  }
)
public class CatalogueApiExceptionHandler {

  private final MessageResolver messages;

  public CatalogueApiExceptionHandler(MessageResolver messages) {
    this.messages = messages;
  }

  @ExceptionHandler(CatalogueNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNotFound(
    CatalogueNotFoundException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
      ApiErrorResponse.of(
        exception.code(),
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(DuplicateCatalogueSlugException.class)
  public ResponseEntity<ApiErrorResponse> handleDuplicateSlug(
    DuplicateCatalogueSlugException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      ApiErrorResponse.of(
        exception.code(),
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(DuplicateProductIdentifierException.class)
  public ResponseEntity<ApiErrorResponse> handleDuplicateIdentifier(
    DuplicateProductIdentifierException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      ApiErrorResponse.of(
        exception.code(),
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(CatalogueReferenceNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleReferenceNotFound(
    CatalogueReferenceNotFoundException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
      ApiErrorResponse.of(
        exception.code(),
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(CatalogueConflictException.class)
  public ResponseEntity<ApiErrorResponse> handleConflict(
    CatalogueConflictException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      ApiErrorResponse.of(
        exception.code(),
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(CatalogueCategoryRankingException.class)
  public ResponseEntity<ApiErrorResponse> handleCategoryRankingConflict(
    CatalogueCategoryRankingException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      ApiErrorResponse.of(
        exception.code(),
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(CatalogueCategoryPlacementTargetException.class)
  public ResponseEntity<ApiErrorResponse> handleCategoryPlacementTarget(
    CatalogueCategoryPlacementTargetException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.badRequest().body(
      ApiErrorResponse.of(
        exception.code(),
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(CatalogueException.class)
  public ResponseEntity<ApiErrorResponse> handleCatalogueException(
    CatalogueException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.badRequest().body(
      ApiErrorResponse.of(
        "VALIDATION_FAILED",
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
  }

  @ExceptionHandler(CatalogueInvalidRequestException.class)
  public ResponseEntity<ApiErrorResponse> handleCatalogueInvalidRequest(
    CatalogueInvalidRequestException exception,
    HttpServletRequest request
  ) {
    return ResponseEntity.badRequest().body(
      ApiErrorResponse.of(
        exception.code(),
        messages.get(exception.messageKey(), exception.messageArgs()),
        request.getRequestURI()
      )
    );
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
    if (exception.messageKey().contains("sort")) {
      return "INVALID_SORT_FIELD";
    }
    if (exception.messageKey().contains("filter")) {
      return "INVALID_FILTER_FIELD";
    }
    if (exception.messageKey().contains("placement.target")) {
      return "INVALID_CATEGORY_PLACEMENT_TARGET";
    }
    if (
      exception.messageKey().contains("placement.unsupported") ||
      exception.messageKey().contains("ranking.disabled")
    ) {
      return "UNSUPPORTED_CATEGORY_PLACEMENT";
    }
    return "VALIDATION_FAILED";
  }
}
