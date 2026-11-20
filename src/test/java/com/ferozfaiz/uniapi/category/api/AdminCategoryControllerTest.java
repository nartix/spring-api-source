package com.ferozfaiz.uniapi.category.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.authorization.AdminCategorySystemAuthorizationPolicy;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class AdminCategoryControllerTest {

  private static final String LIST_PATH = "/api/v1/admin/category-systems/catalogue/categories";

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
    when(categories.definition("catalogue", Operation.LIST)).thenReturn(fixture.definition());
  }

  @Test
  void listReturnsStablePageAndDefinitionDrivenLinks() throws Exception {
    when(categories.list(eq("catalogue"), any(AdminCategoryListValidationInput.class))).thenReturn(
      page()
    );

    mvc
      .perform(get(LIST_PATH).with(authorityJwt("admin:catalogue-categories:read")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value(1))
      .andExpect(jsonPath("$.data[0].parentId").value(nullValue()))
      .andExpect(jsonPath("$.data[0].slug").value("storage"))
      .andExpect(jsonPath("$.page.number").value(0))
      .andExpect(jsonPath("$.page.size").value(20))
      .andExpect(jsonPath("$.sort[0].property").value("updatedAt"))
      .andExpect(jsonPath("$.sort[1].property").value("id"))
      .andExpect(
        jsonPath("$.links.self").value(
          LIST_PATH + "?page=0&size=20&sort=updatedAt,desc&sort=id,asc"
        )
      );

    verify(categories).definition("catalogue", Operation.LIST);
    verify(categories).list(eq("catalogue"), any(AdminCategoryListValidationInput.class));
  }

  @ParameterizedTest
  @ValueSource(
    strings = {
      "admin:blog-categories:read",
      "admin:catalogue-categories:write",
      "admin:categories:read",
      "ROLE_ADMIN",
      "SCOPE_admin:catalogue-categories:read",
    }
  )
  void listRejectsEveryNonExactAuthority(String authority) throws Exception {
    mvc.perform(get(LIST_PATH).with(authorityJwt(authority))).andExpect(status().isForbidden());

    verifyNoInteractions(categories);
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mvc.perform(get(LIST_PATH)).andExpect(status().isUnauthorized());
    verifyNoInteractions(categories);
  }

  @Test
  void trimOnlyKeyHandlingAcceptsWhitespaceButNeverLowercases() throws Exception {
    when(categories.definition(" catalogue ", Operation.LIST)).thenReturn(fixture.definition());
    when(
      categories.list(eq(" catalogue "), any(AdminCategoryListValidationInput.class))
    ).thenReturn(page());
    when(categories.definition("Catalogue", Operation.LIST)).thenThrow(
      AdminCategoryErrors.unknownSystem("Catalogue")
    );

    mvc
      .perform(
        get(java.net.URI.create("/api/v1/admin/category-systems/%20catalogue%20/categories")).with(
          authorityJwt("admin:catalogue-categories:read")
        )
      )
      .andExpect(status().isOk());
    mvc
      .perform(
        get("/api/v1/admin/category-systems/Catalogue/categories").with(
          authorityJwt("admin:Catalogue-categories:read")
        )
      )
      .andExpect(status().isNotFound());

    verify(registrations, never()).findRegistration("Catalogue");
  }

  @Test
  void unknownSystemUsesStableErrorBodyAfterPreliminaryAuthorization() throws Exception {
    when(categories.definition("unknown", Operation.LIST)).thenThrow(
      AdminCategoryErrors.unknownSystem("unknown")
    );

    mvc
      .perform(
        get("/api/v1/admin/category-systems/unknown/categories").with(
          authorityJwt("admin:unknown-categories:read")
        )
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY_SYSTEM"))
      .andExpect(jsonPath("$.message").value("category system was not found"));
  }

  private static RequestPostProcessor authorityJwt(String authority) {
    return jwt().authorities(new SimpleGrantedAuthority(authority));
  }

  private static PageResult<AdminCategoryModels.CategoryNode> page() {
    var timestamp = OffsetDateTime.parse("2026-07-14T00:00:00Z");
    return new PageResult<>(
      List.of(
        new AdminCategoryModels.CategoryNode(
          new AdminCategoryModels.Category(
            1L,
            null,
            null,
            "Storage",
            "storage",
            true,
            timestamp,
            timestamp
          ),
          0,
          false
        )
      ),
      0,
      20,
      1,
      1,
      true,
      true,
      new SortCriteria(
        List.of(
          new com.ferozfaiz.uniapi.shared.pagination.SortOrder("updatedAt", SortDirection.DESC),
          new com.ferozfaiz.uniapi.shared.pagination.SortOrder("id", SortDirection.ASC)
        )
      )
    );
  }
}
