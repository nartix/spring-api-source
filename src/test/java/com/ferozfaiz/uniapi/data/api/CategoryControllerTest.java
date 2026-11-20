package com.ferozfaiz.uniapi.data.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.data.application.CategoryApplicationService;
import com.ferozfaiz.uniapi.data.application.command.CreateCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.MoveCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.ReorderCategoryCommand;
import com.ferozfaiz.uniapi.data.application.query.CategoryTree;
import com.ferozfaiz.uniapi.data.application.query.ListCategoriesQuery;
import com.ferozfaiz.uniapi.data.domain.exception.CategoryHierarchyPolicyViolationException;
import com.ferozfaiz.uniapi.data.domain.exception.CategoryNotFoundException;
import com.ferozfaiz.uniapi.data.domain.exception.DuplicateCategoryException;
import com.ferozfaiz.uniapi.data.domain.model.Category;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveMode;
import com.ferozfaiz.uniapi.data.domain.model.CategoryNode;
import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedAuthorities;
import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedIdentity;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthenticator;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthorityLookup;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.DisabledRankingRequestPolicy;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.PlacementMode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankingCapabilityDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankTableDescriptor;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.authentication.IamAuthenticationProvider;
import com.ferozfaiz.uniapi.platform.security.authorizationserver.AuthorizationServerPersistenceConfiguration;
import com.ferozfaiz.uniapi.platform.security.authorizationserver.AuthorizationServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.authorizationserver.OAuthRegisteredClientSeeder;
import com.ferozfaiz.uniapi.platform.security.jwk.OAuthSigningKeyConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.web.BrowserLoginSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CategoryController.class)
@Import({
  SecurityConfiguration.class,
  AuthorizationServerSecurityConfiguration.class,
  AuthorizationServerPersistenceConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  BrowserLoginSecurityConfiguration.class,
  OAuthSigningKeyConfiguration.class,
  IamAuthenticationProvider.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  DataApiExceptionHandler.class,
})
class CategoryControllerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T06:00:00Z");

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CategoryApplicationService categories;

  @MockitoBean
  private CategorySystemRegistry categorySystems;

  @MockitoBean
  private IdentityAuthenticator authenticator;

  @MockitoBean
  private IdentityAuthorityLookup authorityLookup;

  @MockitoBean
  private JdbcOperations jdbcOperations;

  @MockitoBean
  private OAuthRegisteredClientSeeder oauthRegisteredClientSeeder;

  @BeforeEach
  void authenticateRequests() {
    when(authenticator.authenticate(any(), any())).thenReturn(
      new AuthenticatedIdentity(1L, "feroz", true, true, true, true)
    );
    when(authorityLookup.findAuthorities(any())).thenReturn(
      new AuthenticatedAuthorities(Set.of("ADMIN"), Set.of("categories:read", "categories:write"))
    );
  }

  @Test
  void categoryCapabilitiesExposeRankedOrderingMetadata() throws Exception {
    when(categorySystems.requireDescriptor("data")).thenReturn(
      rankedDescriptor("data", "data_category", "data_category_closure")
    );

    mvc
      .perform(get("/api/v1/categories/capabilities").with(authenticatedJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.rankingEnabled").value(true))
      .andExpect(jsonPath("$.placementModes[0]").value("APPEND"))
      .andExpect(jsonPath("$.placementModes[1]").value("BEFORE_ID"))
      .andExpect(jsonPath("$.sortRankExposed").value(true))
      .andExpect(jsonPath("$.fallbackOrder").isEmpty());
  }

  @Test
  void categoryCapabilitiesRequireReadAuthority() throws Exception {
    mvc.perform(get("/api/v1/categories/capabilities")).andExpect(status().isUnauthorized());

    mvc
      .perform(
        get("/api/v1/categories/capabilities").with(
          authorityJwt(CategoryAuthorities.CATEGORIES_WRITE)
        )
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void createCategoryCreatesRootCategory() throws Exception {
    when(categories.create(any(CreateCategoryCommand.class))).thenReturn(
      category(1L, null, "finance", "Finance")
    );

    mvc
      .perform(
        post("/api/v1/categories")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "slug": "finance",
              "name": "Finance",
              "description": "Finance data",
              "active": true
            }
            """
          )
      )
      .andExpect(status().isCreated())
      .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/v1/categories/1"))
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.parentId").doesNotExist())
      .andExpect(jsonPath("$.slug").value("finance"))
      .andExpect(jsonPath("$.name").value("Finance"))
      .andExpect(jsonPath("$.description").value("Finance data"))
      .andExpect(jsonPath("$.active").value(true));

    verify(categories).create(
      new CreateCategoryCommand(null, "finance", "Finance", "Finance data", true)
    );
  }

  @Test
  void createCategoryPassesPlacementFieldsAndReturnsSortRank() throws Exception {
    when(categories.create(any(CreateCategoryCommand.class))).thenReturn(
      category(1L, null, 1_500_000_000L, "finance", "Finance")
    );

    mvc
      .perform(
        post("/api/v1/categories")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "beforeId": 2,
              "slug": "finance",
              "name": "Finance"
            }
            """
          )
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.sortRank").value(1_500_000_000L));

    verify(categories).create(
      new CreateCategoryCommand(
        null,
        "finance",
        "Finance",
        null,
        null,
        SparseOrderTarget.from(2L, null, null)
      )
    );
  }

  @Test
  void createCategoryRejectsConflictingPlacementFieldsWithStableMessage() throws Exception {
    mvc
      .perform(
        post("/api/v1/categories")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "beforeId": 2,
              "afterId": 3,
              "slug": "finance",
              "name": "Finance"
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
      .andExpect(
        jsonPath("$.message").value(
          "Specify only one placement field: beforeId, afterId, or targetIndex"
        )
      );
  }

  @Test
  void createCategoryAllowsClientCredentialsWriteScope() throws Exception {
    when(categories.create(any(CreateCategoryCommand.class))).thenReturn(
      category(1L, null, "finance", "Finance")
    );

    mvc
      .perform(
        post("/api/v1/categories")
          .with(serviceScopeJwt(CategoryAuthorities.SCOPE_CATEGORIES_WRITE))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "slug": "finance",
              "name": "Finance"
            }
            """
          )
      )
      .andExpect(status().isCreated());
  }

  @Test
  void createCategoryRequiresAuthentication() throws Exception {
    mvc
      .perform(
        post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON).content(
          """
          {
            "slug": "finance",
            "name": "Finance"
          }
          """
        )
      )
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
      .andExpect(jsonPath("$.message").value("Authentication is required"))
      .andExpect(jsonPath("$.path").value("/api/v1/categories"));
  }

  @Test
  void createCategoryRejectsUnderprivilegedAuthenticatedPrincipal() throws Exception {
    mvc
      .perform(
        post("/api/v1/categories")
          .with(underprivilegedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "slug": "finance",
              "name": "Finance"
            }
            """
          )
      )
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"))
      .andExpect(jsonPath("$.message").value("Access is denied"))
      .andExpect(jsonPath("$.path").value("/api/v1/categories"));
  }

  @Test
  void createCategoryRejectsInvalidRequest() throws Exception {
    mvc
      .perform(
        post("/api/v1/categories")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "slug": "Invalid Slug",
              "name": ""
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.message").value("Request validation failed"))
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'slug')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists());
  }

  @Test
  void createCategoryMapsDuplicateSlugToConflict() throws Exception {
    when(categories.create(any(CreateCategoryCommand.class))).thenThrow(
      DuplicateCategoryException.slugAlreadyExistsInParent()
    );

    mvc
      .perform(
        post("/api/v1/categories")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "slug": "finance",
              "name": "Finance"
            }
            """
          )
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"))
      .andExpect(jsonPath("$.message").value("Category slug already exists in this parent"));
  }

  @Test
  void listRootsReturnsCategories() throws Exception {
    when(categories.findRoots()).thenReturn(List.of(category(1L, null, "finance", "Finance")));

    mvc
      .perform(get("/api/v1/categories/roots").with(authenticatedJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(1))
      .andExpect(jsonPath("$[0].slug").value("finance"));
  }

  @Test
  void getCategoryReturnsDepth() throws Exception {
    when(categories.findNodeById(2L)).thenReturn(
      new CategoryNode(category(2L, 1L, "reports", "Reports"), 1)
    );

    mvc
      .perform(get("/api/v1/categories/2").with(authenticatedJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(2))
      .andExpect(jsonPath("$.parentId").value(1))
      .andExpect(jsonPath("$.depth").value(1))
      .andExpect(jsonPath("$.slug").value("reports"));
  }

  @Test
  void listCategoriesReturnsFlatPagedCategories() throws Exception {
    when(categories.findAll(any(ListCategoriesQuery.class))).thenReturn(
      new PageResult<>(
        List.of(
          new CategoryNode(category(1L, null, "finance", "Finance"), 0),
          new CategoryNode(category(2L, 1L, "reports", "Reports"), 1)
        ),
        0,
        20,
        2,
        1,
        true,
        true,
        new SortCriteria(
          List.of(
            new SortOrder("depth", SortDirection.ASC),
            new SortOrder("name", SortDirection.ASC),
            new SortOrder("id", SortDirection.ASC)
          )
        )
      )
    );

    mvc
      .perform(
        get("/api/v1/categories")
          .with(authenticatedJwt())
          .param("page", "0")
          .param("size", "20")
          .param("sort", "depth,asc")
          .param("sort", "name,asc")
          .param("sort", "id,asc")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value(1))
      .andExpect(jsonPath("$.data[0].depth").value(0))
      .andExpect(jsonPath("$.data[1].id").value(2))
      .andExpect(jsonPath("$.data[1].parentId").value(1))
      .andExpect(jsonPath("$.data[1].depth").value(1))
      .andExpect(jsonPath("$.page.number").value(0))
      .andExpect(jsonPath("$.page.size").value(20))
      .andExpect(jsonPath("$.page.totalElements").value(2))
      .andExpect(jsonPath("$.sort[0].property").value("depth"))
      .andExpect(jsonPath("$.sort[0].direction").value("ASC"))
      .andExpect(jsonPath("$.sort[1].property").value("name"))
      .andExpect(jsonPath("$.sort[1].direction").value("ASC"))
      .andExpect(jsonPath("$.sort[2].property").value("id"))
      .andExpect(jsonPath("$.sort[2].direction").value("ASC"))
      .andExpect(
        jsonPath("$.links.self").value(
          "/api/v1/categories?page=0&size=20&sort=depth,asc&sort=name,asc&sort=id,asc"
        )
      );

    verify(categories).findAll(
      new ListCategoriesQuery(
        new PageCriteria(0, 20),
        new SortCriteria(
          List.of(
            new SortOrder("depth", SortDirection.ASC),
            new SortOrder("name", SortDirection.ASC),
            new SortOrder("id", SortDirection.ASC)
          )
        )
      )
    );
  }

  @Test
  void listCategoriesAcceptsTreeScopedAscendingSortRank() throws Exception {
    when(categories.findAll(any(ListCategoriesQuery.class))).thenReturn(
      new PageResult<>(
        List.of(new CategoryNode(category(1L, null, 1_000_000_000L, "finance", "Finance"), 0)),
        0,
        20,
        1,
        1,
        true,
        true,
        new SortCriteria(
          List.of(
            new SortOrder("tree", SortDirection.ASC),
            new SortOrder("sortRank", SortDirection.ASC)
          )
        )
      )
    );

    mvc
      .perform(
        get("/api/v1/categories")
          .with(authenticatedJwt())
          .param("sort", "tree,asc")
          .param("sort", "sortRank,asc")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].sortRank").value(1_000_000_000L))
      .andExpect(jsonPath("$.sort[1].property").value("sortRank"))
      .andExpect(jsonPath("$.sort[1].direction").value("ASC"));

    verify(categories).findAll(
      new ListCategoriesQuery(
        new PageCriteria(0, 20),
        new SortCriteria(
          List.of(
            new SortOrder("tree", SortDirection.ASC),
            new SortOrder("sortRank", SortDirection.ASC)
          )
        )
      )
    );
  }

  @Test
  void listCategoriesMapsDescendingSortRankPolicyRejectionToBadRequest() throws Exception {
    when(categories.findAll(any(ListCategoriesQuery.class))).thenThrow(
      new InvalidRequestException(
        "data.api.category.list.sort.sort-rank.unsupported",
        "sortRank sorting is only supported for ascending ranked tree category views"
      )
    );

    mvc
      .perform(get("/api/v1/categories").with(authenticatedJwt()).param("sort", "sortRank,desc"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
      .andExpect(
        jsonPath("$.message").value(
          "sortRank sorting is only supported for ascending ranked root, tree, or sibling-scoped category views"
        )
      );
  }

  @Test
  void listCategoriesAllowsClientCredentialsReadScope() throws Exception {
    when(categories.findAll(any(ListCategoriesQuery.class))).thenReturn(
      new PageResult<>(
        List.of(new CategoryNode(category(1L, null, "finance", "Finance"), 0)),
        0,
        20,
        1,
        1,
        true,
        true,
        SortCriteria.of("tree", SortDirection.ASC)
      )
    );

    mvc
      .perform(
        get("/api/v1/categories").with(serviceScopeJwt(CategoryAuthorities.SCOPE_CATEGORIES_READ))
      )
      .andExpect(status().isOk());
  }

  @Test
  void listCategoriesRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
  }

  @Test
  void listCategoriesRejectsUnderprivilegedAuthenticatedPrincipal() throws Exception {
    mvc
      .perform(get("/api/v1/categories").with(underprivilegedJwt()))
      .andExpect(status().isForbidden());
  }

  @Test
  void categoryReadAuthorityCannotWrite() throws Exception {
    mvc
      .perform(
        post("/api/v1/categories")
          .with(authorityJwt(CategoryAuthorities.CATEGORIES_READ))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "slug": "finance",
              "name": "Finance"
            }
            """
          )
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void categoryWriteAuthorityCannotRead() throws Exception {
    mvc
      .perform(get("/api/v1/categories").with(authorityJwt(CategoryAuthorities.CATEGORIES_WRITE)))
      .andExpect(status().isForbidden());
  }

  @Test
  void roleAdminIsNotCategoryBypass() throws Exception {
    mvc
      .perform(get("/api/v1/categories").with(authorityJwt("ROLE_ADMIN")))
      .andExpect(status().isForbidden());

    mvc
      .perform(
        post("/api/v1/categories")
          .with(authorityJwt("ROLE_ADMIN"))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "slug": "finance",
              "name": "Finance"
            }
            """
          )
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void listCategoriesRejectsUnsupportedSortProperty() throws Exception {
    mvc
      .perform(get("/api/v1/categories").with(authenticatedJwt()).param("sort", "passwordHash,asc"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
      .andExpect(
        jsonPath("$.message").value(
          "Sort must use one of: id, parentId, depth, tree, sortRank, slug, name, active, createdAt, updatedAt"
        )
      );
  }

  @Test
  void listCategoriesAcceptsTreeSortWithSiblingSorts() throws Exception {
    when(categories.findAll(any(ListCategoriesQuery.class))).thenReturn(
      new PageResult<>(
        List.of(new CategoryNode(category(1L, null, "finance", "Finance"), 0)),
        0,
        20,
        1,
        1,
        true,
        true,
        new SortCriteria(
          List.of(
            new SortOrder("tree", SortDirection.ASC),
            new SortOrder("createdAt", SortDirection.DESC)
          )
        )
      )
    );

    mvc
      .perform(
        get("/api/v1/categories")
          .with(authenticatedJwt())
          .param("sort", "tree,asc")
          .param("sort", "createdAt,desc")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].depth").value(0))
      .andExpect(jsonPath("$.sort[0].property").value("tree"))
      .andExpect(jsonPath("$.sort[0].direction").value("ASC"))
      .andExpect(jsonPath("$.sort[1].property").value("createdAt"))
      .andExpect(jsonPath("$.sort[1].direction").value("DESC"));

    verify(categories).findAll(
      new ListCategoriesQuery(
        new PageCriteria(0, 20),
        new SortCriteria(
          List.of(
            new SortOrder("tree", SortDirection.ASC),
            new SortOrder("createdAt", SortDirection.DESC)
          )
        )
      )
    );
  }

  @Test
  void listCategoriesRejectsTreeSortWhenNotFirst() throws Exception {
    mvc
      .perform(
        get("/api/v1/categories")
          .with(authenticatedJwt())
          .param("sort", "name,asc")
          .param("sort", "tree,asc")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
      .andExpect(jsonPath("$.message").value("Tree sort must be the first sort order"));
  }

  @Test
  void listTreeReturnsNestedCategories() throws Exception {
    when(categories.findTree()).thenReturn(
      List.of(
        new CategoryTree(
          category(1L, null, "finance", "Finance"),
          List.of(new CategoryTree(category(2L, 1L, "reports", "Reports"), List.of()))
        )
      )
    );

    mvc
      .perform(get("/api/v1/categories/tree").with(authenticatedJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(1))
      .andExpect(jsonPath("$[0].children[0].id").value(2))
      .andExpect(jsonPath("$[0].children[0].slug").value("reports"))
      .andExpect(jsonPath("$[0].children[0].children").isArray())
      .andExpect(jsonPath("$[0].children[0].children").isEmpty());
  }

  @Test
  void getTreeReturnsCategorySubtree() throws Exception {
    when(categories.findTree(1L)).thenReturn(
      new CategoryTree(
        category(1L, null, "finance", "Finance"),
        List.of(new CategoryTree(category(2L, 1L, "reports", "Reports"), List.of()))
      )
    );

    mvc
      .perform(get("/api/v1/categories/1/tree").with(authenticatedJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.slug").value("finance"))
      .andExpect(jsonPath("$.children[0].id").value(2));
  }

  @Test
  void getTreeMapsMissingCategoryToNotFound() throws Exception {
    when(categories.findTree(404L)).thenThrow(new CategoryNotFoundException(404L));

    mvc
      .perform(get("/api/v1/categories/404/tree").with(authenticatedJwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
      .andExpect(jsonPath("$.message").value("Category not found"));
  }

  @Test
  void moveCategoryDefaultsToLeafOnlyMode() throws Exception {
    when(categories.move(any(Long.class), any(MoveCategoryCommand.class))).thenReturn(
      category(1L, 2L, "reports", "Reports")
    );

    mvc
      .perform(
        post("/api/v1/categories/1/move")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "parentId": 2
            }
            """
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.parentId").value(2));

    verify(categories).move(1L, new MoveCategoryCommand(2L, CategoryMoveMode.LEAF_ONLY));
  }

  @Test
  void moveCategoryPassesTargetIndexPlacement() throws Exception {
    when(categories.move(any(Long.class), any(MoveCategoryCommand.class))).thenReturn(
      category(1L, 2L, 2_000_000_000L, "reports", "Reports")
    );

    mvc
      .perform(
        post("/api/v1/categories/1/move")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "parentId": 2,
              "mode": "SUBTREE",
              "targetIndex": 0
            }
            """
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.sortRank").value(2_000_000_000L));

    verify(categories).move(
      1L,
      new MoveCategoryCommand(2L, CategoryMoveMode.SUBTREE, SparseOrderTarget.from(null, null, 0))
    );
  }

  @Test
  void reorderCategoryPassesPlacementFields() throws Exception {
    when(categories.reorder(any(Long.class), any(ReorderCategoryCommand.class))).thenReturn(
      category(1L, 2L, 2_500_000_000L, "reports", "Reports")
    );

    mvc
      .perform(
        post("/api/v1/categories/1/reorder")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "parentId": 2,
              "afterId": 3
            }
            """
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.sortRank").value(2_500_000_000L));

    verify(categories).reorder(
      1L,
      new ReorderCategoryCommand(2L, SparseOrderTarget.from(null, 3L, null))
    );
  }

  @Test
  void reorderCategoryRequiresWriteAuthority() throws Exception {
    String body = """
      {
        "afterId": 3
      }
      """;

    mvc
      .perform(
        post("/api/v1/categories/1/reorder").contentType(MediaType.APPLICATION_JSON).content(body)
      )
      .andExpect(status().isUnauthorized());

    mvc
      .perform(
        post("/api/v1/categories/1/reorder")
          .with(authorityJwt(CategoryAuthorities.CATEGORIES_READ))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void estimateMoveReturnsClosureTableCost() throws Exception {
    when(categories.estimateMove(any(Long.class), any(MoveCategoryCommand.class))).thenReturn(
      new CategoryMoveEstimate(2, 1, 3, 2, 6, 8, 1, 2, 4, true)
    );

    mvc
      .perform(
        post("/api/v1/categories/1/move/estimate")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "parentId": 2,
              "mode": "SUBTREE"
            }
            """
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.subtreeSize").value(2))
      .andExpect(jsonPath("$.oldAncestorCount").value(1))
      .andExpect(jsonPath("$.newAncestorCount").value(3))
      .andExpect(jsonPath("$.estimatedDeleteRows").value(2))
      .andExpect(jsonPath("$.estimatedInsertRows").value(6))
      .andExpect(jsonPath("$.estimatedTotalRows").value(8))
      .andExpect(jsonPath("$.subtreeMaxDepth").value(1))
      .andExpect(jsonPath("$.newParentDepth").value(2))
      .andExpect(jsonPath("$.resultingMaxDepth").value(4))
      .andExpect(jsonPath("$.parentChangeRequired").value(true));

    verify(categories).estimateMove(1L, new MoveCategoryCommand(2L, CategoryMoveMode.SUBTREE));
  }

  @Test
  void moveCategoryMapsHierarchyPolicyViolationToConflict() throws Exception {
    when(categories.move(any(Long.class), any(MoveCategoryCommand.class))).thenThrow(
      CategoryHierarchyPolicyViolationException.moveAffectedRowsTooLarge(200_000, 100_000)
    );

    mvc
      .perform(
        post("/api/v1/categories/1/move")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "parentId": 2,
              "mode": "SUBTREE"
            }
            """
          )
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CATEGORY_HIERARCHY_POLICY_VIOLATION"))
      .andExpect(
        jsonPath("$.message").value(
          "Category move is too large for a synchronous request. Estimated closure rows 200,000, limit 100,000"
        )
      );
  }

  @Test
  void moveCategoryMapsRankRebalanceLimitToConflict() throws Exception {
    when(categories.move(any(Long.class), any(MoveCategoryCommand.class))).thenThrow(
      CategoryHierarchyPolicyViolationException.rankRebalanceLimitExceeded(1_001, 1_000)
    );

    mvc
      .perform(
        post("/api/v1/categories/1/move")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "parentId": 2,
              "mode": "SUBTREE",
              "targetIndex": 0
            }
            """
          )
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CATEGORY_HIERARCHY_POLICY_VIOLATION"))
      .andExpect(
        jsonPath("$.message").value("Category rank rebalance would affect 1,001 rows, limit 1,000")
      );
  }

  private static Category category(Long id, Long parentId, String slug, String name) {
    return new Category(id, parentId, slug, name, "Finance data", true, NOW, NOW, "test", "test");
  }

  private static Category category(
    Long id,
    Long parentId,
    Long sortRank,
    String slug,
    String name
  ) {
    return new Category(
      id,
      parentId,
      sortRank,
      slug,
      name,
      "Finance data",
      true,
      NOW,
      NOW,
      "test",
      "test"
    );
  }

  private static CategorySystemDescriptor rankedDescriptor(
    String systemKey,
    String nodeTable,
    String closureTable
  ) {
    return new CategorySystemDescriptor(
      systemKey,
      systemKey,
      ClosureTableDescriptor.of(nodeTable, closureTable),
      RankingCapabilityDescriptor.ranked(
        "sort_rank",
        1_000_000_000L,
        1_000_000_000L,
        1_000L,
        DisabledRankingRequestPolicy.REJECT,
        List.of(
          PlacementMode.APPEND,
          PlacementMode.BEFORE_ID,
          PlacementMode.AFTER_ID,
          PlacementMode.TARGET_INDEX
        ),
        true
      ),
      new FallbackOrderDescriptor(
        List.of(
          new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
          new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
        )
      ),
      Optional.of(SparseRankTableDescriptor.of(nodeTable, "parent_id", "sort_rank"))
    );
  }

  private static RequestPostProcessor authenticatedJwt() {
    return jwt()
      .jwt(jwt -> jwt.claim("username", "feroz").claim("scope", "api"))
      .authorities(
        new SimpleGrantedAuthority(CategoryAuthorities.CATEGORIES_READ),
        new SimpleGrantedAuthority(CategoryAuthorities.CATEGORIES_WRITE)
      );
  }

  private static RequestPostProcessor underprivilegedJwt() {
    return jwt()
      .jwt(jwt -> jwt.claim("username", "feroz").claim("scope", "api"))
      .authorities(new SimpleGrantedAuthority("SCOPE_api"));
  }

  private static RequestPostProcessor serviceScopeJwt(String authority) {
    return jwt()
      .jwt(jwt ->
        jwt
          .subject("uniapi-nextjs-server")
          .claim("aud", List.of("uniapi"))
          .claim("client_id", "uniapi-nextjs-server")
      )
      .authorities(new SimpleGrantedAuthority(authority));
  }

  private static RequestPostProcessor authorityJwt(String... authorities) {
    return jwt()
      .jwt(jwt -> jwt.claim("username", "feroz").claim("scope", "api"))
      .authorities(
        Arrays.stream(authorities)
          .map(SimpleGrantedAuthority::new)
          .toArray(SimpleGrantedAuthority[]::new)
      );
  }
}
