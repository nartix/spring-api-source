package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
  properties = {
    "app.category.admin.jdbc.enabled-systems[0]=descriptor-a",
    "uniapi.category-systems.descriptor-a.owning-module=category",
    "uniapi.category-systems.descriptor-a.node-table=test_category_a",
    "uniapi.category-systems.descriptor-a.closure-table=test_category_a_closure",
    "uniapi.category-systems.descriptor-a.ranking.enabled=true",
    "uniapi.category-systems.descriptor-a.ranking.column-name=sort_rank",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[0]=APPEND",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[1]=BEFORE_ID",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[2]=AFTER_ID",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[3]=TARGET_INDEX",
    "uniapi.category-systems.descriptor-a.ranking.sort-rank-exposed=true",
    "uniapi.category-systems.descriptor-a.fallback-order[0].property=name",
    "uniapi.category-systems.descriptor-a.fallback-order[0].column-name=name",
    "uniapi.category-systems.descriptor-a.fallback-order[0].direction=ASC",
    "uniapi.category-systems.descriptor-a.fallback-order[1].property=id",
    "uniapi.category-systems.descriptor-a.fallback-order[1].column-name=id",
    "uniapi.category-systems.descriptor-a.fallback-order[1].direction=ASC",
    "uniapi.category-systems.descriptor-missing.owning-module=category",
    "uniapi.category-systems.descriptor-missing.node-table=test_category_missing",
    "uniapi.category-systems.descriptor-missing.closure-table=test_category_missing_closure",
    "uniapi.category-systems.descriptor-missing.ranking.enabled=true",
    "uniapi.category-systems.descriptor-missing.ranking.column-name=sort_rank",
    "uniapi.category-systems.descriptor-missing.ranking.placement-modes[0]=APPEND",
    "uniapi.category-systems.descriptor-missing.ranking.sort-rank-exposed=true",
    "uniapi.category-systems.descriptor-missing.fallback-order[0].property=name",
    "uniapi.category-systems.descriptor-missing.fallback-order[0].column-name=name",
    "uniapi.category-systems.descriptor-missing.fallback-order[0].direction=ASC",
    "uniapi.category-systems.descriptor-missing.fallback-order[1].property=id",
    "uniapi.category-systems.descriptor-missing.fallback-order[1].column-name=id",
    "uniapi.category-systems.descriptor-missing.fallback-order[1].direction=ASC",
  }
)
@AutoConfigureMockMvc
class DescriptorDrivenAdminCategoryJdbcRouteIntegrationTest {

