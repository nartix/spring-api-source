package com.ferozfaiz.uniapi.platform.security.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtSessionCredentialConflictFilter extends OncePerRequestFilter {

  private final String userSessionCookieName;
  private final ObjectMapper objectMapper;
  private final MessageResolver messages;

  public JwtSessionCredentialConflictFilter(
    String userSessionCookieName,
    ObjectMapper objectMapper,
    MessageResolver messages
  ) {
    this.userSessionCookieName = userSessionCookieName;
    this.objectMapper = objectMapper;
    this.messages = messages;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    if (!hasBearerToken(request) || !hasUserSessionCookie(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication sessionAuthentication = sessionAuthentication(request);
    Authentication currentAuthentication = SecurityContextHolder.getContext().getAuthentication();
    if (conflicts(currentAuthentication, sessionAuthentication)) {
      SecurityContextHolder.clearContext();
      writeConflict(request, response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean hasUserSessionCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return false;
    }
    for (Cookie cookie : cookies) {
      if (
        userSessionCookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())
      ) {
        return true;
      }
    }
    return false;
  }

  private Authentication sessionAuthentication(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    Object context = session.getAttribute(
      HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
    );
    if (context instanceof SecurityContext securityContext) {
      return securityContext.getAuthentication();
    }
    return null;
  }

  private static boolean conflicts(
    Authentication currentAuthentication,
    Authentication sessionAuthentication
  ) {
    String currentName = principalName(currentAuthentication);
    String sessionName = principalName(sessionAuthentication);
    return (
      StringUtils.hasText(currentName) &&
      StringUtils.hasText(sessionName) &&
      !currentName.equals(sessionName)
    );
  }

  private static String principalName(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
      return principal.username();
    }
    return authentication.getName();
  }

  private static boolean hasBearerToken(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    return (
      StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
    );
  }

  private void writeConflict(HttpServletRequest request, HttpServletResponse response)
    throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
      response.getOutputStream(),
      ApiErrorResponse.of(
        "AUTHENTICATION_CREDENTIAL_CONFLICT",
        messages.get("platform.api.auth.credential-conflict"),
        request.getRequestURI()
      )
    );
  }
}
