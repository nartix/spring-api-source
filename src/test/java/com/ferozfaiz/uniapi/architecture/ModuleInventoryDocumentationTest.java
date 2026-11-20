package com.ferozfaiz.uniapi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.UniapiApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleInventoryDocumentationTest {

  private static final String START = "<!-- implemented-modules:start -->";
  private static final String END = "<!-- implemented-modules:end -->";

  @Test
  void documentedModuleInventoryMatchesSpringModulith() throws IOException {
    String guide = Files.readString(Path.of("docs/architecture.md"));

    assertThat(guide).contains(START, END);

    String inventory = guide.substring(guide.indexOf(START) + START.length(), guide.indexOf(END));
    Set<String> documentedModules = Arrays.stream(inventory.split("\\R"))
      .map(String::trim)
      .filter(line -> line.startsWith("| `"))
      .map(line -> line.substring(3, line.indexOf('`', 3)))
      .collect(Collectors.toSet());
    Set<String> implementedModules = ApplicationModules.of(UniapiApplication.class)
      .stream()
      .map(module -> module.getIdentifier().toString())
      .collect(Collectors.toSet());

    assertThat(documentedModules).containsExactlyInAnyOrderElementsOf(implementedModules);
  }
}
