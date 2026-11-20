package com.ferozfaiz.uniapi.platform.security.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SessionSecurityExceptionHandlers {

  private final ObjectMapper objectMapper;
  private final MessageResolver messages;

  public SessionSecurityExceptionHandlers(ObjectMapper objectMapper, MessageResolver messages) {
    this.objectMapper = objectMapper;
    this.messages = messages;
  }

  public AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, exception) ->
      writeError(
        response,
        HttpStatus.UNAUTHORIZED,
        "UNAUTHENTICATED",
        messages.get("platform.api.auth.unauthenticated"),
        request
      );
  }

  public AccessDeniedHandler accessDeniedHandler() {
    return (request, response, exception) ->
      writeError(
        response,
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        messages.get("platform.api.auth.forbidden"),
        request
      );
  }

  public AccessDeniedHandler adminAccessDeniedHandler() {
    return (request, response, exception) ->
      writeError(
        response,
        HttpStatus.FORBIDDEN,
        "ADMIN_ACCESS_DENIED",
        messages.get("platform.api.auth.admin-access-denied"),
        request
      );
  }

  public void writeError(
    HttpServletResponse response,
    HttpStatus status,
    String code,
    String message,
    HttpServletRequest request
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
