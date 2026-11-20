package com.ferozfaiz.uniapi.platform.security;

import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventLogger {

  private static final Logger log = LoggerFactory.getLogger(AuthenticationEventLogger.class);

  @EventListener
  public void logAuthenticationSuccess(AuthenticationSuccessEvent event) {
    LoggingEventBuilder builder = log
      .atInfo()
      .addKeyValue("security.event", "authentication.success")
      .addKeyValue("authentication.type", event.getAuthentication().getClass().getSimpleName());
    addAuthenticatedPrincipal(builder, event.getAuthentication());
    builder.log("authentication succeeded");
  }

  @EventListener
  public void logAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
    log
      .atWarn()
      .addKeyValue("security.event", "authentication.failure")
      .addKeyValue("authentication.type", event.getAuthentication().getClass().getSimpleName())
      .addKeyValue("failure.type", event.getException().getClass().getSimpleName())
      .log("authentication failed");
  }

  private static void addAuthenticatedPrincipal(
    LoggingEventBuilder builder,
    Authentication authentication
  ) {
    if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
      builder.addKeyValue("user.id", principal.userId());
    }
  }
}
