package com.ferozfaiz.uniapi.platform.observability;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Reports freshness of the immutable configuration generation loaded by this JVM. */
@Component
@ConditionalOnProperty(name = "DEV_LOADED_GENERATION")
final class DevelopmentConfigurationHealthIndicator implements HealthIndicator {

  private final String digest;
  private final Path render;
  private final long maxAgeMillis;

  DevelopmentConfigurationHealthIndicator(Environment environment) {
    digest = environment.getRequiredProperty("DEV_LOADED_DIGEST");
    render = Path.of(environment.getRequiredProperty("DEV_RENDER_FILE"));
    maxAgeMillis = environment.getRequiredProperty("DEV_CONFIG_MAX_AGE", Long.class) * 1000;
  }

  @Override
  public Health health() {
    try {
      if (!Files.isRegularFile(render, LinkOption.NOFOLLOW_LINKS) || Files.size(render) > 1048576) {
        return Health.down().build();
      }
      var age = Instant.now().toEpochMilli() - Files.getLastModifiedTime(render).toMillis();
      var raw = Files.readString(render);
      var content = java.util.Arrays.stream(raw.split("\n", -1))
        .filter(line -> !line.startsWith("#"))
        .collect(java.util.stream.Collectors.joining("\n"));
      var actual = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))
      );
      return age >= -2000 && age <= maxAgeMillis && digest.equals(actual)
        ? Health.up().build()
        : Health.down().build();
    } catch (Exception ignored) {
      return Health.down().build();
    }
  }
}
