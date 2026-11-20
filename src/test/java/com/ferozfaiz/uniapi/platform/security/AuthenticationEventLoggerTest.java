package com.ferozfaiz.uniapi.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

@ExtendWith(OutputCaptureExtension.class)
class AuthenticationEventLoggerTest {

  private final AuthenticationEventLogger logger = new AuthenticationEventLogger();

  @Test
  void logsAuthenticationSuccessWithoutUsername(CapturedOutput output) {
    try (LogLevelOverride ignored = LogLevelOverride.info(AuthenticationEventLogger.class)) {
      TestingAuthenticationToken authentication = new TestingAuthenticationToken(
        new AuthenticatedPrincipal(
          42L,
          "raw-username-never-log",
          Set.of("ADMIN"),
          Set.of("users:read")
        ),
        null
      );

      logger.logAuthenticationSuccess(new AuthenticationSuccessEvent(authentication));

      assertThat(output).contains("authentication succeeded");
      assertThat(output).doesNotContain("raw-username-never-log");
    }
  }

  @Test
  void logsAuthenticationFailureWithoutRawLogin(CapturedOutput output) {
    TestingAuthenticationToken authentication = new TestingAuthenticationToken(
      "feroz@example.com",
      "secret"
    );

    logger.logAuthenticationFailure(
      new AuthenticationFailureBadCredentialsEvent(
        authentication,
        new BadCredentialsException("bad credentials")
      )
    );

    assertThat(output).contains("authentication failed");
    assertThat(output).doesNotContain("feroz@example.com");
    assertThat(output).doesNotContain("secret");
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
