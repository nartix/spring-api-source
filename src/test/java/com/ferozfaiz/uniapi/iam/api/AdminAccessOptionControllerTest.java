package com.ferozfaiz.uniapi.iam.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedAuthorities;
import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedIdentity;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthenticator;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthorityLookup;
import com.ferozfaiz.uniapi.iam.application.AdminAccessOptionApplicationService;
import com.ferozfaiz.uniapi.iam.application.query.AdminListPermissionsQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminListRolesQuery;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.authentication.IamAuthenticationProvider;
import com.ferozfaiz.uniapi.platform.security.authorizationserver.AuthorizationServerPersistenceConfiguration;
import com.ferozfaiz.uniapi.platform.security.authorizationserver.AuthorizationServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.authorizationserver.OAuthRegisteredClientSeeder;
import com.ferozfaiz.uniapi.platform.security.jwk.OAuthSigningKeyConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.web.BrowserLoginSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AdminAccessOptionController.class)
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
  IamApiExceptionHandler.class,
})
class AdminAccessOptionControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private AdminAccessOptionApplicationService accessOptions;

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
      new AuthenticatedIdentity(1L, "admin", true, true, true, true)
    );
    when(authorityLookup.findAuthorities(any())).thenReturn(
      new AuthenticatedAuthorities(
        Set.of("ADMIN"),
        Set.of(AdminAuthorities.ADMIN_USERS_READ, AdminAuthorities.ADMIN_USERS_WRITE)
      )
    );
  }

  @Test
  void listAdminRolesReturnsDefaultPage() throws Exception {
    when(accessOptions.findRoles(any(AdminListRolesQuery.class))).thenAnswer(invocation ->
      rolePage(invocation.getArgument(0))
    );

    mvc
      .perform(get("/api/v1/admin/roles").with(authorityJwt(AdminAuthorities.ADMIN_USERS_WRITE)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value("1"))
      .andExpect(jsonPath("$.data[0].code").value("ADMIN"))
      .andExpect(jsonPath("$.data[0].name").value("Administrator"))
      .andExpect(jsonPath("$.data[0].description").value("Default administrator role"))
      .andExpect(jsonPath("$.data[0].active").value(true))
      .andExpect(jsonPath("$.page.number").value(0))
      .andExpect(jsonPath("$.page.size").value(100))
      .andExpect(jsonPath("$.sort[0].property").value("code"))
      .andExpect(jsonPath("$.sort[0].direction").value("ASC"))
      .andExpect(jsonPath("$.sort[1].property").value("id"))
      .andExpect(jsonPath("$.sort[1].direction").value("ASC"))
      .andExpect(
        jsonPath("$.links.self").value(
          "/api/v1/admin/roles?page=0&size=100&sort=code,asc&sort=id,asc"
        )
      )
      .andExpect(jsonPath("$.links.next").value(nullValue()))
      .andExpect(jsonPath("$.links.previous").value(nullValue()));

    verify(accessOptions).findRoles(
      argThat(
        query ->
          query.page().page() == 0 &&
          query.page().size() == 100 &&
          query.q() == null &&
          Boolean.TRUE.equals(query.active()) &&
          query.sort().orders().size() == 2 &&
          "code".equals(query.sort().orders().get(0).property()) &&
          query.sort().orders().get(0).direction() == SortDirection.ASC &&
          "id".equals(query.sort().orders().get(1).property())
      )
    );
  }

  @Test
  void listAdminPermissionsAcceptsFiltersAndSorts() throws Exception {
    when(accessOptions.findPermissions(any(AdminListPermissionsQuery.class))).thenAnswer(
      invocation -> permissionPage(invocation.getArgument(0))
    );

    mvc
      .perform(
        get("/api/v1/admin/permissions")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_WRITE))
          .param("page", "1")
          .param("size", "5")
          .param("sort", "resource,asc")
          .param("sort", "action,desc")
          .param("q", "users")
          .param("resource", "admin-users")
          .param("action", "read")
          .param("active", "false")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value("10"))
      .andExpect(jsonPath("$.data[0].code").value("admin:users:read"))
      .andExpect(jsonPath("$.data[0].name").value("Read admin users"))
      .andExpect(jsonPath("$.data[0].description").value("Allows reading user records"))
      .andExpect(jsonPath("$.data[0].resource").value("admin-users"))
      .andExpect(jsonPath("$.data[0].action").value("read"))
      .andExpect(jsonPath("$.data[0].active").value(true))
      .andExpect(jsonPath("$.page.number").value(1))
      .andExpect(jsonPath("$.page.size").value(5))
      .andExpect(jsonPath("$.sort[0].property").value("resource"))
      .andExpect(jsonPath("$.sort[0].direction").value("ASC"))
      .andExpect(jsonPath("$.sort[1].property").value("action"))
      .andExpect(jsonPath("$.sort[1].direction").value("DESC"))
      .andExpect(jsonPath("$.sort[2].property").value("id"))
      .andExpect(jsonPath("$.sort[2].direction").value("ASC"))
      .andExpect(
        jsonPath("$.links.previous").value(
          "/api/v1/admin/permissions?page=0&size=5&q=users&resource=admin-users&action=read&active=false&sort=resource,asc&sort=action,desc&sort=id,asc"
        )
      );

    verify(accessOptions).findPermissions(
      argThat(
        query ->
          query.page().page() == 1 &&
          query.page().size() == 5 &&
          "users".equals(query.q()) &&
          "admin-users".equals(query.resource()) &&
          "read".equals(query.action()) &&
          Boolean.FALSE.equals(query.active()) &&
          query.sort().orders().size() == 3 &&
          "resource".equals(query.sort().orders().get(0).property()) &&
          query.sort().orders().get(0).direction() == SortDirection.ASC &&
          "action".equals(query.sort().orders().get(1).property()) &&
          query.sort().orders().get(1).direction() == SortDirection.DESC &&
          "id".equals(query.sort().orders().get(2).property())
      )
    );
  }

  @Test
  void listAdminRolesRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/v1/admin/roles")).andExpect(status().isUnauthorized());
  }

  @Test
  void listAdminRolesRequiresAdminUsersWriteAuthority() throws Exception {
    mvc
      .perform(get("/api/v1/admin/roles").with(authorityJwt(AdminAuthorities.ADMIN_USERS_READ)))
      .andExpect(status().isForbidden());
  }

  @Test
  void listAdminPermissionsRejectsInvalidSort() throws Exception {
    mvc
      .perform(
        get("/api/v1/admin/permissions")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_WRITE))
          .param("sort", "passwordHash,asc")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
      .andExpect(
        jsonPath("$.message").value(
          "sort must use one of: id, code, name, resource, action, active, createdAt, updatedAt"
        )
      )
      .andExpect(jsonPath("$.path").value("/api/v1/admin/permissions"));
  }

  private static PageResult<Role> rolePage(AdminListRolesQuery query) {
    return new PageResult<>(
      List.of(role()),
      query.page().page(),
      query.page().size(),
      1,
      1,
      true,
      true,
      query.sort()
    );
  }

  private static PageResult<Permission> permissionPage(AdminListPermissionsQuery query) {
    return new PageResult<>(
      List.of(permission()),
      query.page().page(),
      query.page().size(),
      6,
      2,
      false,
      true,
      query.sort()
    );
  }

  private static Role role() {
    return new Role(
      1L,
      "ADMIN",
      "Administrator",
      "Default administrator role",
      true,
      OffsetDateTime.parse("2026-05-01T06:30:00Z"),
      OffsetDateTime.parse("2026-05-01T06:30:00Z"),
      "system",
      "system"
    );
  }

  private static Permission permission() {
    return new Permission(
      10L,
      "admin:users:read",
      "Read admin users",
      "Allows reading user records",
      "admin-users",
      "read",
      true,
      OffsetDateTime.parse("2026-05-01T06:30:00Z"),
      OffsetDateTime.parse("2026-05-01T06:30:00Z"),
      "system",
      "system"
    );
  }

  private static RequestPostProcessor authorityJwt(String authority) {
    return jwt()
      .jwt(token -> token.subject("admin"))
      .authorities(new SimpleGrantedAuthority(authority));
  }
}
