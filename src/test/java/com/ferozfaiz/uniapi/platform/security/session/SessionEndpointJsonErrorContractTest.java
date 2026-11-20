package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class SessionEndpointJsonErrorContractTest {

  private final SessionSecurityExceptionHandlers handlers = handlers();

  @Test
  void unauthenticatedSessionEndpointReturnsJsonWithoutRedirect() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .authenticationEntryPoint()
      .commence(
        request("/api/v1/auth/me"),
        response,
        new BadCredentialsException("unauthenticated")
      );

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull();
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString()).contains("UNAUTHENTICATED");
  }

  @Test
  void forbiddenSessionEndpointReturnsJsonWithoutRedirect() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .accessDeniedHandler()
      .handle(request("/api/v1/auth/me"), response, new AccessDeniedException("denied"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull();
    assertThat(response.getContentAsString()).contains("FORBIDDEN");
  }

  @Test
  void adminForbiddenEndpointReturnsAdminJsonError() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .adminAccessDeniedHandler()
      .handle(request("/api/v1/admin/auth/me"), response, new AccessDeniedException("denied"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull();
    assertThat(response.getContentAsString()).contains("ADMIN_ACCESS_DENIED");
  }

  private static MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setRequestURI(path);
    return request;
  }

  private static SessionSecurityExceptionHandlers handlers() {
    MessageResolver messages = (code, args) ->
      switch (code) {
        case "platform.api.auth.unauthenticated" -> "Authentication is required";
        case "platform.api.auth.forbidden" -> "Access is denied";
        case "platform.api.auth.admin-access-denied" -> "Admin access is denied";
        default -> code;
      };
    return new SessionSecurityExceptionHandlers(
      new ObjectMapper().findAndRegisterModules(),
      messages
    );
  }
}
