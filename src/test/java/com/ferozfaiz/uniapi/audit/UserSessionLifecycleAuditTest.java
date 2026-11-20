package com.ferozfaiz.uniapi.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.iam.api.auth.LoginActivityRecorder;
import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import com.ferozfaiz.uniapi.platform.security.session.SessionCookieProperties;
import com.ferozfaiz.uniapi.platform.security.session.SessionIdHashingService;
import com.ferozfaiz.uniapi.platform.security.session.SessionJsonAuthenticationHandlers;
import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.filter.ForwardedHeaderFilter;

class UserSessionLifecycleAuditTest {

  private final List<Object> events = new ArrayList<>();
  private final List<Long> recordedLoginUserIds = new ArrayList<>();
  private final SessionCookieProperties properties = properties();
  private final SessionJsonAuthenticationHandlers handlers = handlers();

  @Test
  void regularLoginSuccessPublishesSafeLifecycleEvent() throws Exception {
    MockHttpServletRequest request = request("/api/v1/auth/login");
    request.getSession(true);
    String rawSessionId = request.getSession(false).getId();
    MockHttpServletResponse response = new MockHttpServletResponse();
    var authentication = new TestingAuthenticationToken(
      new AuthenticatedPrincipal(1L, "feroz", Set.of("USER"), Set.of("user:profile:read")),
      null
    );

    handlers
      .regularLoginSuccessHandler()
      .onAuthenticationSuccess(request, response, authentication);

    SessionLifecycleEvent event = singleEvent();
    assertThat(event.eventType()).isEqualTo(SessionLifecycleEvent.Type.USER_LOGIN_SUCCESS);
    assertThat(event.sessionScope()).isEqualTo(SessionLifecycleEvent.Scope.USER);
    assertThat(event.userId()).isEqualTo(1L);
    assertThat(event.sessionIdHash()).hasSize(64);
    assertThat(event.sessionIdHash()).isNotEqualTo(rawSessionId);
    assertThat(event.ipAddress()).isEqualTo("127.0.0.1");
    assertThat(response.getContentAsString()).contains("\"success\":true");
    assertThat(recordedLoginUserIds).containsExactly(1L);
  }

  @Test
  void regularLoginSuccessPublishesForwardedClientIpWhenForwardedHeadersAreApplied()
    throws Exception {
    MockHttpServletRequest request = request("/api/v1/auth/login");
    request.setRemoteAddr("172.21.0.1");
    request.addHeader("X-Forwarded-For", "203.0.113.24, 172.21.0.1");
    request.addHeader("X-Forwarded-Proto", "https");
    request.getSession(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    var authentication = new TestingAuthenticationToken(
      new AuthenticatedPrincipal(1L, "feroz", Set.of("USER"), Set.of("user:profile:read")),
      null
    );

    new ForwardedHeaderFilter().doFilter(request, response, (filteredRequest, filteredResponse) ->
      handlers
        .regularLoginSuccessHandler()
        .onAuthenticationSuccess(
          (HttpServletRequest) filteredRequest,
          (HttpServletResponse) filteredResponse,
          authentication
        )
    );

    SessionLifecycleEvent event = singleEvent();
    assertThat(event.eventType()).isEqualTo(SessionLifecycleEvent.Type.USER_LOGIN_SUCCESS);
    assertThat(event.ipAddress()).isEqualTo("203.0.113.24");
    assertThat(recordedLoginUserIds).containsExactly(1L);
  }

  @Test
  void regularLoginFailurePublishesGenericFailureEvent() throws Exception {
    MockHttpServletRequest request = request("/api/v1/auth/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .regularLoginFailureHandler()
      .onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

    SessionLifecycleEvent event = singleEvent();
    assertThat(event.eventType()).isEqualTo(SessionLifecycleEvent.Type.USER_LOGIN_FAILURE);
    assertThat(event.userId()).isNull();
    assertThat(event.username()).isNull();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("LOGIN_FAILED");
    assertThat(recordedLoginUserIds).isEmpty();
  }

  @Test
  void regularLogoutPublishesLogoutEventWithHashedSessionId() throws Exception {
    MockHttpServletRequest request = request("/api/v1/auth/logout");
    request.setRequestedSessionId("raw-session-id");
    MockHttpServletResponse response = new MockHttpServletResponse();
    var authentication = new TestingAuthenticationToken(
      new AuthenticatedPrincipal(1L, "feroz", Set.of("USER"), Set.of()),
      null
    );

    handlers.regularLogoutSuccessHandler().onLogoutSuccess(request, response, authentication);

    SessionLifecycleEvent event = singleEvent();
    assertThat(event.eventType()).isEqualTo(SessionLifecycleEvent.Type.USER_LOGOUT);
    assertThat(event.sessionIdHash()).hasSize(64);
    assertThat(event.sessionIdHash()).doesNotContain("raw-session-id");
    assertThat(response.getCookie(properties.userCsrfCookieName()).getMaxAge()).isZero();
    assertThat(response.getCookie(properties.adminCsrfCookieName())).isNull();
  }

  private SessionLifecycleEvent singleEvent() {
    assertThat(events).hasSize(1);
    SessionLifecycleEvent event = (SessionLifecycleEvent) events.getFirst();
    events.clear();
    return event;
  }

  private static MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRequestURI(path);
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("User-Agent", "test-agent");
    return request;
  }

  private SessionJsonAuthenticationHandlers handlers() {
    MessageResolver messages = (code, args) ->
      code.equals("iam.api.auth.login.failed") ? "Invalid username or password" : code;
    return new SessionJsonAuthenticationHandlers(
      new ObjectMapper().findAndRegisterModules(),
      messages,
      events::add,
      new SessionIdHashingService(properties),
      properties,
      recorder()
    );
  }

  private LoginActivityRecorder recorder() {
    return recordedLoginUserIds::add;
  }

  private static SessionCookieProperties properties() {
    SessionCookieProperties properties = new SessionCookieProperties();
    properties.setAuditHmacSecret("local-test-session-audit-hmac-secret");
    return properties;
  }
}