  private static final String BASE = "/api/v1/admin/category-systems/descriptor-a/categories";

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    DescriptorBackedAdminCategoryJdbcFixture.dropSchema(jdbc);
    DescriptorBackedAdminCategoryJdbcFixture.createSchema(jdbc);
  }

  @Test
  void readRoutesUseEnabledDescriptorBackedProvider() throws Exception {
    Long rootId = insertNode("Components", "components", null, true, 1_000_000_000L);
    Long gpuId = insertNode("GPUs", "gpus", rootId, true, 1_000_000_000L);
    insertPath(rootId, rootId, 0);
    insertPath(gpuId, gpuId, 0);
    insertPath(rootId, gpuId, 1);

    mvc
      .perform(get(BASE + "?parentId=" + rootId + "&q=gpu").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value(String.valueOf(gpuId)))
      .andExpect(jsonPath("$.data[0].slug").value("gpus"));

    mvc
      .perform(get(BASE + "/tree").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].slug").value("components"));

    mvc
      .perform(get(BASE + "/" + gpuId).with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.slug").value("gpus"));

    mvc
      .perform(get(BASE + "/" + gpuId + "/breadcrumb").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].slug").value("components"))
      .andExpect(jsonPath("$[1].slug").value("gpus"));

    mvc
      .perform(get(BASE + "/parent-options?q=g&limit=10").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].slug").value("gpus"));
  }

  @Test
  void mutationRoutesPersistThroughEnabledDescriptorBackedProvider() throws Exception {
    mvc
      .perform(
        post(BASE)
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson(null, "Components", "components"))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.slug").value("components"));
    Long parentId = idBySlug("components");

    mvc
      .perform(
        post(BASE)
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson(parentId, "GPUs", "gpus"))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.parentId").value(String.valueOf(parentId)));
    Long childId = idBySlug("gpus");

    mvc
      .perform(
        put(BASE + "/" + childId)
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson(parentId, "Graphics Cards", "graphics-cards"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.slug").value("graphics-cards"));

    mvc
      .perform(
        patch(BASE + "/" + childId)
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content("{\"active\":false}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(false));

    mvc
      .perform(post(BASE + "/" + childId + "/activate").with(writeJwt()))
      .andExpect(status().isNoContent());
    mvc
      .perform(
        post(BASE + "/" + childId + "/move")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"newParentId\":null}")
      )
      .andExpect(status().isNoContent());
    mvc
      .perform(
        post(BASE + "/" + childId + "/reorder")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"targetIndex\":0}")
      )
      .andExpect(status().isOk());
    mvc
      .perform(post(BASE + "/" + childId + "/deactivate").with(writeJwt()))
      .andExpect(status().isNoContent());
    mvc.perform(delete(BASE + "/" + childId).with(writeJwt())).andExpect(status().isNoContent());

    assertThat(rowExists(childId)).isFalse();
  }

  @Test
  void replaceRouteRemainsQueryVisibleAfterDirectDescriptorBinding() throws Exception {
    Long categoryId = insertNode("Components", "components", null, true, 1_000_000_000L);
    insertPath(categoryId, categoryId, 0);

    mvc
      .perform(
        put(BASE + "/" + categoryId)
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson(null, "Hardware", "hardware"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(String.valueOf(categoryId)))
      .andExpect(jsonPath("$.name").value("Hardware"))
      .andExpect(jsonPath("$.slug").value("hardware"));

    mvc
      .perform(get(BASE + "/" + categoryId).with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Hardware"))
      .andExpect(jsonPath("$.slug").value("hardware"));

    assertThat(idBySlug("hardware")).isEqualTo(categoryId);
  }

  @Test
  void descriptorBackedRouteErrorsStaySystemScopedAndClientSafe() throws Exception {
    mvc
      .perform(get(BASE).with(authorityJwt("admin:other-categories:read")))
      .andExpect(status().isForbidden());

    mvc
      .perform(
        get("/api/v1/admin/category-systems/unknown/categories").with(
          authorityJwt("admin:unknown-categories:read")
        )
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY_SYSTEM"))
      .andExpect(content().string(not(containsString("test_category_a"))));

    mvc
      .perform(
        get("/api/v1/admin/category-systems/descriptor-missing/categories").with(
          authorityJwt("admin:descriptor-missing-categories:read")
        )
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY_SYSTEM"))
      .andExpect(content().string(not(containsString("test_category_missing"))));

    mvc
      .perform(get(BASE + "/404").with(readJwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
      .andExpect(content().string(not(containsString("test_category_a"))));

    mvc
      .perform(get(BASE + "?parentId=404").with(readJwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("CATEGORY_PARENT_NOT_FOUND"))
      .andExpect(content().string(not(containsString("test_category_a"))));
  }

  private Long insertNode(String name, String slug, Long parentId, boolean active, long sortRank) {
    return DescriptorBackedAdminCategoryJdbcFixture.insertNode(
      jdbc,
      DescriptorBackedAdminCategoryJdbcFixture.TABLE_A,
      name,
      slug,
      parentId,
      active,
      sortRank
    );
  }

  private void insertPath(Long ancestorId, Long descendantId, int depth) {
    DescriptorBackedAdminCategoryJdbcFixture.insertClosure(
      jdbc,
      DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A,
      ancestorId,
      descendantId,
      depth
    );
  }

  private Long idBySlug(String slug) {
    return jdbc.queryForObject("SELECT id FROM test_category_a WHERE slug = ?", Long.class, slug);
  }

  private boolean rowExists(Long id) {
    return Boolean.TRUE.equals(
      jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM test_category_a WHERE id = ?)",
        Boolean.class,
        id
      )
    );
  }

  private static String categoryJson(Long parentId, String name, String slug) {
    String parent = parentId == null ? "null" : parentId.toString();
    return """
    {
      "parentId": %s,
      "name": "%s",
      "slug": "%s",
      "active": true
    }
    """.formatted(parent, name, slug);
  }

  private static RequestPostProcessor readJwt() {
    return authorityJwt("admin:descriptor-a-categories:read");
  }

  private static RequestPostProcessor writeJwt() {
    return authorityJwt("admin:descriptor-a-categories:write");
  }

  private static RequestPostProcessor authorityJwt(String authority) {
    return jwt().authorities(new SimpleGrantedAuthority(authority));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ContainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
    }
  }
}
