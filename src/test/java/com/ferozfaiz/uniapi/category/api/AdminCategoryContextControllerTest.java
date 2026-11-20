package com.ferozfaiz.uniapi.category.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.authorization.AdminCategorySystemAuthorizationPolicy;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AdminCategoryController.class)
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  AdminCategoryApiExceptionHandler.class,
  AdminCategorySystemAuthorizationPolicy.class,
})
class AdminCategoryContextControllerTest {

  private static final String BASE_PATH = "/api/v1/admin/category-systems/catalogue/categories";

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private AdminCategoryApplicationService categories;

  @MockitoBean
  private AdminCategorySystemRegistry registrations;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private AdminCategoryRuntimeTestFixture.Harness fixture;

  @BeforeEach
  void registerCatalogue() {
    fixture = AdminCategoryRuntimeTestFixture.harness();
    when(registrations.findRegistration("catalogue")).thenReturn(
      Optional.of(fixture.registration())
    );
    when(categories.definition("catalogue", Operation.TREE)).thenReturn(fixture.definition());
    when(categories.definition("catalogue", Operation.PARENT_OPTIONS)).thenReturn(
      fixture.definition()
    );
  }

  @Test
  void capabilitiesExposeTheAdditiveDefinitionOnlyProjection() throws Exception {
    when(categories.capabilities("catalogue")).thenReturn(fixture.definition());

    mvc
      .perform(get(BASE_PATH + "/capabilities").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.rankingEnabled").value(true))
      .andExpect(jsonPath("$.supportedOperations.length()").value(14))
      .andExpect(jsonPath("$.hierarchy.enabled").value(true))
      .andExpect(jsonPath("$.limits.pageSize").value(1000))
      .andExpect(jsonPath("$.identifierProfile").value("NUMERIC_LONG_V1"))
      .andExpect(jsonPath("$.ordering.rankingEnabled").value(true));

    verify(categories).capabilities("catalogue");
  }

  @Test
  void treeAndParentOptionsPreserveDefaultsAndOpaqueCursor() throws Exception {
    when(categories.tree(eq("catalogue"), any(AdminCategoryTreeValidationInput.class))).thenReturn(
      new PageResult<>(
        List.of(node()),
        0,
        10_000,
        1,
        1,
        true,
        true,
        SortCriteria.of("id", SortDirection.ASC)
      )
    );
    when(
      categories.parentOptions(
        eq("catalogue"),
        any(AdminCategoryParentOptionsValidationInput.class)
      )
    ).thenReturn(new AdminCategoryModels.ParentOptionSlice(List.of(), true, "opaque-next"));

    mvc
      .perform(get(BASE_PATH + "/tree").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page.size").value(10000));
    mvc
      .perform(get(BASE_PATH + "/parent-options?cursor=opaque-input&includeIds=10").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.hasMore").value(true))
      .andExpect(jsonPath("$.nextCursor").value("opaque-next"));

    verify(categories).tree(eq("catalogue"), any(AdminCategoryTreeValidationInput.class));
    verify(categories).parentOptions(
      eq("catalogue"),
      org.mockito.ArgumentMatchers.argThat(
        input -> input.cursor().equals("opaque-input") && input.includeIds().equals(List.of("10"))
      )
    );
  }

  @Test
  void getAndBreadcrumbRetainResponseShapes() throws Exception {
    when(categories.get("catalogue", 10L)).thenReturn(node().category());
    when(categories.breadcrumb("catalogue", 10L)).thenReturn(
      List.of(new AdminCategoryModels.BreadcrumbItem(node().category(), 0))
    );

    mvc
      .perform(get(BASE_PATH + "/10").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(10))
      .andExpect(jsonPath("$.parentId").value(nullValue()));
    mvc
      .perform(get(BASE_PATH + "/10/breadcrumb").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(10));
  }

  @Test
  void everyContextReadRequiresItsExactReadPermission() throws Exception {
    for (String path : List.of(
      BASE_PATH + "/capabilities",
      BASE_PATH + "/tree",
      BASE_PATH + "/parent-options",
      BASE_PATH + "/10",
      BASE_PATH + "/10/breadcrumb"
    )) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc
        .perform(get(path).with(authorityJwt("admin:catalogue-categories:write")))
        .andExpect(status().isForbidden());
    }
    verifyNoInteractions(categories);
  }

  private static RequestPostProcessor readJwt() {
    return authorityJwt("admin:catalogue-categories:read");
  }

  private static RequestPostProcessor authorityJwt(String authority) {
    return jwt().authorities(new SimpleGrantedAuthority(authority));
  }

  private static AdminCategoryModels.CategoryNode node() {
    var timestamp = OffsetDateTime.parse("2026-07-14T00:00:00Z");
    return new AdminCategoryModels.CategoryNode(
      new AdminCategoryModels.Category(
        10L,
        null,
        1_000_000_000L,
        "Storage",
        "storage",
        true,
        timestamp,
        timestamp
      ),
      0,
      false
    );
  }
}
