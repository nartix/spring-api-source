package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class AdminAuthorizationSecurityTest {

  @Test
  void adminAccessDeniedPublishesPersistentAuditEventShape() throws Exception {
    List<Object> events = new ArrayList<>();
    SessionCookieProperties properties = new SessionCookieProperties();
    properties.setAuditHmacSecret("local-test-session-audit-hmac-secret");
    MessageResolver messages = (code, args) ->
      code.equals("platform.api.auth.admin-access-denied") ? "Admin access is denied" : code;
    SessionSecurityExceptionHandlers exceptionHandlers = new SessionSecurityExceptionHandlers(
      new ObjectMapper().findAndRegisterModules(),
      messages
    );
    var handler = new AdminSessionSecurityConfiguration().adminAccessDeniedHandler(
      exceptionHandlers,
      events::add,
      new SessionIdHashingService(properties)
    );
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/users");
    request.setRequestURI("/api/v1/admin/users");
    request.setRequestedSessionId("raw-admin-session-id");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("denied"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("ADMIN_ACCESS_DENIED");
    assertThat(events).hasSize(1);
    SessionLifecycleEvent event = (SessionLifecycleEvent) events.getFirst();
    assertThat(event.eventType()).isEqualTo(SessionLifecycleEvent.Type.ADMIN_ACCESS_DENIED);
    assertThat(event.sessionIdHash()).hasSize(64);
    assertThat(event.sessionIdHash()).doesNotContain("raw-admin-session-id");
  }
}
