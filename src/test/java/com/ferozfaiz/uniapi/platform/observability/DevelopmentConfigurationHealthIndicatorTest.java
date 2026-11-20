package com.ferozfaiz.uniapi.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.env.MockEnvironment;

class DevelopmentConfigurationHealthIndicatorTest {

  @TempDir
  Path directory;

  @Test
  void healthTracksLoadedContentAndFreshnessInsteadOfNewerUnloadedGeneration() throws Exception {
    var file = directory.resolve("render.properties");
    var content = "setting=synthetic\n";
    Files.writeString(file, "# heartbeat one\n" + content);
    var digest = HexFormat.of().formatHex(
      MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))
    );
    var environment = new MockEnvironment()
      .withProperty("DEV_LOADED_DIGEST", digest)
      .withProperty("DEV_RENDER_FILE", file.toString())
      .withProperty("DEV_CONFIG_MAX_AGE", "30");
    var indicator = new DevelopmentConfigurationHealthIndicator(environment);
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    Files.writeString(file, "# heartbeat two\n" + content);
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    Files.writeString(file, "setting=changed\n");
    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    Files.writeString(file, content);
    Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(31)));
    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    Files.delete(file);
    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void developmentBindLoggingLevelsRemainStrings() throws Exception {
    var source = new org.springframework.boot.env.YamlPropertySourceLoader()
      .load("dev", new org.springframework.core.io.ClassPathResource("application-dev.yaml"))
      .getFirst();
    assertThat(source.getProperty("logging.level[org.hibernate.orm.jdbc.bind]")).isEqualTo("OFF");
    assertThat(
      source.getProperty("logging.level[org.springframework.jdbc.core.StatementCreatorUtils]")
    ).isEqualTo("OFF");
  }
}
