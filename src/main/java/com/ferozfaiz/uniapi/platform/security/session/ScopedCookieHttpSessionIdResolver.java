package com.ferozfaiz.uniapi.platform.security.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.util.StringUtils;

public class ScopedCookieHttpSessionIdResolver implements HttpSessionIdResolver {

  private final SessionCookieProperties properties;
  private final Map<SessionCookieScope, CookieHttpSessionIdResolver> resolvers;

  public ScopedCookieHttpSessionIdResolver(SessionCookieProperties properties) {
    this.properties = properties;
    this.resolvers = new EnumMap<>(SessionCookieScope.class);
    this.resolvers.put(SessionCookieScope.USER, resolver(SessionCookieScope.USER));
    this.resolvers.put(SessionCookieScope.ADMIN, resolver(SessionCookieScope.ADMIN));
  }

  @Override
  public List<String> resolveSessionIds(HttpServletRequest request) {
    return resolver(request).resolveSessionIds(request);
  }

  @Override
  public void setSessionId(
    HttpServletRequest request,
    HttpServletResponse response,
    String sessionId
  ) {
    resolver(request).setSessionId(request, response, sessionId);
  }

  @Override
  public void expireSession(HttpServletRequest request, HttpServletResponse response) {
    resolver(request).expireSession(request, response);
  }

  public SessionCookieScope resolveScope(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String scopedPath = StringUtils.hasText(contextPath)
      ? requestUri.substring(contextPath.length())
      : requestUri;
    return isAdminPath(scopedPath) ? SessionCookieScope.ADMIN : SessionCookieScope.USER;
  }

  private boolean isAdminPath(String scopedPath) {
    String adminPath = properties.adminCookiePath();
    return scopedPath.equals(adminPath) || scopedPath.startsWith(adminPath + "/");
  }

  private CookieHttpSessionIdResolver resolver(HttpServletRequest request) {
    return resolvers.get(resolveScope(request));
  }

  private CookieHttpSessionIdResolver resolver(SessionCookieScope scope) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(properties.sessionCookieName(scope));
    serializer.setCookiePath(properties.sessionCookiePath(scope));
    serializer.setSameSite(properties.sameSite());
    serializer.setUseHttpOnlyCookie(true);
    serializer.setUseSecureCookie(properties.secure());
    serializer.setUseBase64Encoding(false);

    CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
    resolver.setCookieSerializer(serializer);
    return resolver;
  }
}
