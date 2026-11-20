package com.ferozfaiz.uniapi.iam.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedAuthorities;
import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedIdentity;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthenticator;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthorityLookup;
import com.ferozfaiz.uniapi.iam.application.AdminUserApplicationService;
import com.ferozfaiz.uniapi.iam.application.command.AdminCreateUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.AdminUserMutationCommand;
import com.ferozfaiz.uniapi.iam.application.command.AdminUserStatusCommand;
import com.ferozfaiz.uniapi.iam.application.query.AdminListUsersQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminUserListItem;
import com.ferozfaiz.uniapi.iam.domain.exception.UserNotFoundException;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.support.IamTestUsers;
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
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.Arrays;
import java.util.List;
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

@WebMvcTest(AdminUserController.class)
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
class AdminUserControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private AdminUserApplicationService users;

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
  void listAdminUsersReturnsUsers() throws Exception {
    when(users.findAll(any(AdminListUsersQuery.class))).thenReturn(pageOf(listItem()));

    mvc
      .perform(get("/api/v1/admin/users").with(authorityJwt(AdminAuthorities.ADMIN_USERS_READ)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value("1"))
      .andExpect(jsonPath("$.data[0].username").value("feroz"))
      .andExpect(jsonPath("$.data[0].accountNonExpired").value(true))
      .andExpect(jsonPath("$.data[0].accountNonLocked").value(true))
      .andExpect(jsonPath("$.data[0].credentialsNonExpired").value(true))
      .andExpect(jsonPath("$.data[0].createdBy").value("system"))
      .andExpect(jsonPath("$.data[0].updatedBy").value("system"))
      .andExpect(jsonPath("$.data[0].roles[0]").value("ADMIN"))
      .andExpect(jsonPath("$.data[0].permissions[0]").value("admin:users:read"))
      .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
      .andExpect(jsonPath("$.page.number").value(0))
      .andExpect(jsonPath("$.page.size").value(20))
      .andExpect(jsonPath("$.sort[0].property").value("updatedAt"))
      .andExpect(jsonPath("$.sort[0].direction").value("DESC"))
      .andExpect(jsonPath("$.sort[1].property").value("id"))
      .andExpect(jsonPath("$.sort[1].direction").value("ASC"))
      .andExpect(
        jsonPath("$.links.self").value(
          "/api/v1/admin/users?page=0&size=20&sort=updatedAt,desc&sort=id,asc"
        )
      )
      .andExpect(jsonPath("$.links.next").value(nullValue()))
      .andExpect(jsonPath("$.links.previous").value(nullValue()));

    verify(users).findAll(defaultQuery());
  }

  @Test
  void listAdminUsersRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void listAdminUsersRejectsUnderprivilegedAuthenticatedPrincipal() throws Exception {
    mvc
      .perform(get("/api/v1/admin/users").with(underprivilegedJwt()))
      .andExpect(status().isForbidden());
  }

  @Test
  void adminUsersReadAuthorityCannotWrite() throws Exception {
    mvc
      .perform(
        post("/api/v1/admin/users")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_READ))
          .contentType(MediaType.APPLICATION_JSON)
          .content(validCreateJson())
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void adminUsersWriteAuthorityCannotRead() throws Exception {
    mvc
      .perform(get("/api/v1/admin/users").with(authorityJwt(AdminAuthorities.ADMIN_USERS_WRITE)))
      .andExpect(status().isForbidden());
  }

  @Test
  void adminUsersStatusAuthorityCannotReadOrWrite() throws Exception {
    mvc
      .perform(get("/api/v1/admin/users").with(authorityJwt(AdminAuthorities.ADMIN_USERS_STATUS)))
      .andExpect(status().isForbidden());

    mvc
      .perform(
        post("/api/v1/admin/users")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_STATUS))
          .contentType(MediaType.APPLICATION_JSON)
          .content(validCreateJson())
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void adminUsersReadAuthorityCannotUpdateStatus() throws Exception {
    mvc
      .perform(
        patch("/api/v1/admin/users/1/status")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_READ))
          .contentType(MediaType.APPLICATION_JSON)
          .content(validStatusJson())
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void roleAdminCanReadWriteAndUpdateStatusAdminUsers() throws Exception {
    when(users.findAll(any(AdminListUsersQuery.class))).thenReturn(pageOf(listItem()));
    when(users.create(any(AdminCreateUserCommand.class))).thenReturn(user());
    when(users.updateStatus(any(Long.class), any(AdminUserStatusCommand.class))).thenReturn(
      statusUser()
    );

    mvc
      .perform(get("/api/v1/admin/users").with(authorityJwt(AdminAuthorities.ROLE_ADMIN)))
      .andExpect(status().isOk());

    mvc
      .perform(
        post("/api/v1/admin/users")
          .with(authorityJwt(AdminAuthorities.ROLE_ADMIN))
          .contentType(MediaType.APPLICATION_JSON)
          .content(validCreateJson())
      )
      .andExpect(status().isCreated());

    mvc
      .perform(
        patch("/api/v1/admin/users/1/status")
          .with(authorityJwt(AdminAuthorities.ROLE_ADMIN))
          .contentType(MediaType.APPLICATION_JSON)
          .content(validStatusJson())
      )
      .andExpect(status().isOk());
  }

  @Test
  void listAdminUsersAcceptsPaginationAndMultiSort() throws Exception {
    when(users.findAll(any(AdminListUsersQuery.class))).thenReturn(
      new PageResult<>(
        List.of(listItem()),
        1,
        5,
        6,
        2,
        false,
        true,
        new SortCriteria(
          List.of(
            new SortOrder("username", SortDirection.ASC),
            new SortOrder("updatedAt", SortDirection.DESC),
            new SortOrder("id", SortDirection.ASC)
          )
        )
      )
    );

    mvc
      .perform(
        get("/api/v1/admin/users")
          .param("page", "1")
          .param("size", "5")
          .param("sort", "username,asc")
          .param("sort", "updatedAt,desc")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_READ))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page.number").value(1))
      .andExpect(jsonPath("$.page.size").value(5))
      .andExpect(jsonPath("$.sort[0].property").value("username"))
      .andExpect(jsonPath("$.sort[0].direction").value("ASC"))
      .andExpect(jsonPath("$.sort[1].property").value("updatedAt"))
      .andExpect(jsonPath("$.sort[1].direction").value("DESC"))
      .andExpect(jsonPath("$.sort[2].property").value("id"))
      .andExpect(jsonPath("$.sort[2].direction").value("ASC"))
      .andExpect(
        jsonPath("$.links.self").value(
          "/api/v1/admin/users?page=1&size=5&sort=username,asc&sort=updatedAt,desc&sort=id,asc"
        )
      )
      .andExpect(
        jsonPath("$.links.previous").value(
          "/api/v1/admin/users?page=0&size=5&sort=username,asc&sort=updatedAt,desc&sort=id,asc"
        )
      );

    verify(users).findAll(
      query(
        new PageCriteria(1, 5),
        new SortCriteria(
          List.of(
            new SortOrder("username", SortDirection.ASC),
            new SortOrder("updatedAt", SortDirection.DESC),
            new SortOrder("id", SortDirection.ASC)
          )
        )
      )
    );
  }

  @Test
  void listAdminUsersAcceptsSearchFiltersAndEveryReturnedFieldSort() throws Exception {
    when(users.findAll(any(AdminListUsersQuery.class))).thenReturn(
      new PageResult<>(
        List.of(listItem()),
        0,
        20,
        1,
        1,
        true,
        true,
        new SortCriteria(
          List.of(new SortOrder("roles", SortDirection.ASC), new SortOrder("id", SortDirection.ASC))
        )
      )
    );

    mvc
      .perform(
        get("/api/v1/admin/users")
          .param("search", "feroz")
          .param("username", "feroz")
          .param("roles", "ADMIN")
          .param("permissions", "admin:users:read")
          .param("active", "true")
          .param("accountNonExpired", "true")
          .param("createdFrom", "2026-05-01T00:00:00Z")
          .param("createdTo", "2026-05-02T00:00:00Z")
          .param("sort", "roles,asc")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_READ))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.sort[0].property").value("roles"))
      .andExpect(jsonPath("$.sort[0].direction").value("ASC"))
      .andExpect(jsonPath("$.sort[1].property").value("id"))
      .andExpect(jsonPath("$.sort[1].direction").value("ASC"));

    verify(users).findAll(
      argThat(
        query ->
          "feroz".equals(query.search()) &&
          "feroz".equals(query.username()) &&
          Boolean.TRUE.equals(query.active()) &&
          Boolean.TRUE.equals(query.accountNonExpired()) &&
          query.roles().equals(List.of("ADMIN")) &&
          query.permissions().equals(List.of("admin:users:read")) &&
          query.createdFrom().toString().equals("2026-05-01T00:00Z") &&
          query.createdTo().toString().equals("2026-05-02T00:00Z") &&
          query
            .sort()
            .orders()
            .equals(
              List.of(
                new SortOrder("roles", SortDirection.ASC),
                new SortOrder("id", SortDirection.ASC)
              )
            )
      )
    );
  }

  @Test
  void listAdminUsersRejectsUnsupportedSortProperty() throws Exception {
    mvc
      .perform(
        get("/api/v1/admin/users")
          .param("sort", "passwordHash,asc")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_READ))
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
      .andExpect(
        jsonPath("$.message").value(
          "sort must use one of: id, username, email, firstName, lastName, active, accountNonExpired, accountNonLocked, credentialsNonExpired, lastLoginAt, lastActivityAt, createdAt, updatedAt, createdBy, updatedBy, roles, permissions"
        )
      )
      .andExpect(jsonPath("$.path").value("/api/v1/admin/users"));
  }

  @Test
  void getAdminUserReturnsUser() throws Exception {
    when(users.findById(1L)).thenReturn(user());

    mvc
      .perform(get("/api/v1/admin/users/1").with(authorityJwt(AdminAuthorities.ADMIN_USERS_READ)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("1"))
      .andExpect(jsonPath("$.username").value("feroz"))
      .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
      .andExpect(jsonPath("$.permissions[0]").value("admin:users:read"))
      .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void getAdminUserMapsMissingUserToNotFound() throws Exception {
    when(users.findById(404L)).thenThrow(new UserNotFoundException(404L));

    mvc
      .perform(get("/api/v1/admin/users/404").with(authorityJwt(AdminAuthorities.ADMIN_USERS_READ)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
      .andExpect(jsonPath("$.message").value("User not found"))
      .andExpect(jsonPath("$.path").value("/api/v1/admin/users/404"));
  }

  @Test
  void createAdminUserRegistersUser() throws Exception {
    when(users.create(any(AdminCreateUserCommand.class))).thenReturn(user());

    mvc
      .perform(
        post("/api/v1/admin/users")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_WRITE))
          .contentType(MediaType.APPLICATION_JSON)
          .content(validCreateJson())
      )
      .andExpect(status().isCreated())
      .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/v1/admin/users/1"))
      .andExpect(jsonPath("$.id").value("1"))
      .andExpect(jsonPath("$.username").value("feroz"))
      .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
      .andExpect(jsonPath("$.permissions[0]").value("admin:users:read"))
      .andExpect(jsonPath("$.passwordHash").doesNotExist());

    verify(users).create(
      new AdminCreateUserCommand(
        "feroz",
        "feroz@example.com",
        "secret123",
        "Feroz",
        "Faiz",
        true,
        List.of("ADMIN"),
        List.of(AdminAuthorities.ADMIN_USERS_READ)
      )
    );
  }

  @Test
  void createAdminUserRejectsInvalidRequest() throws Exception {
    mvc
      .perform(
        post("/api/v1/admin/users")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_WRITE))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "",
              "email": "not-an-email",
              "active": null
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.path").value("/api/v1/admin/users"))
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'active')]").exists());
  }

  @Test
  void updateAdminUserUpdatesUser() throws Exception {
    when(users.update(any(Long.class), any(AdminUserMutationCommand.class))).thenReturn(user());

    mvc
      .perform(
        put("/api/v1/admin/users/1")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_WRITE))
          .contentType(MediaType.APPLICATION_JSON)
          .content(validMutationJson())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("1"))
      .andExpect(jsonPath("$.username").value("feroz"))
      .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
      .andExpect(jsonPath("$.permissions[0]").value("admin:users:read"))
      .andExpect(jsonPath("$.passwordHash").doesNotExist());

    verify(users).update(
      1L,
      new AdminUserMutationCommand(
        "feroz",
        "feroz@example.com",
        "Feroz",
        "Faiz",
        true,
        List.of("ADMIN"),
        List.of(AdminAuthorities.ADMIN_USERS_READ)
      )
    );
  }

  @Test
  void updateAdminUserStatusUpdatesStatusFields() throws Exception {
    when(users.updateStatus(any(Long.class), any(AdminUserStatusCommand.class))).thenReturn(
      statusUser()
    );

    mvc
      .perform(
        patch("/api/v1/admin/users/1/status")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_STATUS))
          .contentType(MediaType.APPLICATION_JSON)
          .content(validStatusJson())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("1"))
      .andExpect(jsonPath("$.username").value("feroz"))
      .andExpect(jsonPath("$.active").value(false))
      .andExpect(jsonPath("$.accountNonExpired").value(true))
      .andExpect(jsonPath("$.accountNonLocked").value(false))
      .andExpect(jsonPath("$.credentialsNonExpired").value(true))
      .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
      .andExpect(jsonPath("$.permissions[0]").value("admin:users:read"))
      .andExpect(jsonPath("$.passwordHash").doesNotExist());

    verify(users).updateStatus(1L, new AdminUserStatusCommand(false, true, false, true));
  }

  @Test
  void updateAdminUserStatusRejectsEmptyRequest() throws Exception {
    mvc
      .perform(
        patch("/api/v1/admin/users/1/status")
          .with(authorityJwt(AdminAuthorities.ADMIN_USERS_STATUS))
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.path").value("/api/v1/admin/users/1/status"))
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'statusChangePresent')]").exists());
  }

  @Test
  void deleteAdminUserDeletesUser() throws Exception {
    mvc
      .perform(
        delete("/api/v1/admin/users/1").with(authorityJwt(AdminAuthorities.ADMIN_USERS_WRITE))
      )
      .andExpect(status().isNoContent());

    verify(users).delete(1L);
  }

  private RequestPostProcessor underprivilegedJwt() {
    return jwt()
      .jwt(jwt ->
        jwt
          .subject("1")
          .claim("aud", List.of("uniapi"))
          .claim("username", "viewer")
          .claim("roles", List.of())
          .claim("permissions", List.of())
      )
      .authorities(new SimpleGrantedAuthority("SCOPE_api"));
  }

  private RequestPostProcessor authorityJwt(String... authorities) {
    return jwt()
      .jwt(jwt -> jwt.subject("1").claim("aud", List.of("uniapi")).claim("username", "admin"))
      .authorities(
        Arrays.stream(authorities)
          .map(SimpleGrantedAuthority::new)
          .toArray(SimpleGrantedAuthority[]::new)
      );
  }

  private User user() {
    return IamTestUsers.testUser();
  }

  private User statusUser() {
    return IamTestUsers.user(
      1L,
      "feroz",
      "feroz@example.com",
      false,
      true,
      false,
      true,
      "Feroz",
      "Faiz"
    );
  }

  private AdminUserListItem listItem() {
    User user = user();
    return new AdminUserListItem(
      user.id(),
      user.username(),
      user.email(),
      user.firstName(),
      user.lastName(),
      user.active(),
      user.accountNonExpired(),
      user.accountNonLocked(),
      user.credentialsNonExpired(),
      user.lastLoginAt(),
      user.lastActivityAt(),
      user.createdAt(),
      user.updatedAt(),
      user.createdBy(),
      user.updatedBy(),
      List.of("ADMIN"),
      List.of(AdminAuthorities.ADMIN_USERS_READ)
    );
  }

  private PageResult<AdminUserListItem> pageOf(AdminUserListItem user) {
    return new PageResult<>(
      List.of(user),
      0,
      20,
      1,
      1,
      true,
      true,
      new SortCriteria(
        List.of(
          new SortOrder("updatedAt", SortDirection.DESC),
          new SortOrder("id", SortDirection.ASC)
        )
      )
    );
  }

  private AdminListUsersQuery defaultQuery() {
    return query(
      new PageCriteria(0, 20),
      new SortCriteria(
        List.of(
          new SortOrder("updatedAt", SortDirection.DESC),
          new SortOrder("id", SortDirection.ASC)
        )
      )
    );
  }

  private AdminListUsersQuery query(PageCriteria page, SortCriteria sort) {
    return new AdminListUsersQuery(
      page,
      sort,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      List.of(),
      List.of()
    );
  }

  private String validMutationJson() {
    return """
    {
      "username": "feroz",
      "email": "feroz@example.com",
      "firstName": "Feroz",
      "lastName": "Faiz",
      "active": true,
      "roles": ["ADMIN"],
      "permissions": ["admin:users:read"]
    }
    """;
  }

  private String validCreateJson() {
    return """
    {
      "username": "feroz",
      "email": "feroz@example.com",
      "password": "secret123",
      "firstName": "Feroz",
      "lastName": "Faiz",
      "active": true,
      "roles": ["ADMIN"],
      "permissions": ["admin:users:read"]
    }
    """;
  }

  private String validStatusJson() {
    return """
    {
      "active": false,
      "accountNonExpired": true,
      "accountNonLocked": false,
      "credentialsNonExpired": true
    }
    """;
  }
}
