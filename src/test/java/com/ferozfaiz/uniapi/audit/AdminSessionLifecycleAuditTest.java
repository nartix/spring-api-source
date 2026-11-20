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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminSessionLifecycleAuditTest {

  private final List<Object> events = new ArrayList<>();
  private final List<Long> recordedLoginUserIds = new ArrayList<>();
  private final SessionCookieProperties properties = properties();
  private final SessionJsonAuthenticationHandlers handlers = handlers();

  @Test
  void adminLoginSuccessPublishesAdminEvent() throws Exception {
    MockHttpServletRequest request = request("/api/v1/admin/auth/login");
    request.getSession(true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .adminLoginSuccessHandler()
      .onAuthenticationSuccess(request, response, authentication("ADMIN", "ROLE_ADMIN"));

    SessionLifecycleEvent event = singleEvent();
    assertThat(event.eventType()).isEqualTo(SessionLifecycleEvent.Type.ADMIN_LOGIN_SUCCESS);
    assertThat(event.sessionScope()).isEqualTo(SessionLifecycleEvent.Scope.ADMIN);
    assertThat(event.sessionIdHash()).hasSize(64);
    assertThat(recordedLoginUserIds).containsExactly(1L);
  }

  @Test
  void moderatorLoginSuccessPublishesModeratorEvent() throws Exception {
    MockHttpServletRequest request = request("/api/v1/admin/auth/login");
    request.getSession(true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .adminLoginSuccessHandler()
      .onAuthenticationSuccess(request, response, authentication("MODERATOR", "ROLE_MODERATOR"));

    SessionLifecycleEvent event = singleEvent();
    assertThat(event.eventType()).isEqualTo(SessionLifecycleEvent.Type.MODERATOR_LOGIN_SUCCESS);
    assertThat(event.sessionScope()).isEqualTo(SessionLifecycleEvent.Scope.MODERATOR);
    assertThat(recordedLoginUserIds).containsExactly(1L);
  }

  @Test
  void adminLoginFailurePublishesGenericFailureEvent() throws Exception {
    MockHttpServletRequest request = request("/api/v1/admin/auth/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .adminLoginFailureHandler()
      .onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

    SessionLifecycleEvent event = singleEvent();
    assertThat(event.eventType()).isEqualTo(SessionLifecycleEvent.Type.ADMIN_LOGIN_FAILURE);
    assertThat(event.userId()).isNull();
    assertThat(event.username()).isNull();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("ADMIN_LOGIN_FAILED");
    assertThat(recordedLoginUserIds).isEmpty();
  }

  @Test
  void moderatorLogoutPublishesModeratorLogoutEvent() throws Exception {
    MockHttpServletRequest request = request("/api/v1/admin/auth/logout");
    request.setRequestedSessionId("raw-admin-session-id");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .adminLogoutSuccessHandler()
      .onLogoutSuccess(request, response, authentication("MODERATOR", "ROLE_MODERATOR"));

    SessionLifecycleEvent event = singleEvent();
    assertThat(event.eventType()).isEqualTo(SessionLifecycleEvent.Type.MODERATOR_LOGOUT);
    assertThat(event.sessionIdHash()).hasSize(64);
    assertThat(event.sessionIdHash()).doesNotContain("raw-admin-session-id");
    assertThat(response.getCookie(properties.adminCsrfCookieName()).getMaxAge()).isZero();
    assertThat(response.getCookie(properties.userCsrfCookieName())).isNull();
  }

  private SessionLifecycleEvent singleEvent() {
    assertThat(events).hasSize(1);
    SessionLifecycleEvent event = (SessionLifecycleEvent) events.getFirst();
    events.clear();
    return event;
  }

  private static TestingAuthenticationToken authentication(String role, String authority) {
    return new TestingAuthenticationToken(
      new AuthenticatedPrincipal(1L, "feroz", Set.of(role), Set.of()),
      null,
      List.of(new SimpleGrantedAuthority(authority))
    );
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
      code.equals("iam.api.auth.admin-login.failed") ? "Invalid username or password" : code;
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
