package com.ferozfaiz.uniapi.platform.observability;

import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String REQUEST_ID_MDC_KEY = "request.id";

  private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final String HEALTH_PATH = "/actuator/health";

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
    response.setHeader(REQUEST_ID_HEADER, requestId);

    long startedAt = System.nanoTime();
    Throwable failure = null;
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    try {
      filterChain.doFilter(request, response);
    } catch (ServletException | IOException | RuntimeException | Error exception) {
      failure = exception;
      throw exception;
    } finally {
      try {
        logCompletedRequest(request, response, startedAt, failure);
      } finally {
        MDC.remove(REQUEST_ID_MDC_KEY);
      }
    }
  }

  private static String resolveRequestId(String candidate) {
    if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }

  private static void logCompletedRequest(
    HttpServletRequest request,
    HttpServletResponse response,
    long startedAt,
    Throwable failure
  ) {
    if (HEALTH_PATH.equals(request.getRequestURI())) {
      return;
    }

    long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
    int status = failure == null ? response.getStatus() : Math.max(response.getStatus(), 500);
    LoggingEventBuilder event = eventForStatus(status, failure)
      .addKeyValue("http.method", request.getMethod())
      .addKeyValue("http.path", request.getRequestURI())
      .addKeyValue("http.status_code", status)
      .addKeyValue("http.duration_ms", durationMs);

    addPrincipal(event, request.getUserPrincipal());
    if (failure != null) {
      event.setCause(failure).addKeyValue("error.type", failure.getClass().getSimpleName());
    }
    event.log("http request completed");
  }

  private static LoggingEventBuilder eventForStatus(int status, Throwable failure) {
    if (failure != null || status >= 500) {
      return log.atError();
    }
    if (status >= 400) {
      return log.atWarn();
    }
    return log.atInfo();
  }

  private static void addPrincipal(LoggingEventBuilder event, Principal principal) {
    if (principal instanceof AuthenticatedPrincipal authenticatedPrincipal) {
      event.addKeyValue("user.id", authenticatedPrincipal.userId());
    }
  }
}
