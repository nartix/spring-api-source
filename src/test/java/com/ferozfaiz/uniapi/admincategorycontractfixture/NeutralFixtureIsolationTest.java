package com.ferozfaiz.uniapi.admincategorycontractfixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.UniapiApplication;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
  classes = UniapiApplication.class,
  properties = "spring.jpa.hibernate.ddl-auto=validate"
)
class NeutralFixtureIsolationTest {

  private static final String FIXTURE_PACKAGE = "com.ferozfaiz.uniapi.admincategorycontractfixture";
  private static final String FLAT_KEY = "neutral-flat";
  private static final String RESTRICTED_KEY = "neutral-restricted";
  private static final String RESTRICTED_TABLE = "admin_category_contract_restricted_node";
  private static final List<String> FIXTURE_PERMISSIONS = List.of(
    "admin:neutral-flat-categories:read",
    "admin:neutral-restricted-categories:read",
    "admin:neutral-restricted-categories:write"
  );
  private static final List<String> PRODUCTION_MARKERS = List.of(
    FIXTURE_PACKAGE,
    "NeutralAdminCategory",
    FLAT_KEY,
    RESTRICTED_KEY,
    RESTRICTED_TABLE,
    FIXTURE_PERMISSIONS.get(0),
    FIXTURE_PERMISSIONS.get(1),
    FIXTURE_PERMISSIONS.get(2)
  );
  private static final List<Path> PRODUCTION_ROOTS = List.of(
    Path.of("src/main/java"),
    Path.of("src/main/resources"),
    Path.of("docs/api/http")
  );
  private static final ObjectMapper JSON = new ObjectMapper();

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:latest");

  @Autowired
  private ConfigurableApplicationContext context;

  @Autowired
  private ConfigurableEnvironment environment;

  @Autowired
  private AdminCategorySystemRegistry registrations;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void normalProductionStartupPublishesNoNeutralRegistrationBeanOrRoute() {
    assertThat(registrations.registrations())
      .extracting(registration -> registration.systemKey().orElseThrow())
      .containsExactly("catalogue");
    assertThat(registrations.findRegistration(FLAT_KEY)).isEmpty();
    assertThat(registrations.findRegistration(RESTRICTED_KEY)).isEmpty();

    assertThat(neutralBeanDefinitions()).isEmpty();
    assertThat(neutralHandlerMappings()).isEmpty();
  }

