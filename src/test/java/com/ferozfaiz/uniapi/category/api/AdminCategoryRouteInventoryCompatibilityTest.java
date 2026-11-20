package com.ferozfaiz.uniapi.category.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.catalogue.api.CatalogueCategoryController;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@WebMvcTest({ AdminCategoryController.class, CatalogueCategoryController.class })
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration({
  SpringDocConfiguration.class,
  SpringDocConfigProperties.class,
  SpringDocWebMvcConfiguration.class,
})
@TestPropertySource(properties = "spring.data.rest.base-path=/api/v1")
class AdminCategoryRouteInventoryCompatibilityTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String GENERIC_PREFIX =
    "/api/v1/admin/category-systems/{systemKey}/categories";
  private static final String CATALOGUE_PREFIX = "/api/v1/catalogue/categories";
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

  private static final List<MvcOperation> GENERIC_ONLY_OPERATIONS = List.of(
    generic(RequestMethod.GET, GENERIC_PREFIX, "listCategories"),
    generic(RequestMethod.GET, GENERIC_PREFIX + "/tree", "listCategoryTree"),
    generic(RequestMethod.GET, GENERIC_PREFIX + "/parent-options", "listParentOptions"),
    generic(RequestMethod.GET, GENERIC_PREFIX + "/{id}", "getCategory"),
    generic(RequestMethod.GET, GENERIC_PREFIX + "/{id}/breadcrumb", "getCategoryBreadcrumb")
  );

  private static final List<RoutePair> CATALOGUE_OVERLAPS = List.of(
    pair(
      "capabilities",
      RequestMethod.GET,
      GENERIC_PREFIX + "/capabilities",
      "capabilities",
      CATALOGUE_PREFIX + "/capabilities",
      "capabilities"
    ),
    pair(
      "create",
      RequestMethod.POST,
      GENERIC_PREFIX,
      "createCategory",
      CATALOGUE_PREFIX,
      "createCategory"
    ),
    pair(
      "replace",
      RequestMethod.PUT,
      GENERIC_PREFIX + "/{id}",
      "updateCategory",
      CATALOGUE_PREFIX + "/{id}",
      "updateCategory"
    ),
    pair(
      "patch",
      RequestMethod.PATCH,
      GENERIC_PREFIX + "/{id}",
      "patchCategory",
      CATALOGUE_PREFIX + "/{id}",
      "patchCategory"
    ),
    pair(
      "delete",
      RequestMethod.DELETE,
      GENERIC_PREFIX + "/{id}",
      "deleteCategory",
      CATALOGUE_PREFIX + "/{id}",
      "deleteCategory"
    ),
    pair(
      "move",
      RequestMethod.POST,
      GENERIC_PREFIX + "/{id}/move",
      "moveCategory",
      CATALOGUE_PREFIX + "/{id}/move",
      "moveCategory"
    ),
    pair(
      "reorder",
      RequestMethod.POST,
      GENERIC_PREFIX + "/{id}/reorder",
      "reorderCategory",
      CATALOGUE_PREFIX + "/{id}/reorder",
      "reorderCategory"
    ),
    pair(
      "activate",
      RequestMethod.POST,
      GENERIC_PREFIX + "/{id}/activate",
      "activateCategory",
      CATALOGUE_PREFIX + "/{id}/activate",
      "activateCategory"
    ),
    pair(
      "deactivate",
      RequestMethod.POST,
      GENERIC_PREFIX + "/{id}/deactivate",
      "deactivateCategory",
      CATALOGUE_PREFIX + "/{id}/deactivate",
      "deactivateCategory"
    )
  );

  private static final MvcOperation CATALOGUE_PRODUCT_LISTING = new MvcOperation(
    RequestMethod.GET,
    "/api/v1/categories/{slug}/products",
    CatalogueCategoryController.class,
    "listCategoryProducts"
  );
  private static final List<MvcOperation> EXPECTED_GENERIC_MVC = Stream.concat(
    GENERIC_ONLY_OPERATIONS.stream(),
    CATALOGUE_OVERLAPS.stream().map(RoutePair::generic)
  ).toList();
  private static final List<MvcOperation> EXPECTED_CATALOGUE_MVC = Stream.concat(
    CATALOGUE_OVERLAPS.stream().map(RoutePair::catalogue),
    Stream.of(CATALOGUE_PRODUCT_LISTING)
  ).toList();
  private static final List<HttpOperation> EXPECTED_GENERIC_OPENAPI = List.of(
    new HttpOperation(RequestMethod.GET, GENERIC_PREFIX),
    new HttpOperation(RequestMethod.GET, GENERIC_PREFIX + "/capabilities"),
    new HttpOperation(RequestMethod.GET, GENERIC_PREFIX + "/tree"),
    new HttpOperation(RequestMethod.GET, GENERIC_PREFIX + "/parent-options"),
    new HttpOperation(RequestMethod.GET, GENERIC_PREFIX + "/{id}"),
    new HttpOperation(RequestMethod.GET, GENERIC_PREFIX + "/{id}/breadcrumb"),
    new HttpOperation(RequestMethod.POST, GENERIC_PREFIX),
    new HttpOperation(RequestMethod.PUT, GENERIC_PREFIX + "/{id}"),
    new HttpOperation(RequestMethod.PATCH, GENERIC_PREFIX + "/{id}"),
    new HttpOperation(RequestMethod.DELETE, GENERIC_PREFIX + "/{id}"),
    new HttpOperation(RequestMethod.POST, GENERIC_PREFIX + "/{id}/move"),
    new HttpOperation(RequestMethod.POST, GENERIC_PREFIX + "/{id}/reorder"),
    new HttpOperation(RequestMethod.POST, GENERIC_PREFIX + "/{id}/activate"),
    new HttpOperation(RequestMethod.POST, GENERIC_PREFIX + "/{id}/deactivate")
  );
  private static final List<HttpOperation> EXPECTED_CATALOGUE_OVERLAP_OPENAPI = List.of(
    new HttpOperation(RequestMethod.GET, CATALOGUE_PREFIX + "/capabilities"),
    new HttpOperation(RequestMethod.POST, CATALOGUE_PREFIX),
    new HttpOperation(RequestMethod.PUT, CATALOGUE_PREFIX + "/{id}"),
    new HttpOperation(RequestMethod.PATCH, CATALOGUE_PREFIX + "/{id}"),
    new HttpOperation(RequestMethod.DELETE, CATALOGUE_PREFIX + "/{id}"),
    new HttpOperation(RequestMethod.POST, CATALOGUE_PREFIX + "/{id}/move"),
    new HttpOperation(RequestMethod.POST, CATALOGUE_PREFIX + "/{id}/reorder"),
    new HttpOperation(RequestMethod.POST, CATALOGUE_PREFIX + "/{id}/activate"),
    new HttpOperation(RequestMethod.POST, CATALOGUE_PREFIX + "/{id}/deactivate")
  );
  private static final List<String> ABSENT_HISTORICAL_ROUTE_FAMILY = List.of(
    REMOVED_PREFIX,
    REMOVED_PREFIX + "/capabilities",
    REMOVED_PREFIX + "/tree",
    REMOVED_PREFIX + "/parent-options",
    REMOVED_PREFIX + "/{id}",
    REMOVED_PREFIX + "/{id}/breadcrumb",
    REMOVED_PREFIX + "/{id}/move",
    REMOVED_PREFIX + "/{id}/reorder",
    REMOVED_PREFIX + "/{id}/activate",
    REMOVED_PREFIX + "/{id}/deactivate"
  );

  @Autowired
  private MockMvc mvc;

  @Autowired
  private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean
  private AdminCategoryApplicationService adminCategories;

  @MockitoBean
  private CatalogueQueryService catalogueQueries;

  @MockitoBean
  private CatalogueCategoryApplicationService catalogueCategories;

  @MockitoBean
  private CatalogueListPolicy catalogueListPolicy;

  @MockitoBean
  private CategorySystemRegistry categorySystems;

  @MockitoBean
  private MessageResolver messageResolver;

  @Test
  void mvcRetainsFourteenGenericOperationsAndNineIndependentCataloguePairs() {
    assertThat(CATALOGUE_OVERLAPS)
      .extracting(RoutePair::operation)
      .containsExactly(
        "capabilities",
        "create",
        "replace",
        "patch",
        "delete",
        "move",
        "reorder",
        "activate",
        "deactivate"
      );
    assertThat(
      mvcOperationsHandledBy(AdminCategoryController.class)
    ).containsExactlyInAnyOrderElementsOf(EXPECTED_GENERIC_MVC);
    assertThat(
      mvcOperationsHandledBy(CatalogueCategoryController.class)
    ).containsExactlyInAnyOrderElementsOf(EXPECTED_CATALOGUE_MVC);

    List<String> paths = mvcPaths();
    assertThat(paths).doesNotContain(REMOVED_PREFIX);
    assertThat(paths).noneMatch(path -> path.startsWith(REMOVED_PREFIX + "/"));
  }

  @Test
  void generatedOpenApiRetainsFourteenGenericOperationsAndNineCataloguePairs() throws Exception {
    JsonNode docs = generatedOpenApi();

    assertThat(documentedOperationsUnder(docs, GENERIC_PREFIX)).containsExactlyInAnyOrderElementsOf(
      EXPECTED_GENERIC_OPENAPI
    );
    assertThat(
      documentedOperationsUnder(docs, CATALOGUE_PREFIX)
    ).containsExactlyInAnyOrderElementsOf(EXPECTED_CATALOGUE_OVERLAP_OPENAPI);
    assertThat(documentedOperationsUnder(docs, CATALOGUE_PRODUCT_LISTING.path())).containsExactly(
      CATALOGUE_PRODUCT_LISTING.httpOperation()
    );

    List<String> paths = pathNames(docs);
    assertThat(paths).doesNotContain(REMOVED_PREFIX);
    assertThat(paths).noneMatch(path -> path.startsWith(REMOVED_PREFIX + "/"));
  }

  @Test
  void mvcOverlapIsExactlyCapabilitiesAndEightCatalogueMutations() {
    List<HttpOperation> genericOperations = mvcOperationsHandledBy(AdminCategoryController.class)
      .stream()
      .map(MvcOperation::httpOperation)
      .toList();
    List<HttpOperation> catalogueOperations = mvcOperationsHandledBy(
      CatalogueCategoryController.class
    )
      .stream()
      .map(MvcOperation::httpOperation)
      .toList();

    assertThat(translatedGenericOverlaps(genericOperations, catalogueOperations))
      .hasSize(9)
      .containsExactlyInAnyOrderElementsOf(
        CATALOGUE_OVERLAPS.stream()
          .map(RoutePair::generic)
          .map(MvcOperation::httpOperation)
          .toList()
      );
  }

  @Test
  void mvcAndGeneratedOpenApiKeepEveryHistoricalAdminCategoryRouteAbsent() throws Exception {
    List<String> mvcPaths = mvcPaths();
    List<String> openApiPaths = pathNames(generatedOpenApi());

    assertThat(mvcPaths).doesNotContainAnyElementsOf(ABSENT_HISTORICAL_ROUTE_FAMILY);
    assertThat(openApiPaths).doesNotContainAnyElementsOf(ABSENT_HISTORICAL_ROUTE_FAMILY);
    assertThat(mvcPaths).noneMatch(path -> isExactPathOrDescendant(path, REMOVED_PREFIX));
    assertThat(openApiPaths).noneMatch(path -> isExactPathOrDescendant(path, REMOVED_PREFIX));
  }

  private JsonNode generatedOpenApi() throws Exception {
    String openApi = mvc
      .perform(get("/v3/api-docs"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    return JSON.readTree(openApi);
  }

  private List<MvcOperation> mvcOperationsHandledBy(Class<?> controllerType) {
    return handlerMapping
      .getHandlerMethods()
      .entrySet()
      .stream()
      .filter(entry -> entry.getValue().getBeanType() == controllerType)
      .flatMap(entry -> mvcOperations(entry.getKey(), entry.getValue()).stream())
      .toList();
  }

  private List<String> mvcPaths() {
    return handlerMapping
      .getHandlerMethods()
      .keySet()
      .stream()
      .flatMap(mapping -> paths(mapping).stream())
      .toList();
  }

  private static List<MvcOperation> mvcOperations(
    RequestMappingInfo mapping,
    HandlerMethod handler
  ) {
    return paths(mapping)
      .stream()
      .flatMap(path ->
        mapping
          .getMethodsCondition()
          .getMethods()
          .stream()
          .map(method ->
            new MvcOperation(method, path, handler.getBeanType(), handler.getMethod().getName())
          )
      )
      .toList();
  }

  private static Set<String> paths(RequestMappingInfo mapping) {
    if (mapping.getPathPatternsCondition() != null) {
      return mapping.getPathPatternsCondition().getPatternValues();
    }
    if (mapping.getPatternsCondition() != null) {
      return mapping.getPatternsCondition().getPatterns();
    }
    return Set.of();
  }

  private static List<HttpOperation> documentedOperationsUnder(JsonNode docs, String prefix) {
    List<HttpOperation> operations = new ArrayList<>();
    docs
      .path("paths")
      .properties()
      .forEach(path -> {
        if (!isExactPathOrDescendant(path.getKey(), prefix)) {
          return;
        }
        for (Map.Entry<String, JsonNode> methodEntry : path.getValue().properties()) {
          String method = methodEntry.getKey();
          if (HTTP_METHODS.contains(method)) {
            operations.add(
              new HttpOperation(
                RequestMethod.valueOf(method.toUpperCase(Locale.ROOT)),
                path.getKey()
              )
            );
          }
        }
      });
    return operations;
  }

  private static List<String> pathNames(JsonNode docs) {
    return docs.path("paths").properties().stream().map(Map.Entry::getKey).toList();
  }

  private static boolean isExactPathOrDescendant(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

  private static List<HttpOperation> translatedGenericOverlaps(
    List<HttpOperation> genericOperations,
    List<HttpOperation> catalogueOperations
  ) {
    return genericOperations
      .stream()
      .filter(operation -> operation.path().startsWith(GENERIC_PREFIX))
      .filter(operation ->
        catalogueOperations.contains(
          new HttpOperation(
            operation.method(),
            CATALOGUE_PREFIX + operation.path().substring(GENERIC_PREFIX.length())
          )
        )
      )
      .toList();
  }

  private static MvcOperation generic(RequestMethod method, String path, String handlerMethod) {
    return new MvcOperation(method, path, AdminCategoryController.class, handlerMethod);
  }

  private static RoutePair pair(
    String operation,
    RequestMethod method,
    String genericPath,
    String genericHandlerMethod,
    String cataloguePath,
    String catalogueHandlerMethod
  ) {
    return new RoutePair(
      operation,
      generic(method, genericPath, genericHandlerMethod),
      new MvcOperation(
        method,
        cataloguePath,
        CatalogueCategoryController.class,
        catalogueHandlerMethod
      )
    );
  }

  private record HttpOperation(RequestMethod method, String path) {}

  private record MvcOperation(
    RequestMethod method,
    String path,
    Class<?> handlerType,
    String handlerMethod
  ) {
    HttpOperation httpOperation() {
      return new HttpOperation(method, path);
    }
  }

  private record RoutePair(String operation, MvcOperation generic, MvcOperation catalogue) {}
}
