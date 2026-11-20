package com.ferozfaiz.uniapi.category.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AdminCategoryOpenApiDocumentation {

  private static final String BEARER_AUTH = "bearerAuth";
  private static final String GENERIC_PREFIX =
    "/api/v1/admin/category-systems/{systemKey}/categories";

  @Bean
  OpenApiCustomizer adminCategoryOpenApiCustomizer() {
    return openApi -> {
      Components components = openApi.getComponents();
      if (components == null) {
        components = new Components();
        openApi.setComponents(components);
      }
      components.addSecuritySchemes(
        BEARER_AUTH,
        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
      );

      if (openApi.getPaths() == null) {
        return;
      }

      openApi.getPaths().forEach((path, pathItem) -> {
        if (path.startsWith(GENERIC_PREFIX)) {
          documentGenericCategoryPath(path, pathItem);
        }
      });
    };
  }

  private static void documentGenericCategoryPath(String path, PathItem pathItem) {
    pathItem.readOperationsMap().forEach((method, operation) -> {
      secure(operation);
      addCommonAdminResponses(operation);
      addGenericResponses(path, method, operation);
    });
  }

  private static void secure(Operation operation) {
    if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
      operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
  }

  private static void addCommonAdminResponses(Operation operation) {
    addResponse(operation, "401", "Authentication is required", false);
    addResponse(operation, "403", "User lacks the required category permission", false);
    addResponse(
      operation,
      "409",
      "Operation is not supported by the selected category system (CATEGORY_CAPABILITY_UNSUPPORTED)",
      true
    );
  }

  private static void addGenericResponses(
    String path,
    PathItem.HttpMethod method,
    Operation operation
  ) {
    if (method == PathItem.HttpMethod.GET && path.equals(GENERIC_PREFIX)) {
      addResponse(
        operation,
        "400",
        "Invalid list request (VALIDATION_FAILED, INVALID_SORT_FIELD)",
        true
      );
      addResponse(
        operation,
        "404",
        "Selected system or parent category was not found (UNKNOWN_CATEGORY_SYSTEM, CATEGORY_PARENT_NOT_FOUND)",
        true
      );
      return;
    }

    if (path.equals(GENERIC_PREFIX + "/tree")) {
      addResponse(
        operation,
        "400",
        "Invalid tree request (VALIDATION_FAILED, INVALID_SORT_FIELD)",
        true
      );
      addResponse(
        operation,
        "404",
        "Selected category system was not found (UNKNOWN_CATEGORY_SYSTEM)",
        true
      );
      addResponse(operation, "409", "Tree exceeds policy (CATEGORY_TREE_TOO_LARGE)", true);
      return;
    }

    if (path.equals(GENERIC_PREFIX + "/parent-options")) {
      addResponse(
        operation,
        "400",
        "Invalid parent-options request (VALIDATION_FAILED, CATEGORY_PARENT_OPTIONS_CURSOR_INVALID)",
        true
      );
      addResponse(
        operation,
        "404",
        "Selected category system or excluded category was not found (UNKNOWN_CATEGORY_SYSTEM, CATEGORY_NOT_FOUND)",
        true
      );
      return;
    }

    if (path.equals(GENERIC_PREFIX + "/{id}/move")) {
      addMutationResponses(operation);
      addResponse(
        operation,
        "400",
        "Invalid move or placement request (VALIDATION_FAILED, INVALID_CATEGORY_MOVE, INVALID_CATEGORY_PLACEMENT_TARGET)",
        true
      );
      return;
    }

    if (path.equals(GENERIC_PREFIX + "/{id}") && method == PathItem.HttpMethod.DELETE) {
      addMutationResponses(operation);
      addResponse(
        operation,
        "409",
        "Deletion is blocked by children, assignments, or selected-system policy (CATEGORY_DELETE_BLOCKED)",
        true
      );
      return;
    }

    if (method == PathItem.HttpMethod.POST && path.equals(GENERIC_PREFIX)) {
      addMutationResponses(operation);
      addResponse(
        operation,
        "409",
        "Slug already exists in the selected system (DUPLICATE_CATEGORY_SLUG)",
        true
      );
      return;
    }

    if (method != PathItem.HttpMethod.GET) {
      addMutationResponses(operation);
    } else {
      addResponse(
        operation,
        "404",
        "Selected category system or category was not found (UNKNOWN_CATEGORY_SYSTEM, CATEGORY_NOT_FOUND)",
        true
      );
    }
  }

  private static void addMutationResponses(Operation operation) {
    addResponse(
      operation,
      "400",
      "Invalid category mutation request (VALIDATION_FAILED, INVALID_CATEGORY_PLACEMENT_TARGET)",
      true
    );
    addResponse(
      operation,
      "404",
      "Selected system, category, or parent category was not found (UNKNOWN_CATEGORY_SYSTEM, CATEGORY_NOT_FOUND, CATEGORY_PARENT_NOT_FOUND)",
      true
    );
  }

  private static void addResponse(
    Operation operation,
    String status,
    String description,
    boolean withErrorBody
  ) {
    if (operation.getResponses() == null) {
      return;
    }
    ApiResponse existing = operation.getResponses().get(status);
    if (existing != null && existing.getDescription() != null) {
      description = existing.getDescription() + "; " + description;
    }
    ApiResponse response = new ApiResponse().description(description);
    if (withErrorBody) {
      response.content(errorContent());
    }
    operation.getResponses().addApiResponse(status, response);
  }

  private static Content errorContent() {
    return new Content().addMediaType(
      "application/json",
      new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))
    );
  }
}