  @Test
  void generatedOpenApiContainsNoNeutralFixtureRouteOrSchema() throws Exception {
    String document = mvc
      .perform(get("/v3/api-docs"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    JsonNode openApi = JSON.readTree(document);

    assertThat(pathNames(openApi)).noneMatch(NeutralFixtureIsolationTest::hasNeutralApiMarker);
    assertThat(schemaNames(openApi)).noneMatch(NeutralFixtureIsolationTest::hasNeutralApiMarker);
    assertThat(document.toLowerCase(Locale.ROOT)).doesNotContain(
      FIXTURE_PACKAGE.toLowerCase(Locale.ROOT),
      FLAT_KEY,
      RESTRICTED_KEY,
      RESTRICTED_TABLE,
      FIXTURE_PERMISSIONS.get(0),
      FIXTURE_PERMISSIONS.get(1),
      FIXTURE_PERMISSIONS.get(2)
    );
  }

  @Test
  void productionConfigurationPermissionsAndSchemaContainNoNeutralArtifact() throws IOException {
    assertThat(neutralPropertyNames()).isEmpty();
    assertThat(productionSourceMatches()).isEmpty();

    assertThat(
      jdbc.queryForList(
        "SELECT code FROM iam_permission WHERE code IN (?, ?, ?) ORDER BY code",
        String.class,
        FIXTURE_PERMISSIONS.get(0),
        FIXTURE_PERMISSIONS.get(1),
        FIXTURE_PERMISSIONS.get(2)
      )
    ).isEmpty();
    assertThat(
      jdbc.queryForList(
        """
        SELECT table_schema || '.' || table_name
        FROM information_schema.tables
        WHERE table_name = ?
        ORDER BY table_schema
        """,
        String.class,
        RESTRICTED_TABLE
      )
    ).isEmpty();
  }

  private List<String> neutralBeanDefinitions() {
    List<String> matches = new ArrayList<>();
    for (String beanName : context.getBeanDefinitionNames()) {
      Class<?> beanType = context.getType(beanName, false);
      String typeName = beanType == null ? "" : ClassUtils.getUserClass(beanType).getName();
      String definitionClass = "";
      String definitionResource = "";
      if (context.getBeanFactory().containsBeanDefinition(beanName)) {
        BeanDefinition definition = context.getBeanFactory().getBeanDefinition(beanName);
        definitionClass = String.valueOf(definition.getBeanClassName());
        definitionResource = String.valueOf(definition.getResourceDescription());
      }
      String observation = String.join(
        " | ",
        beanName,
        typeName,
        definitionClass,
        definitionResource
      );
      if (hasFixtureMarker(observation)) {
        matches.add(observation);
      }
    }
    return matches;
  }

  private List<String> neutralHandlerMappings() {
    List<String> matches = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping
      .getHandlerMethods()
      .entrySet()) {
      String handlerType = ClassUtils.getUserClass(entry.getValue().getBeanType()).getName();
      Set<String> paths = paths(entry.getKey());
      String observation = handlerType + "#" + entry.getValue().getMethod().getName() + " " + paths;
      if (hasFixtureMarker(observation)) {
        matches.add(observation);
      }
    }
    return matches;
  }

  private List<String> neutralPropertyNames() {
    Set<String> matches = new LinkedHashSet<>();
    environment.getPropertySources().forEach(propertySource -> {
      if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
        Arrays.stream(enumerable.getPropertyNames())
          .filter(NeutralFixtureIsolationTest::hasFixtureMarker)
          .forEach(matches::add);
      }
    });
    return List.copyOf(matches);
  }

  private static List<String> productionSourceMatches() throws IOException {
    List<String> matches = new ArrayList<>();
    for (Path root : PRODUCTION_ROOTS) {
      if (!Files.exists(root)) {
        continue;
      }
      List<Path> files;
      try (var paths = Files.walk(root)) {
        files = paths.filter(Files::isRegularFile).sorted().toList();
      }
      for (Path file : files) {
        String content = Files.readString(file);
        for (String marker : PRODUCTION_MARKERS) {
          if (content.toLowerCase(Locale.ROOT).contains(marker.toLowerCase(Locale.ROOT))) {
            matches.add(file + " contains " + marker);
          }
        }
      }
    }
    return matches;
  }

  private static List<String> pathNames(JsonNode openApi) {
    return openApi.path("paths").properties().stream().map(Map.Entry::getKey).toList();
  }

  private static List<String> schemaNames(JsonNode openApi) {
    return openApi
      .path("components")
      .path("schemas")
      .properties()
      .stream()
      .map(Map.Entry::getKey)
      .toList();
  }

  private static Set<String> paths(RequestMappingInfo mapping) {
    if (mapping.getPathPatternsCondition() != null) {
      return mapping.getPathPatternsCondition().getPatternValues();
    }
    if (mapping.getPatternsCondition() != null) {
      return mapping.getPatternsCondition().getPatterns();
    }
    return Set.of();
  }

  private static boolean hasFixtureMarker(String value) {
    String normalized = value.toLowerCase(Locale.ROOT);
    return (
      normalized.contains(FIXTURE_PACKAGE.toLowerCase(Locale.ROOT)) ||
      normalized.contains("neutraladmincategory") ||
      normalized.contains(FLAT_KEY) ||
      normalized.contains(RESTRICTED_KEY) ||
      normalized.contains(RESTRICTED_TABLE)
    );
  }

  private static boolean hasNeutralApiMarker(String value) {
    return (value.toLowerCase(Locale.ROOT).contains("neutral") || hasFixtureMarker(value));
  }
}
