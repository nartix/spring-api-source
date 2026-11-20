package com.ferozfaiz.uniapi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.api.CatalogueCategoryController;
import com.ferozfaiz.uniapi.catalogue.api.CatalogueController;
import com.ferozfaiz.uniapi.catalogue.api.CatalogueReferenceDataController;
import com.ferozfaiz.uniapi.catalogue.api.authorization.RequireCatalogueRead;
import com.ferozfaiz.uniapi.catalogue.api.authorization.RequireCatalogueWrite;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueReferenceDataApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.category.api.AdminCategoryController;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryActivate;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryBreadcrumb;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryCapabilities;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryCreate;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryDeactivate;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryDelete;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryGet;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryList;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryMove;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryParentOptions;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryPatch;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryReorder;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryReplace;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategorySystemRead;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategorySystemWrite;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryTree;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.authorization.AdminCategorySystemAuthorizationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryTreePolicy;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.data.api.CategoryController;
import com.ferozfaiz.uniapi.data.api.authorization.RequireCategoriesRead;
import com.ferozfaiz.uniapi.data.api.authorization.RequireCategoriesWrite;
import com.ferozfaiz.uniapi.data.application.CategoryApplicationService;
import com.ferozfaiz.uniapi.iam.api.AdminUserController;
import com.ferozfaiz.uniapi.iam.api.UserController;
import com.ferozfaiz.uniapi.iam.api.auth.AdminSessionAuthController;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthorityLookup;
import com.ferozfaiz.uniapi.iam.api.auth.RegularSessionAuthController;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireAdminAccess;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireAdminUsersRead;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireAdminUsersStatus;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireAdminUsersWrite;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireUsersRead;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireUsersWrite;
import com.ferozfaiz.uniapi.iam.application.AdminUserApplicationService;
import com.ferozfaiz.uniapi.iam.application.UserApplicationService;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@WebMvcTest({
  UserController.class,
  CategoryController.class,
  CatalogueController.class,
  CatalogueCategoryController.class,
  CatalogueReferenceDataController.class,
  AdminCategoryController.class,
  AdminUserController.class,
  RegularSessionAuthController.class,
  AdminSessionAuthController.class,
})
@Import({ AdminCategorySystemAuthorizationPolicy.class, AdminCategoryTreePolicy.class })
@TestPropertySource(properties = "spring.data.rest.base-path=/api/v1")
class AuthorizationCoverageTest {

