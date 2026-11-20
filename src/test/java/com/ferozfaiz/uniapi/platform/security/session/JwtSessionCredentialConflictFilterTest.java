package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class JwtSessionCredentialConflictFilterTest {

  private final SessionCookieProperties properties = new SessionCookieProperties();
  private final JwtSessionCredentialConflictFilter filter = new JwtSessionCredentialConflictFilter(
    properties.userCookieName(),
    new ObjectMapper().findAndRegisterModules(),
    messages()
  );

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void allowsMatchingJwtAndSessionIdentity() throws Exception {
    MockHttpServletRequest request = request("feroz");
    SecurityContextHolder.getContext().setAuthentication(authentication("feroz"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    CountingFilterChain chain = new CountingFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.invocations).isEqualTo(1);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void rejectsConflictingJwtAndSessionIdentity() throws Exception {
    MockHttpServletRequest request = request("session-user");
    SecurityContextHolder.getContext().setAuthentication(authentication("jwt-user"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    CountingFilterChain chain = new CountingFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.invocations).isZero();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("AUTHENTICATION_CREDENTIAL_CONFLICT");
  }

  private MockHttpServletRequest request(String sessionUsername) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
    request.setRequestURI("/api/v1/users");
    request.addHeader("Authorization", "Bearer token");
    request.setCookies(new Cookie(properties.userCookieName(), "user-session"));
    request
      .getSession(true)
      .setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authentication(sessionUsername))
      );
    return request;
  }

  private static TestingAuthenticationToken authentication(String username) {
    return new TestingAuthenticationToken(
      new AuthenticatedPrincipal(1L, username, Set.of("USER"), Set.of()),
      null,
      List.of()
    );
  }

  private static MessageResolver messages() {
    return (code, args) ->
      code.equals("platform.api.auth.credential-conflict")
        ? "Authentication credentials conflict"
        : code;
  }

  private static class CountingFilterChain extends MockFilterChain {

    private int invocations;

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, ServletResponse response) {
      invocations++;
    }
  }
}
