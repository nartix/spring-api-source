package com.ferozfaiz.uniapi.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class HttpRequestLoggingFilterTest {

  private final HttpRequestLoggingFilter filter = new HttpRequestLoggingFilter();

  @AfterEach
  void requestIdIsCleared() {
    assertThat(MDC.get(HttpRequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void echoesSafeClientRequestIdAndAddsItToMdc() throws Exception {
    MockHttpServletRequest request = request("GET", "/api/v1/users");
    request.addHeader(HttpRequestLoggingFilter.REQUEST_ID_HEADER, "client-request-1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) ->
      assertThat(MDC.get(HttpRequestLoggingFilter.REQUEST_ID_MDC_KEY)).isEqualTo("client-request-1")
    );

    assertThat(response.getHeader(HttpRequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo(
      "client-request-1"
    );
  }

  @Test
  void replacesUnsafeClientRequestId() throws Exception {
    MockHttpServletRequest request = request("GET", "/api/v1/users");
    request.addHeader(HttpRequestLoggingFilter.REQUEST_ID_HEADER, "bad request id");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) ->
      assertThat(MDC.get(HttpRequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNotEqualTo(
        "bad request id"
      )
    );

    assertThat(response.getHeader(HttpRequestLoggingFilter.REQUEST_ID_HEADER))
      .isNotBlank()
      .isNotEqualTo("bad request id");
  }

  @Test
  void logsCompletedRequestsWithoutSensitiveRequestHeaders(CapturedOutput output) throws Exception {
    try (LogLevelOverride ignored = LogLevelOverride.info(HttpRequestLoggingFilter.class)) {
      MockHttpServletRequest request = request("POST", "/api/v1/users");
      request.addHeader("Authorization", "Bearer secret-token");
      request.setUserPrincipal(
        new AuthenticatedPrincipal(
          42L,
          "raw-username-never-log",
          Set.of("ADMIN"),
          Set.of("users:write")
        )
      );
      MockHttpServletResponse response = new MockHttpServletResponse();
      response.setStatus(201);

      filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

      assertThat(output).contains("http request completed");
      assertThat(output).doesNotContain("secret-token");
      assertThat(output).doesNotContain("Bearer");
      assertThat(output).doesNotContain("raw-username-never-log");
    }
  }

  @Test
  void skipsHealthEndpointAccessLog(CapturedOutput output) throws Exception {
    MockHttpServletRequest request = request("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

    assertThat(output).doesNotContain("http request completed");
  }

  private static MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setRequestURI(path);
    return request;
  }

  private record LogLevelOverride(
    ch.qos.logback.classic.Logger logger,
    ch.qos.logback.classic.Level originalLevel
  ) implements AutoCloseable {
    static LogLevelOverride info(Class<?> loggerType) {
      ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(loggerType);
      ch.qos.logback.classic.Level originalLevel = logger.getLevel();
      logger.setLevel(ch.qos.logback.classic.Level.INFO);
      return new LogLevelOverride(logger, originalLevel);
    }

    @Override
    public void close() {
      logger.setLevel(originalLevel);
    }
  }
}