  private static final String API_BASE_PATH = "/api/v1";
  private static final String ADMIN_CATEGORY_BASE_PATH =
    API_BASE_PATH + "/admin/category-systems/{systemKey}/categories";
  private static final String REMOVED_ADMIN_CATEGORY_PATH = API_BASE_PATH + "/admin/categories";
  private static final List<EndpointMapping> ADMIN_CATEGORY_ENDPOINTS = List.of(
    new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH),
    new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/capabilities"),
    new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/tree"),
    new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/parent-options"),
    new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/{id}"),
    new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/{id}/breadcrumb"),
    new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH),
    new EndpointMapping(RequestMethod.PUT, ADMIN_CATEGORY_BASE_PATH + "/{id}"),
    new EndpointMapping(RequestMethod.PATCH, ADMIN_CATEGORY_BASE_PATH + "/{id}"),
    new EndpointMapping(RequestMethod.DELETE, ADMIN_CATEGORY_BASE_PATH + "/{id}"),
    new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH + "/{id}/move"),
    new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH + "/{id}/reorder"),
    new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH + "/{id}/activate"),
    new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH + "/{id}/deactivate")
  );
  private static final Map<
    EndpointMapping,
    Class<? extends Annotation>
  > ADMIN_CATEGORY_AUTHORIZATION = Map.ofEntries(
    Map.entry(
      new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/capabilities"),
      RequireAdminCategoryCapabilities.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH),
      RequireAdminCategoryList.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/tree"),
      RequireAdminCategoryTree.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/parent-options"),
      RequireAdminCategoryParentOptions.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/{id}"),
      RequireAdminCategoryGet.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.GET, ADMIN_CATEGORY_BASE_PATH + "/{id}/breadcrumb"),
      RequireAdminCategoryBreadcrumb.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH),
      RequireAdminCategoryCreate.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.PUT, ADMIN_CATEGORY_BASE_PATH + "/{id}"),
      RequireAdminCategoryReplace.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.PATCH, ADMIN_CATEGORY_BASE_PATH + "/{id}"),
      RequireAdminCategoryPatch.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.DELETE, ADMIN_CATEGORY_BASE_PATH + "/{id}"),
      RequireAdminCategoryDelete.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH + "/{id}/move"),
      RequireAdminCategoryMove.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH + "/{id}/reorder"),
      RequireAdminCategoryReorder.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH + "/{id}/activate"),
      RequireAdminCategoryActivate.class
    ),
    Map.entry(
      new EndpointMapping(RequestMethod.POST, ADMIN_CATEGORY_BASE_PATH + "/{id}/deactivate"),
      RequireAdminCategoryDeactivate.class
    )
  );
  private static final Map<
    Class<? extends Annotation>,
    Operation
  > ADMIN_CATEGORY_COMPOSED_AUTHORIZATION = Map.ofEntries(
    Map.entry(RequireAdminCategoryCapabilities.class, Operation.CAPABILITIES),
    Map.entry(RequireAdminCategoryList.class, Operation.LIST),
    Map.entry(RequireAdminCategoryTree.class, Operation.TREE),
    Map.entry(RequireAdminCategoryParentOptions.class, Operation.PARENT_OPTIONS),
    Map.entry(RequireAdminCategoryGet.class, Operation.GET),
    Map.entry(RequireAdminCategoryBreadcrumb.class, Operation.BREADCRUMB),
    Map.entry(RequireAdminCategoryCreate.class, Operation.CREATE),
    Map.entry(RequireAdminCategoryReplace.class, Operation.REPLACE),
    Map.entry(RequireAdminCategoryPatch.class, Operation.PATCH),
    Map.entry(RequireAdminCategoryDelete.class, Operation.DELETE),
    Map.entry(RequireAdminCategoryMove.class, Operation.MOVE),
    Map.entry(RequireAdminCategoryReorder.class, Operation.REORDER),
    Map.entry(RequireAdminCategoryActivate.class, Operation.ACTIVATE),
    Map.entry(RequireAdminCategoryDeactivate.class, Operation.DEACTIVATE)
  );
  private static final List<PublicEndpoint> BUSINESS_PUBLIC_ALLOW_LIST = List.of(
    new PublicEndpoint(
      RequestMethod.GET,
      "/api/v1/auth/csrf",
      "Regular browser clients need a CSRF token before login.",
      "docs/api/http/iam/session-auth.http",
      "RegularSessionAuthControllerTest"
    ),
    new PublicEndpoint(
      RequestMethod.GET,
      "/api/v1/admin/auth/csrf",
      "Admin browser clients need a CSRF token before login.",
      "docs/api/http/iam/session-auth.http",
      "AdminSessionSecurityTest"
    )
  );
  private static final Set<Class<? extends Annotation>> READ_ANNOTATIONS = Set.of(
    RequireAdminAccess.class,
    RequireAdminUsersRead.class,
    RequireAdminCategorySystemRead.class,
    RequireAdminCategoryCapabilities.class,
    RequireAdminCategoryList.class,
    RequireAdminCategoryTree.class,
    RequireAdminCategoryParentOptions.class,
    RequireAdminCategoryGet.class,
    RequireAdminCategoryBreadcrumb.class,
    RequireUsersRead.class,
    RequireCategoriesRead.class,
    RequireCatalogueRead.class
  );
  private static final Set<Class<? extends Annotation>> WRITE_ANNOTATIONS = Set.of(
    RequireAdminAccess.class,
    RequireAdminUsersStatus.class,
    RequireAdminUsersWrite.class,
    RequireAdminCategorySystemWrite.class,
    RequireAdminCategoryCreate.class,
    RequireAdminCategoryReplace.class,
    RequireAdminCategoryPatch.class,
    RequireAdminCategoryDelete.class,
    RequireAdminCategoryMove.class,
    RequireAdminCategoryReorder.class,
    RequireAdminCategoryActivate.class,
    RequireAdminCategoryDeactivate.class,
    RequireUsersWrite.class,
    RequireCategoriesWrite.class,
    RequireCatalogueWrite.class
  );

  @Autowired
  private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean
  private UserApplicationService users;

  @MockitoBean
  private AdminUserApplicationService adminUsers;

  @MockitoBean
  private IdentityAuthorityLookup authorityLookup;

  @MockitoBean
  private CategoryApplicationService categories;

  @MockitoBean
  private CatalogueQueryService catalogueQueries;

  @MockitoBean
  private ProductApplicationService products;

  @MockitoBean
  private CatalogueCategoryApplicationService catalogueCategories;

  @MockitoBean
  private AdminCategoryApplicationService adminCategories;

  @MockitoBean
  private CatalogueReferenceDataApplicationService referenceData;

  @MockitoBean
  private CatalogueListPolicy catalogueListPolicy;

  @MockitoBean
  private MessageResolver messageResolver;

  @MockitoBean
  private CategorySystemRegistry categorySystemRegistry;

  @MockitoBean
  private AdminCategorySystemRegistry adminCategorySystemRegistry;

  @Test
  void protectedApiHandlersDeclareApprovedAuthorization() {
    List<String> violations = handlerMapping
      .getHandlerMethods()
      .entrySet()
      .stream()
      .flatMap(entry -> endpoints(entry.getKey(), entry.getValue()).stream())
      .filter(endpoint -> !endpoint.isPublic())
      .filter(endpoint -> !endpoint.isRegularSessionInfrastructure())
      .filter(endpoint -> !endpoint.hasExpectedAuthorization())
      .map(ProtectedEndpoint::description)
      .sorted()
      .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  void publicBusinessEndpointAllowListRequiresDocumentation() {
    assertThat(BUSINESS_PUBLIC_ALLOW_LIST).isNotEmpty();
    assertThat(BUSINESS_PUBLIC_ALLOW_LIST).allSatisfy(endpoint -> {
      assertThat(endpoint.method()).isNotNull();
      assertThat(endpoint.path()).startsWith(API_BASE_PATH);
      assertThat(endpoint.reason()).isNotBlank();
      assertThat(endpoint.documentation()).isNotBlank();
      assertThat(endpoint.testCoverage()).isNotBlank();
    });
  }

  @Test
  void selectedSystemAdminCategoryMappingsExposeExactlyFourteenAuthorizedOperations() {
    List<ProtectedEndpoint> endpoints = handlerMapping
      .getHandlerMethods()
      .entrySet()
      .stream()
      .filter(entry -> entry.getValue().getBeanType() == AdminCategoryController.class)
      .flatMap(entry -> endpoints(entry.getKey(), entry.getValue()).stream())
      .toList();

    assertThat(endpoints).allMatch(AuthorizationCoverageTest::hasAdminCategorySystemAuthorization);
    assertThat(endpoints)
      .map(endpoint -> new EndpointMapping(endpoint.method(), endpoint.path()))
      .containsExactlyInAnyOrderElementsOf(ADMIN_CATEGORY_ENDPOINTS);
  }

  @Test
  void selectedSystemComposedAnnotationsRemainExactAndOperationSpecific() {
    assertThat(ADMIN_CATEGORY_AUTHORIZATION).hasSize(14);
    assertThat(ADMIN_CATEGORY_AUTHORIZATION.values()).containsExactlyInAnyOrderElementsOf(
      ADMIN_CATEGORY_COMPOSED_AUTHORIZATION.keySet()
    );
    assertThat(ADMIN_CATEGORY_COMPOSED_AUTHORIZATION)
      .hasSize(14)
      .allSatisfy((annotation, operation) -> {
        PreAuthorize preAuthorize = annotation.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).as(annotation.getSimpleName()).isNotNull();
        assertThat(preAuthorize.value())
          .as(annotation.getSimpleName())
          .contains(".canAccess(authentication,#systemKey,")
          .contains(".Operation)." + operation.name() + ")")
          .doesNotContain(".canRead(")
          .doesNotContain(".canWrite(");
      });
  }

  @Test
  void effectiveMappingsContainNeitherTheRemovedAdminCategoryPathNorDescendants() {
    List<String> forbiddenMappings = handlerMapping
      .getHandlerMethods()
      .keySet()
      .stream()
      .flatMap(mapping -> paths(mapping).stream())
      .filter(path -> isExactPathOrDescendant(path, REMOVED_ADMIN_CATEGORY_PATH))
      .sorted()
      .toList();

    assertThat(forbiddenMappings).isEmpty();
  }

  @Test
  void acceptsClassLevelAndMethodLevelAuthorizationDeclarations() throws Exception {
    assertThat(
      hasExpectedAuthorization(
        RequestMethod.GET,
        ClassLevelReadController.class,
        ClassLevelReadController.class.getDeclaredMethod("list")
      )
    ).isTrue();
    assertThat(
      hasExpectedAuthorization(
        RequestMethod.POST,
        MethodLevelWriteController.class,
        MethodLevelWriteController.class.getDeclaredMethod("create")
      )
    ).isTrue();
    assertThat(
      hasExpectedAuthorization(
        RequestMethod.GET,
        MissingAuthorizationController.class,
        MissingAuthorizationController.class.getDeclaredMethod("list")
      )
    ).isFalse();
  }

  @Test
  void httpMethodHeuristicSeparatesReadAndWriteAuthorization() throws Exception {
    Method write = MethodLevelWriteController.class.getDeclaredMethod("create");
    Method read = MethodLevelReadController.class.getDeclaredMethod("list");

    assertThat(
      hasExpectedAuthorization(RequestMethod.POST, MethodLevelWriteController.class, write)
    ).isTrue();
    assertThat(
      hasExpectedAuthorization(RequestMethod.GET, MethodLevelWriteController.class, write)
    ).isFalse();
    assertThat(
      hasExpectedAuthorization(RequestMethod.GET, MethodLevelReadController.class, read)
    ).isTrue();
    assertThat(
      hasExpectedAuthorization(RequestMethod.DELETE, MethodLevelReadController.class, read)
    ).isFalse();
  }

  private static List<ProtectedEndpoint> endpoints(
    RequestMappingInfo mappingInfo,
    HandlerMethod handlerMethod
  ) {
    if (!isProjectApiHandler(handlerMethod)) {
      return List.of();
    }
    Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
    return paths(mappingInfo)
      .stream()
      .filter(path -> path.startsWith(API_BASE_PATH))
      .flatMap(path ->
        methods.stream().map(method -> new ProtectedEndpoint(method, path, handlerMethod))
      )
      .sorted(Comparator.comparing(ProtectedEndpoint::description))
      .toList();
  }

  private static boolean isProjectApiHandler(HandlerMethod handlerMethod) {
    Package handlerPackage = handlerMethod.getBeanType().getPackage();
    return (
      handlerPackage != null &&
      handlerPackage.getName().startsWith("com.ferozfaiz.uniapi") &&
      (handlerPackage.getName().endsWith(".api") || handlerPackage.getName().endsWith(".api.auth"))
    );
  }

  private static Set<String> paths(RequestMappingInfo mappingInfo) {
    if (mappingInfo.getPathPatternsCondition() != null) {
      return mappingInfo.getPathPatternsCondition().getPatternValues();
    }
    if (mappingInfo.getPatternsCondition() != null) {
      return mappingInfo.getPatternsCondition().getPatterns();
    }
    return Set.of();
  }

  private static boolean isExactPathOrDescendant(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

  private static boolean hasExpectedAuthorization(
    RequestMethod requestMethod,
    Class<?> handlerType,
    Method handlerMethod
  ) {
    if (requestMethod == RequestMethod.GET || requestMethod == RequestMethod.HEAD) {
      return hasAuthorization(handlerType, handlerMethod, READ_ANNOTATIONS);
    }
    return hasAuthorization(handlerType, handlerMethod, WRITE_ANNOTATIONS);
  }

  private static boolean hasAdminCategorySystemAuthorization(ProtectedEndpoint endpoint) {
    Class<? extends Annotation> expected = ADMIN_CATEGORY_AUTHORIZATION.get(
      new EndpointMapping(endpoint.method(), endpoint.path())
    );
    return (
      expected != null &&
      (AnnotatedElementUtils.hasAnnotation(endpoint.handlerMethod().getMethod(), expected) ||
        AnnotatedElementUtils.hasAnnotation(endpoint.handlerMethod().getBeanType(), expected))
    );
  }

  private static boolean hasAuthorization(
    Class<?> handlerType,
    Method handlerMethod,
    Set<Class<? extends Annotation>> approvedAnnotations
  ) {
    return approvedAnnotations
      .stream()
      .anyMatch(
        annotation ->
          AnnotatedElementUtils.hasAnnotation(handlerMethod, annotation) ||
          AnnotatedElementUtils.hasAnnotation(handlerType, annotation)
      );
  }

  private record ProtectedEndpoint(RequestMethod method, String path, HandlerMethod handlerMethod) {
    boolean isPublic() {
      return BUSINESS_PUBLIC_ALLOW_LIST.stream().anyMatch(
        endpoint -> endpoint.method() == method && endpoint.path().equals(path)
      );
    }

    boolean isRegularSessionInfrastructure() {
      return path.startsWith(API_BASE_PATH + "/auth/");
    }

    boolean hasExpectedAuthorization() {
      return AuthorizationCoverageTest.hasExpectedAuthorization(
        method,
        handlerMethod.getBeanType(),
        handlerMethod.getMethod()
      );
    }

    String description() {
      return method + " " + path + " -> " + handlerMethod.getShortLogMessage();
    }
  }

  private record PublicEndpoint(
    RequestMethod method,
    String path,
    String reason,
    String documentation,
    String testCoverage
  ) {}

  private record EndpointMapping(RequestMethod method, String path) {}

  @RequireCatalogueRead
  private static class ClassLevelReadController {

    @GetMapping("/class-level-read")
    void list() {}
  }

  private static class MethodLevelReadController {

    @GetMapping("/method-level-read")
    @RequireCatalogueRead
    void list() {}
  }

  private static class MethodLevelWriteController {

    @PostMapping("/method-level-write")
    @RequireCatalogueWrite
    void create() {}
  }

  private static class MissingAuthorizationController {

    @GetMapping("/missing-authorization")
    void list() {}
  }
}
