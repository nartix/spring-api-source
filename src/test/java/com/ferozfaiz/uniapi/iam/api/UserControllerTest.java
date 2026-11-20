package com.ferozfaiz.uniapi.iam.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
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
import com.ferozfaiz.uniapi.iam.application.UserApplicationService;
import com.ferozfaiz.uniapi.iam.application.command.PatchUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.RegisterUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.UpdateUserCommand;
import com.ferozfaiz.uniapi.iam.application.query.ListUsersQuery;
import com.ferozfaiz.uniapi.iam.domain.exception.DuplicateUserException;
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

@WebMvcTest(UserController.class)
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
class UserControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private UserApplicationService users;

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
      new AuthenticatedAuthorities(Set.of("ADMIN"), Set.of("users:read", "users:write"))
    );
  }

  @Test
  void createUserRegistersUser() throws Exception {
    when(users.register(any(RegisterUserCommand.class))).thenReturn(user());

    mvc
      .perform(
        post("/api/v1/users")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "feroz",
              "email": "feroz@example.com",
              "password": "secret123",
              "firstName": "Feroz",
              "lastName": "Faiz"
            }
            """
          )
      )
      .andExpect(status().isCreated())
      .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/v1/users/1"))
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.username").value("feroz"))
      .andExpect(jsonPath("$.email").value("feroz@example.com"))
      .andExpect(jsonPath("$.firstName").value("Feroz"))
      .andExpect(jsonPath("$.lastName").value("Faiz"))
      .andExpect(jsonPath("$.active").value(true))
      .andExpect(jsonPath("$.passwordHash").doesNotExist());

    verify(users).register(
      new RegisterUserCommand("feroz", "feroz@example.com", "secret123", "Feroz", "Faiz")
    );
  }

  @Test
  void createUserAllowsClientCredentialsWriteScope() throws Exception {
    when(users.register(any(RegisterUserCommand.class))).thenReturn(user());

    mvc
      .perform(
        post("/api/v1/users")
          .with(serviceScopeJwt(UserAuthorities.SCOPE_USERS_WRITE))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "feroz",
              "email": "feroz@example.com",
              "password": "secret123"
            }
            """
          )
      )
      .andExpect(status().isCreated());
  }

  @Test
  void createUserRequiresAuthentication() throws Exception {
    mvc
      .perform(
        post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(
          """
          {
            "username": "feroz",
            "email": "feroz@example.com",
            "password": "secret123"
          }
          """
        )
      )
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
      .andExpect(jsonPath("$.message").value("Authentication is required"))
      .andExpect(jsonPath("$.path").value("/api/v1/users"));
  }

  @Test
  void createUserRejectsUnderprivilegedAuthenticatedPrincipal() throws Exception {
    mvc
      .perform(
        post("/api/v1/users")
          .with(underprivilegedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "feroz",
              "email": "feroz@example.com",
              "password": "secret123"
            }
            """
          )
      )
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"))
      .andExpect(jsonPath("$.message").value("Access is denied"))
      .andExpect(jsonPath("$.path").value("/api/v1/users"));
  }

  @Test
  void createUserRejectsInvalidRequest() throws Exception {
    mvc
      .perform(
        post("/api/v1/users")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "",
              "email": "not-an-email",
              "password": "short"
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.message").value("Request validation failed"))
      .andExpect(jsonPath("$.path").value("/api/v1/users"))
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
  }

  @Test
  void createUserLocalizesValidationFailureMessage() throws Exception {
    mvc
      .perform(
        post("/api/v1/users")
          .with(authenticatedJwt())
          .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "",
              "email": "not-an-email",
              "password": "short"
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.message").value("Echec de la validation de la requete"))
      .andExpect(jsonPath("$.path").value("/api/v1/users"));
  }

  @Test
  void createUserMapsDuplicateUserToConflict() throws Exception {
    when(users.register(any(RegisterUserCommand.class))).thenThrow(
      DuplicateUserException.emailAlreadyRegistered()
    );

    mvc
      .perform(
        post("/api/v1/users")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "feroz",
              "email": "feroz@example.com",
              "password": "secret123"
            }
            """
          )
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"))
      .andExpect(jsonPath("$.message").value("Email is already registered"))
      .andExpect(jsonPath("$.path").value("/api/v1/users"));
  }

  @Test
  void createUserLocalizesDuplicateUserMessage() throws Exception {
    when(users.register(any(RegisterUserCommand.class))).thenThrow(
      DuplicateUserException.emailAlreadyRegistered()
    );

    mvc
      .perform(
        post("/api/v1/users")
          .with(authenticatedJwt())
          .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "feroz",
              "email": "feroz@example.com",
              "password": "secret123"
            }
            """
          )
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"))
      .andExpect(jsonPath("$.message").value("L'adresse e-mail est deja enregistree"))
      .andExpect(jsonPath("$.path").value("/api/v1/users"));
  }

  @Test
  void listUsersReturnsUsers() throws Exception {
    when(users.findAll(any(ListUsersQuery.class))).thenReturn(pageOf(user()));

    mvc
      .perform(get("/api/v1/users").with(authenticatedJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value(1))
      .andExpect(jsonPath("$.data[0].username").value("feroz"))
      .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
      .andExpect(jsonPath("$.page.number").value(0))
      .andExpect(jsonPath("$.page.size").value(20))
      .andExpect(jsonPath("$.page.totalElements").value(1))
      .andExpect(jsonPath("$.page.totalPages").value(1))
      .andExpect(jsonPath("$.page.numberOfElements").value(1))
      .andExpect(jsonPath("$.page.first").value(true))
      .andExpect(jsonPath("$.page.last").value(true))
      .andExpect(jsonPath("$.sort[0].property").value("createdAt"))
      .andExpect(jsonPath("$.sort[0].direction").value("DESC"))
      .andExpect(jsonPath("$.links.self").value("/api/v1/users?page=0&size=20&sort=createdAt,desc"))
      .andExpect(jsonPath("$.links.next").value(nullValue()))
      .andExpect(jsonPath("$.links.previous").value(nullValue()));

    verify(users).findAll(
      new ListUsersQuery(new PageCriteria(0, 20), SortCriteria.of("createdAt", SortDirection.DESC))
    );
  }

  @Test
  void listUsersAllowsClientCredentialsReadScope() throws Exception {
    when(users.findAll(any(ListUsersQuery.class))).thenReturn(pageOf(user()));

    mvc
      .perform(get("/api/v1/users").with(serviceScopeJwt(UserAuthorities.SCOPE_USERS_READ)))
      .andExpect(status().isOk());
  }

  @Test
  void listUsersRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void listUsersRejectsUnderprivilegedAuthenticatedPrincipal() throws Exception {
    mvc.perform(get("/api/v1/users").with(underprivilegedJwt())).andExpect(status().isForbidden());
  }

  @Test
  void usersReadAuthorityCannotWrite() throws Exception {
    mvc
      .perform(
        post("/api/v1/users")
          .with(authorityJwt(UserAuthorities.USERS_READ))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "feroz",
              "email": "feroz@example.com",
              "password": "secret123"
            }
            """
          )
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void usersWriteAuthorityCannotRead() throws Exception {
    mvc
      .perform(get("/api/v1/users").with(authorityJwt(UserAuthorities.USERS_WRITE)))
      .andExpect(status().isForbidden());
  }

  @Test
  void roleAdminCanReadAndWriteUsers() throws Exception {
    when(users.findAll(any(ListUsersQuery.class))).thenReturn(pageOf(user()));
    when(users.register(any(RegisterUserCommand.class))).thenReturn(user());

    mvc
      .perform(get("/api/v1/users").with(authorityJwt(UserAuthorities.ROLE_ADMIN)))
      .andExpect(status().isOk());

    mvc
      .perform(
        post("/api/v1/users")
          .with(authorityJwt(UserAuthorities.ROLE_ADMIN))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "feroz",
              "email": "feroz@example.com",
              "password": "secret123"
            }
            """
          )
      )
      .andExpect(status().isCreated());
  }

  @Test
  void listUsersAcceptsPaginationAndMultiSort() throws Exception {
    when(users.findAll(any(ListUsersQuery.class))).thenReturn(
      new PageResult<>(
        List.of(user()),
        1,
        5,
        6,
        2,
        false,
        true,
        new SortCriteria(
          List.of(
            new SortOrder("username", SortDirection.ASC),
            new SortOrder("createdAt", SortDirection.DESC)
          )
        )
      )
    );

    mvc
      .perform(
        get("/api/v1/users")
          .param("page", "1")
          .param("size", "5")
          .param("sort", "username,asc")
          .param("sort", "createdAt,desc")
          .with(authenticatedJwt())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page.number").value(1))
      .andExpect(jsonPath("$.page.size").value(5))
      .andExpect(jsonPath("$.page.totalElements").value(6))
      .andExpect(jsonPath("$.page.totalPages").value(2))
      .andExpect(jsonPath("$.page.numberOfElements").value(1))
      .andExpect(jsonPath("$.page.first").value(false))
      .andExpect(jsonPath("$.page.last").value(true))
      .andExpect(jsonPath("$.sort[0].property").value("username"))
      .andExpect(jsonPath("$.sort[0].direction").value("ASC"))
      .andExpect(jsonPath("$.sort[1].property").value("createdAt"))
      .andExpect(jsonPath("$.sort[1].direction").value("DESC"))
      .andExpect(
        jsonPath("$.links.self").value(
          "/api/v1/users?page=1&size=5&sort=username,asc&sort=createdAt,desc"
        )
      )
      .andExpect(jsonPath("$.links.next").value(nullValue()))
      .andExpect(
        jsonPath("$.links.previous").value(
          "/api/v1/users?page=0&size=5&sort=username,asc&sort=createdAt,desc"
        )
      );

    verify(users).findAll(
      new ListUsersQuery(
        new PageCriteria(1, 5),
        new SortCriteria(
          List.of(
            new SortOrder("username", SortDirection.ASC),
            new SortOrder("createdAt", SortDirection.DESC)
          )
        )
      )
    );
  }

  @Test
  void listUsersRejectsInvalidPaginationAndSorting() throws Exception {
    mvc
      .perform(
        get("/api/v1/users")
          .param("page", "-1")
          .param("size", "101")
          .param("direction", "sideways")
          .with(authenticatedJwt())
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.path").value("/api/v1/users"))
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'page')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'size')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'direction')]").exists());
  }

  @Test
  void listUsersRejectsUnsupportedSortProperty() throws Exception {
    mvc
      .perform(get("/api/v1/users").param("sort", "passwordHash,asc").with(authenticatedJwt()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
      .andExpect(
        jsonPath("$.message").value(
          "sort must use one of: id, username, email, active, createdAt, updatedAt"
        )
      )
      .andExpect(jsonPath("$.path").value("/api/v1/users"));
  }

  @Test
  void getUserReturnsUser() throws Exception {
    when(users.findById(1L)).thenReturn(user());

    mvc
      .perform(get("/api/v1/users/1").with(authenticatedJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.username").value("feroz"))
      .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void getUserMapsMissingUserToNotFound() throws Exception {
    when(users.findById(404L)).thenThrow(new UserNotFoundException(404L));

    mvc
      .perform(get("/api/v1/users/404").with(authenticatedJwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
      .andExpect(jsonPath("$.message").value("User not found"))
      .andExpect(jsonPath("$.path").value("/api/v1/users/404"));
  }

  @Test
  void updateUserUpdatesUser() throws Exception {
    when(users.update(any(Long.class), any(UpdateUserCommand.class))).thenReturn(user());

    mvc
      .perform(
        put("/api/v1/users/1")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "feroz",
              "email": "feroz@example.com",
              "firstName": "Feroz",
              "lastName": "Faiz",
              "active": true
            }
            """
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.username").value("feroz"))
      .andExpect(jsonPath("$.passwordHash").doesNotExist());

    verify(users).update(
      1L,
      new UpdateUserCommand("feroz", "feroz@example.com", "Feroz", "Faiz", true)
    );
  }

  @Test
  void updateUserRejectsInvalidRequest() throws Exception {
    mvc
      .perform(
        put("/api/v1/users/1")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "",
              "email": "not-an-email",
              "firstName": "Feroz",
              "active": true
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.path").value("/api/v1/users/1"))
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists());
  }

  @Test
  void patchUserPatchesProvidedFields() throws Exception {
    when(users.patch(any(Long.class), any(PatchUserCommand.class))).thenReturn(inactiveUser());

    mvc
      .perform(
        patch("/api/v1/users/1")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "firstName": "Feroz Updated",
              "active": false
            }
            """
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.firstName").value("Feroz Updated"))
      .andExpect(jsonPath("$.active").value(false))
      .andExpect(jsonPath("$.passwordHash").doesNotExist());

    verify(users).patch(1L, new PatchUserCommand(null, null, "Feroz Updated", null, false));
  }

  @Test
  void patchUserRejectsInvalidRequest() throws Exception {
    mvc
      .perform(
        patch("/api/v1/users/1")
          .with(authenticatedJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "username": "",
              "email": "not-an-email"
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.path").value("/api/v1/users/1"))
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists());
  }

  @Test
  void deleteUserDeletesUser() throws Exception {
    mvc
      .perform(delete("/api/v1/users/1").with(authenticatedJwt()))
      .andExpect(status().isNoContent());

    verify(users).delete(1L);
  }

  @Test
  void exposesOidcMetadata() throws Exception {
    mvc
      .perform(get("/.well-known/openid-configuration"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.issuer").value("http://localhost:8080"));
  }

  @Test
  void exposesJwks() throws Exception {
    mvc
      .perform(get("/oauth2/jwks"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.keys").isArray());
  }

  private RequestPostProcessor authenticatedJwt() {
    return jwt()
      .jwt(jwt ->
        jwt
          .subject("1")
          .claim("aud", List.of("uniapi"))
          .claim("username", "feroz")
          .claim("roles", List.of("ADMIN"))
          .claim("permissions", List.of("users:read", "users:write"))
      )
      .authorities(
        new SimpleGrantedAuthority("SCOPE_api"),
        new SimpleGrantedAuthority("ROLE_ADMIN"),
        new SimpleGrantedAuthority("users:read"),
        new SimpleGrantedAuthority("users:write")
      );
  }

  private RequestPostProcessor underprivilegedJwt() {
    return jwt()
      .jwt(jwt ->
        jwt
          .subject("1")
          .claim("aud", List.of("uniapi"))
          .claim("username", "feroz")
          .claim("roles", List.of())
          .claim("permissions", List.of())
      )
      .authorities(new SimpleGrantedAuthority("SCOPE_api"));
  }

  private RequestPostProcessor serviceScopeJwt(String authority) {
    return jwt()
      .jwt(jwt ->
        jwt
          .subject("uniapi-nextjs-server")
          .claim("aud", List.of("uniapi"))
          .claim("client_id", "uniapi-nextjs-server")
      )
      .authorities(new SimpleGrantedAuthority(authority));
  }

  private RequestPostProcessor authorityJwt(String... authorities) {
    return jwt()
      .jwt(jwt -> jwt.subject("1").claim("aud", List.of("uniapi")).claim("username", "feroz"))
      .authorities(
        Arrays.stream(authorities)
          .map(SimpleGrantedAuthority::new)
          .toArray(SimpleGrantedAuthority[]::new)
      );
  }

  private User user() {
    return IamTestUsers.testUser();
  }

  private User inactiveUser() {
    return IamTestUsers.inactiveUser(1L, "Feroz Updated", "Faiz");
  }

  private PageResult<User> pageOf(User user) {
    return new PageResult<>(
      List.of(user),
      0,
      20,
      1,
      1,
      true,
      true,
      SortCriteria.of("createdAt", SortDirection.DESC)
    );
  }
}
