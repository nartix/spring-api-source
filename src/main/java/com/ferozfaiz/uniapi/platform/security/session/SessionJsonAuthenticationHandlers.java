package com.ferozfaiz.uniapi.platform.security.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.iam.api.AdminAuthorities;
import com.ferozfaiz.uniapi.iam.api.auth.LoginActivityRecorder;
import com.ferozfaiz.uniapi.iam.api.auth.SessionAuthResponse;
import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class SessionJsonAuthenticationHandlers {

  public static final String SESSION_SCOPE_ATTRIBUTE = "SESSION_SCOPE";
  public static final String ADMIN_AUTHENTICATED_AT_ATTRIBUTE = "ADMIN_AUTHENTICATED_AT";

  private final ObjectMapper objectMapper;
  private final MessageResolver messages;
  private final ApplicationEventPublisher events;
  private final SessionIdHashingService sessionIdHashing;
  private final SessionCookieProperties properties;
  private final LoginActivityRecorder loginActivity;

  public SessionJsonAuthenticationHandlers(
    ObjectMapper objectMapper,
    MessageResolver messages,
    ApplicationEventPublisher events,
    SessionIdHashingService sessionIdHashing,
    SessionCookieProperties properties,
    LoginActivityRecorder loginActivity
  ) {
    this.objectMapper = objectMapper;
    this.messages = messages;
    this.events = events;
    this.sessionIdHashing = sessionIdHashing;
    this.properties = properties;
    this.loginActivity = loginActivity;
  }

  public AuthenticationSuccessHandler regularLoginSuccessHandler() {
    return (request, response, authentication) -> {
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.setAttribute(SESSION_SCOPE_ATTRIBUTE, SessionLifecycleEvent.Scope.USER.name());
      }
      recordSuccessfulLogin(authentication);
      publish(
        request,
        authentication,
        SessionLifecycleEvent.Type.USER_LOGIN_SUCCESS,
        SessionLifecycleEvent.Scope.USER,
        hashSession(session),
        null
      );
      writeSuccess(response);
    };
  }

  public AuthenticationFailureHandler regularLoginFailureHandler() {
    return (request, response, exception) -> {
      publishFailure(
        request,
        SessionLifecycleEvent.Type.USER_LOGIN_FAILURE,
        SessionLifecycleEvent.Scope.USER,
        exception
      );
      writeError(
        request,
        response,
        HttpStatus.UNAUTHORIZED,
        "LOGIN_FAILED",
        messages.get("iam.api.auth.login.failed")
      );
    };
  }

  public LogoutSuccessHandler regularLogoutSuccessHandler() {
    return (request, response, authentication) -> {
      publish(
        request,
        authentication,
        SessionLifecycleEvent.Type.USER_LOGOUT,
        SessionLifecycleEvent.Scope.USER,
        sessionIdHashing.hash(request.getRequestedSessionId()),
        null
      );
      expireCsrfCookie(response, SessionCookieScope.USER);
      writeSuccess(response);
    };
  }

  public AuthenticationSuccessHandler adminLoginSuccessHandler() {
    return (request, response, authentication) -> {
      SessionLifecycleEvent.Scope scope = adminSessionScope(authentication);
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.setAttribute(SESSION_SCOPE_ATTRIBUTE, scope.name());
        session.setAttribute(ADMIN_AUTHENTICATED_AT_ATTRIBUTE, Instant.now());
      }
      recordSuccessfulLogin(authentication);
      publish(
        request,
        authentication,
        scope == SessionLifecycleEvent.Scope.MODERATOR
          ? SessionLifecycleEvent.Type.MODERATOR_LOGIN_SUCCESS
          : SessionLifecycleEvent.Type.ADMIN_LOGIN_SUCCESS,
        scope,
        hashSession(session),
        null
      );
      writeSuccess(response);
    };
  }

  public AuthenticationFailureHandler adminLoginFailureHandler() {
    return (request, response, exception) -> {
      publishFailure(
        request,
        SessionLifecycleEvent.Type.ADMIN_LOGIN_FAILURE,
        SessionLifecycleEvent.Scope.ADMIN,
        exception
      );
      writeError(
        request,
        response,
        HttpStatus.UNAUTHORIZED,
        "ADMIN_LOGIN_FAILED",
        messages.get("iam.api.auth.admin-login.failed")
      );
    };
  }

  public LogoutSuccessHandler adminLogoutSuccessHandler() {
    return (request, response, authentication) -> {
      SessionLifecycleEvent.Scope scope = adminSessionScope(authentication);
      publish(
        request,
        authentication,
        scope == SessionLifecycleEvent.Scope.MODERATOR
          ? SessionLifecycleEvent.Type.MODERATOR_LOGOUT
          : SessionLifecycleEvent.Type.ADMIN_LOGOUT,
        scope,
        sessionIdHashing.hash(request.getRequestedSessionId()),
        null
      );
      expireCsrfCookie(response, SessionCookieScope.ADMIN);
      writeSuccess(response);
    };
  }

  private void publish(
    HttpServletRequest request,
    Authentication authentication,
    SessionLifecycleEvent.Type eventType,
    SessionLifecycleEvent.Scope scope,
    String sessionIdHash,
    String failureReason
  ) {
    AuthenticatedPrincipal principal = principal(authentication);
    events.publishEvent(
      SessionLifecycleEvent.now(
        eventType,
        scope,
        principal == null ? null : principal.userId(),
        principal == null ? null : principal.username(),
        sessionIdHash,
        request.getRemoteAddr(),
        request.getHeader("User-Agent"),
        failureReason
      )
    );
  }

  private void publishFailure(
    HttpServletRequest request,
    SessionLifecycleEvent.Type eventType,
    SessionLifecycleEvent.Scope scope,
    AuthenticationException exception
  ) {
    events.publishEvent(
      SessionLifecycleEvent.now(
        eventType,
        scope,
        null,
        null,
        null,
        request.getRemoteAddr(),
        request.getHeader("User-Agent"),
        exception.getClass().getSimpleName()
      )
    );
  }

  private String hashSession(HttpSession session) {
    return session == null ? null : sessionIdHashing.hash(session.getId());
  }

  private void recordSuccessfulLogin(Authentication authentication) {
    AuthenticatedPrincipal principal = principal(authentication);
    if (principal != null) {
      loginActivity.recordSuccessfulLogin(principal.userId());
    }
  }

  private static AuthenticatedPrincipal principal(Authentication authentication) {
    if (
      authentication != null &&
      authentication.getPrincipal() instanceof AuthenticatedPrincipal principal
    ) {
      return principal;
    }
    return null;
  }

  private static SessionLifecycleEvent.Scope adminSessionScope(Authentication authentication) {
    return AdminAuthorities.isModerator(authentication) && !AdminAuthorities.isAdmin(authentication)
      ? SessionLifecycleEvent.Scope.MODERATOR
      : SessionLifecycleEvent.Scope.ADMIN;
  }

  private void expireCsrfCookie(HttpServletResponse response, SessionCookieScope scope) {
    Cookie cookie = new Cookie(properties.csrfCookieName(scope), "");
    cookie.setPath(properties.sessionCookiePath(scope));
    cookie.setMaxAge(0);
    cookie.setHttpOnly(false);
    cookie.setSecure(properties.secure());
    cookie.setAttribute("SameSite", properties.sameSite());
    response.addCookie(cookie);
  }

  private void writeSuccess(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.OK.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getOutputStream(), SessionAuthResponse.ok());
  }

  private void writeError(
    HttpServletRequest request,
    HttpServletResponse response,
    HttpStatus status,
    String code,
    String message
  ) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
      response.getOutputStream(),
      ApiErrorResponse.of(code, message, request.getRequestURI())
    );
  }
}
