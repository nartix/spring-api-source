package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class GenericAdminCategoryOpenApiDocumentationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String GENERIC_PREFIX =
    "/api/v1/admin/category-systems/{systemKey}/categories";
  private static final String CATALOGUE_PREFIX = "/api/v1/catalogue/categories";
  private static final String CATALOGUE_PRODUCT_LISTING = "/api/v1/categories/{slug}/products";
  private static final String REMOVED_PREFIX = "/api/v1/admin/categories";
  private static final Set<String> HTTP_METHODS = Set.of(
    "get",
    "post",
    "put",
    "patch",
    "delete",
    "head",
    "options",
    "trace"
  );
  private static final List<DocumentedOperation> EXPECTED_OPERATIONS = List.of(
    new DocumentedOperation(GENERIC_PREFIX, "get"),
    new DocumentedOperation(GENERIC_PREFIX + "/capabilities", "get"),
    new DocumentedOperation(GENERIC_PREFIX + "/tree", "get"),
    new DocumentedOperation(GENERIC_PREFIX + "/parent-options", "get"),
    new DocumentedOperation(GENERIC_PREFIX + "/{id}", "get"),
    new DocumentedOperation(GENERIC_PREFIX + "/{id}/breadcrumb", "get"),
    new DocumentedOperation(GENERIC_PREFIX, "post"),
    new DocumentedOperation(GENERIC_PREFIX + "/{id}", "put"),
    new DocumentedOperation(GENERIC_PREFIX + "/{id}", "patch"),
    new DocumentedOperation(GENERIC_PREFIX + "/{id}", "delete"),
    new DocumentedOperation(GENERIC_PREFIX + "/{id}/move", "post"),
    new DocumentedOperation(GENERIC_PREFIX + "/{id}/reorder", "post"),
    new DocumentedOperation(GENERIC_PREFIX + "/{id}/activate", "post"),
    new DocumentedOperation(GENERIC_PREFIX + "/{id}/deactivate", "post")
  );
  private static final List<DocumentedOperation> EXPECTED_CATALOGUE_OVERLAP_OPERATIONS = List.of(
    new DocumentedOperation(CATALOGUE_PREFIX + "/capabilities", "get"),
    new DocumentedOperation(CATALOGUE_PREFIX, "post"),
    new DocumentedOperation(CATALOGUE_PREFIX + "/{id}", "put"),
    new DocumentedOperation(CATALOGUE_PREFIX + "/{id}", "patch"),
    new DocumentedOperation(CATALOGUE_PREFIX + "/{id}", "delete"),
    new DocumentedOperation(CATALOGUE_PREFIX + "/{id}/move", "post"),
    new DocumentedOperation(CATALOGUE_PREFIX + "/{id}/reorder", "post"),
    new DocumentedOperation(CATALOGUE_PREFIX + "/{id}/activate", "post"),
    new DocumentedOperation(CATALOGUE_PREFIX + "/{id}/deactivate", "post")
  );
  private static final Set<String> FOUNDATION_SCHEMA_PREFIXES = Set.of(
    "AdminCategoryApiProfile",
    "AdminCategoryIdentifierCodec",
    "AdminCategoryIdentifierProfile",
    "AdminCategoryLogicalPolicyId",
    "AdminCategoryPermissionMapping",
    "AdminCategoryPolicyObligation",
    "AdminCategoryPolicyReference",
    "AdminCategoryPolicyReferences",
    "AdminCategoryRegistration",
    "AdminCategorySystemDefinition",
    "AdminCategorySystemRegistration",
    "AdminCategorySystemRegistry",
    "AdminCategoryValidation",
    "Binding",
    "FieldQueryDeclaration",
    "HardMaxima",
    "HierarchyDeclaration",
    "LifecycleDeclaration",
    "LogicalOrder",
    "OperationDeclaration",
    "OperationalLimits",
    "OrderingDeclaration"
  );

  @Autowired
  private MockMvc mvc;

  @Test
  void generatedOpenApiDocumentsGenericAdminCategoryContractOnly() throws Exception {
    String openApi = mvc
      .perform(get("/v3/api-docs"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    JsonNode docs = JSON.readTree(openApi);
    JsonNode genericList = operation(
      docs,
      "/api/v1/admin/category-systems/{systemKey}/categories",
      "get"
    );
    JsonNode genericCreate = operation(
      docs,
      "/api/v1/admin/category-systems/{systemKey}/categories",
      "post"
    );
    JsonNode genericCapabilities = operation(
      docs,
      "/api/v1/admin/category-systems/{systemKey}/categories/capabilities",
      "get"
    );
    JsonNode genericTree = operation(
      docs,
      "/api/v1/admin/category-systems/{systemKey}/categories/tree",
      "get"
    );
    JsonNode genericParentOptions = operation(docs, GENERIC_PREFIX + "/parent-options", "get");
    JsonNode genericGet = operation(docs, GENERIC_PREFIX + "/{id}", "get");
    JsonNode genericBreadcrumb = operation(docs, GENERIC_PREFIX + "/{id}/breadcrumb", "get");
    JsonNode genericUpdate = operation(docs, GENERIC_PREFIX + "/{id}", "put");
    JsonNode genericPatch = operation(docs, GENERIC_PREFIX + "/{id}", "patch");
    JsonNode genericDelete = operation(docs, GENERIC_PREFIX + "/{id}", "delete");
    JsonNode genericMove = operation(docs, GENERIC_PREFIX + "/{id}/move", "post");
    JsonNode genericReorder = operation(docs, GENERIC_PREFIX + "/{id}/reorder", "post");
    JsonNode genericActivate = operation(docs, GENERIC_PREFIX + "/{id}/activate", "post");
    JsonNode genericDeactivate = operation(docs, GENERIC_PREFIX + "/{id}/deactivate", "post");

    assertThat(documentedOperationsUnder(docs, GENERIC_PREFIX)).containsExactlyInAnyOrderElementsOf(
      EXPECTED_OPERATIONS
    );
    assertThat(pathNames(docs)).noneMatch(path -> isExactPathOrDescendant(path, REMOVED_PREFIX));
    assertThat(schemaNames(docs)).noneMatch(schemaName ->
      FOUNDATION_SCHEMA_PREFIXES.stream().anyMatch(schemaName::startsWith)
    );

    assertThat(parameterNames(genericList)).contains(
      "systemKey",
      "page",
      "size",
      "sort",
      "parentId",
      "active",
      "q",
      "slug"
    );
    assertThat(parameterNames(genericParentOptions)).contains(
      "systemKey",
      "q",
      "limit",
      "cursor",
      "includeIds",
      "excludeSubtreeId"
    );
    assertThat(successSchema(genericList).toString()).contains("PageResponse");
    assertThat(successSchema(genericTree).toString()).contains("PageResponse");
    assertThat(successSchema(genericParentOptions).toString()).contains(
      "AdminCategoryParentOptionSliceResponse"
    );

    assertThat(openApi)
      .contains("/api/v1/admin/category-systems/{systemKey}/categories")
      .contains("/api/v1/admin/category-systems/{systemKey}/categories/capabilities")
      .contains("/api/v1/admin/category-systems/{systemKey}/categories/tree")
      .contains("/api/v1/admin/category-systems/{systemKey}/categories/parent-options")
      .contains("PageResponse")
      .contains("rankingEnabled")
      .contains("placementModes")
      .contains("sortRankExposed")
      .contains("fallbackOrder")
      .contains("supportedOperations")
      .contains("searchableFields")
      .contains("allowedFilters")
      .contains("allowedSorts")
      .contains("identifierProfile")
      .contains("hierarchyDepth");
    assertThat(openApi).doesNotContain(REMOVED_PREFIX);

    assertBearerSecurityScheme(docs);
    List.of(
      genericCapabilities,
      genericList,
      genericTree,
      genericParentOptions,
      genericGet,
      genericBreadcrumb,
      genericCreate,
      genericUpdate,
      genericPatch,
      genericDelete,
      genericMove,
      genericReorder,
      genericActivate,
      genericDeactivate
    ).forEach(GenericAdminCategoryOpenApiDocumentationTest::assertSecured);

    List.of(
      genericGet,
      genericBreadcrumb,
      genericUpdate,
      genericPatch,
      genericDelete,
      genericMove,
      genericReorder,
      genericActivate,
      genericDeactivate
    ).forEach(operation -> assertIntegerParameter(operation, "id"));

    assertIntegerProperties(docs, "AdminCategoryDetailResponse", "id", "parentId", "sortRank");
    assertIntegerProperties(docs, "AdminCategoryListItemResponse", "id", "parentId", "sortRank");
    assertIntegerProperties(docs, "AdminCategoryTreeItemResponse", "id", "parentId", "sortRank");
    assertIntegerProperties(docs, "AdminCategoryParentOptionResponse", "id", "parentId");
    assertIntegerProperties(
      docs,
      "AdminCategoryBreadcrumbItemResponse",
      "id",
      "parentId",
      "sortRank"
    );
    assertIntegerProperties(docs, "AdminCategoryCreateRequest", "parentId", "beforeId", "afterId");
    assertIntegerProperties(docs, "AdminCategoryUpdateRequest", "parentId");
    assertIntegerProperties(docs, "AdminCategoryMoveRequest", "newParentId", "beforeId", "afterId");
    assertIntegerProperties(docs, "AdminCategoryReorderRequest", "parentId", "beforeId", "afterId");

    assertErrorResponse(genericList, "400", "INVALID_SORT_FIELD", "VALIDATION_FAILED");
    assertErrorResponse(genericList, "404", "UNKNOWN_CATEGORY_SYSTEM", "CATEGORY_PARENT_NOT_FOUND");
    assertUnauthorizedResponses(genericList);

    List.of(
      genericCapabilities,
      genericList,
      genericTree,
      genericParentOptions,
      genericGet,
      genericBreadcrumb,
      genericCreate,
      genericUpdate,
      genericPatch,
      genericDelete,
      genericMove,
      genericReorder,
      genericActivate,
      genericDeactivate
    ).forEach(operation -> {
      assertErrorResponse(operation, "409", "CATEGORY_CAPABILITY_UNSUPPORTED");
      assertThat(operation.path("responses").has("500")).isFalse();
    });

    assertCapabilitySchema(docs);

    assertErrorResponse(genericTree, "409", "CATEGORY_TREE_TOO_LARGE");
    assertErrorResponse(genericParentOptions, "400", "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID");
    assertErrorResponse(genericCreate, "404", "CATEGORY_PARENT_NOT_FOUND");
    assertErrorResponse(genericCreate, "409", "DUPLICATE_CATEGORY_SLUG");
    assertErrorResponse(genericDelete, "409", "CATEGORY_DELETE_BLOCKED");
    assertErrorResponse(
      genericMove,
      "400",
      "INVALID_CATEGORY_MOVE",
      "INVALID_CATEGORY_PLACEMENT_TARGET"
    );
  }

  @Test
  void generatedOpenApiRetainsExactCatalogueOverlapAndNoHistoricalAdminRouteFamily()
    throws Exception {
    JsonNode docs = JSON.readTree(
      mvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString()
    );

    List<DocumentedOperation> genericOperations = documentedOperationsUnder(docs, GENERIC_PREFIX);
    List<DocumentedOperation> catalogueOperations = documentedOperationsUnder(
      docs,
      CATALOGUE_PREFIX
    );

    assertThat(genericOperations).containsExactlyInAnyOrderElementsOf(EXPECTED_OPERATIONS);
    assertThat(catalogueOperations)
      .hasSize(9)
      .containsExactlyInAnyOrderElementsOf(EXPECTED_CATALOGUE_OVERLAP_OPERATIONS);
    assertThat(documentedOperationsUnder(docs, CATALOGUE_PRODUCT_LISTING)).containsExactly(
      new DocumentedOperation(CATALOGUE_PRODUCT_LISTING, "get")
    );
    assertThat(
      translatedGenericOverlaps(genericOperations, catalogueOperations)
    ).containsExactlyInAnyOrderElementsOf(
      EXPECTED_CATALOGUE_OVERLAP_OPERATIONS.stream()
        .map(operation ->
          new DocumentedOperation(
            GENERIC_PREFIX + operation.path().substring(CATALOGUE_PREFIX.length()),
            operation.method()
          )
        )
        .toList()
    );
    assertThat(pathNames(docs)).noneMatch(path -> isExactPathOrDescendant(path, REMOVED_PREFIX));
  }

  private static JsonNode operation(JsonNode docs, String path, String method) {
    JsonNode operation = docs.path("paths").path(path).path(method);
    assertThat(operation.isMissingNode())
      .as(path + " " + method)
      .isFalse();
    return operation;
  }

  private static List<DocumentedOperation> documentedOperationsUnder(JsonNode docs, String prefix) {
    List<DocumentedOperation> operations = new ArrayList<>();
    docs
      .path("paths")
      .fields()
      .forEachRemaining(pathEntry -> {
        if (!isExactPathOrDescendant(pathEntry.getKey(), prefix)) {
          return;
        }
        pathEntry
          .getValue()
          .fieldNames()
          .forEachRemaining(method -> {
            if (HTTP_METHODS.contains(method)) {
              operations.add(new DocumentedOperation(pathEntry.getKey(), method));
            }
          });
      });
    return operations;
  }

  private static List<String> pathNames(JsonNode docs) {
    List<String> names = new ArrayList<>();
    docs.path("paths").fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static List<String> schemaNames(JsonNode docs) {
    List<String> names = new ArrayList<>();
    docs.path("components").path("schemas").fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static boolean isExactPathOrDescendant(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

  private static List<DocumentedOperation> translatedGenericOverlaps(
    List<DocumentedOperation> genericOperations,
    List<DocumentedOperation> catalogueOperations
  ) {
    return genericOperations
      .stream()
      .filter(operation -> operation.path().startsWith(GENERIC_PREFIX))
      .filter(operation ->
        catalogueOperations.contains(
          new DocumentedOperation(
            CATALOGUE_PREFIX + operation.path().substring(GENERIC_PREFIX.length()),
            operation.method()
          )
        )
      )
      .toList();
  }

  private static void assertIntegerParameter(JsonNode operation, String parameterName) {
    JsonNode parameter = findParameter(operation, parameterName);
    assertIntegerSchema(parameter.path("schema"), "parameter " + parameterName);
  }

  private static JsonNode findParameter(JsonNode operation, String parameterName) {
    for (JsonNode parameter : operation.path("parameters")) {
      if (parameterName.equals(parameter.path("name").asText())) {
        return parameter;
      }
    }
    throw new AssertionError("Missing OpenAPI parameter: " + parameterName);
  }

  private static void assertIntegerProperties(
    JsonNode docs,
    String schemaName,
    String... propertyNames
  ) {
    JsonNode schema = docs.path("components").path("schemas").path(schemaName);
    assertThat(schema.isMissingNode())
      .as(schemaName + " schema")
      .isFalse();
    for (String propertyName : propertyNames) {
      assertIntegerSchema(
        schema.path("properties").path(propertyName),
        schemaName + "." + propertyName
      );
    }
  }

  private static void assertIntegerSchema(JsonNode schema, String description) {
    assertThat(schema.isMissingNode()).as(description).isFalse();
    assertThat(schema.path("type").asText())
      .as(description + " type")
      .isEqualTo("integer");
    assertThat(schema.path("format").asText())
      .as(description + " format")
      .isEqualTo("int64");
  }

  private static JsonNode successSchema(JsonNode operation) {
    JsonNode content = operation.path("responses").path("200").path("content");
    assertThat(content.isObject()).isTrue();

    Iterator<Map.Entry<String, JsonNode>> fields = content.fields();
    assertThat(fields.hasNext()).isTrue();
    JsonNode schema = fields.next().getValue().path("schema");

    assertThat(schema.isMissingNode()).isFalse();
    return schema;
  }

  private static List<String> parameterNames(JsonNode operation) {
    List<String> names = new ArrayList<>();
    operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
    return names;
  }

  private static void assertBearerSecurityScheme(JsonNode docs) {
    JsonNode scheme = docs.path("components").path("securitySchemes").path("bearerAuth");
    assertThat(scheme.path("type").asText()).isEqualTo("http");
    assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
    assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");
  }

  private static void assertSecured(JsonNode operation) {
    assertThat(operation.path("security").toString()).contains("bearerAuth");
  }

  private static void assertUnauthorizedResponses(JsonNode operation) {
    assertThat(operation.path("responses").path("401").path("description").asText()).contains(
      "Authentication"
    );
    assertThat(operation.path("responses").path("403").path("description").asText()).contains(
      "permission"
    );
  }

  private static void assertErrorResponse(
    JsonNode operation,
    String responseCode,
    String... errorCodes
  ) {
    JsonNode response = operation.path("responses").path(responseCode);
    assertThat(response.isMissingNode())
      .as(responseCode + " response")
      .isFalse();
    assertThat(response.toString()).contains("ApiErrorResponse");
    for (String errorCode : errorCodes) {
      assertThat(response.path("description").asText()).contains(errorCode);
    }
  }

  private static void assertCapabilitySchema(JsonNode docs) {
    JsonNode schema = docs
      .path("components")
      .path("schemas")
      .path("AdminCategoryCapabilityResponse")
      .path("properties");
    List<String> properties = new ArrayList<>();
    schema.fieldNames().forEachRemaining(properties::add);
    assertThat(properties).containsExactlyInAnyOrder(
      "rankingEnabled",
      "placementModes",
      "sortRankExposed",
      "fallbackOrder",
      "supportedOperations",
      "hierarchy",
      "limits",
      "lifecycle",
      "identifierProfile",
      "fields",
      "searchableFields",
      "allowedFilters",
      "allowedSorts",
      "ordering"
    );
  }

  private record DocumentedOperation(String path, String method) {}
}
