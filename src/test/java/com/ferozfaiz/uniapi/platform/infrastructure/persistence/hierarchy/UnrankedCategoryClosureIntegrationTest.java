package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
class UnrankedCategoryClosureIntegrationTest {

  @Autowired
  private ClosureTableOperations closureTables;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoBean
  private SparseRankOperations rankOperations;

  private UnrankedCategoryFixtureSchema schema;

  @BeforeEach
  void setUp() {
    schema = new UnrankedCategoryFixtureSchema(jdbc);
    schema.recreate();
  }

  @Test
  void createsMovesAndReadsUnrankedHierarchyWithFallbackOrder() {
    Long zRoot = schema.insertNode("z-root", "Z Root", null);
    Long aRoot = schema.insertNode("a-root", "A Root", null);

    closureTables.createRoot(UnrankedCategoryFixture.CLOSURE, zRoot);
    closureTables.createRoot(UnrankedCategoryFixture.CLOSURE, aRoot);

    Long beta = schema.insertNode("beta", "Beta", zRoot);
    Long alpha = schema.insertNode("alpha", "Alpha", zRoot);
    closureTables.createChild(UnrankedCategoryFixture.CLOSURE, beta, zRoot);
    closureTables.createChild(UnrankedCategoryFixture.CLOSURE, alpha, zRoot);

    assertThat(schema.rootNamesInFallbackOrder()).containsExactly("A Root", "Z Root");
    assertThat(schema.childNamesInFallbackOrder(zRoot)).containsExactly("Alpha", "Beta");

    closureTables.moveSubtree(UnrankedCategoryFixture.CLOSURE, alpha, aRoot);

    assertThat(schema.childNamesInFallbackOrder(zRoot)).containsExactly("Beta");
    assertThat(schema.childNamesInFallbackOrder(aRoot)).containsExactly("Alpha");
    assertThat(schema.treeNamesInFallbackOrder(aRoot)).containsExactly("Alpha");
    assertThat(
      closureTables.findAncestorIds(UnrankedCategoryFixture.CLOSURE, alpha)
    ).containsExactly(aRoot);
    assertThat(
      closureTables.findDescendantIds(UnrankedCategoryFixture.CLOSURE, aRoot)
    ).containsExactly(alpha);

    schema.renameNode(beta, "Gamma");
    schema.updateSlug(beta, "gamma");
    schema.setActive(beta, false);
    assertThat(schema.childNamesInFallbackOrder(zRoot)).containsExactly("Gamma");
    assertThat(schema.allNamesInFallbackOrder()).containsExactly(
      "Alpha",
      "A Root",
      "Gamma",
      "Z Root"
    );
    assertThat(schema.projectedNode(beta)).doesNotContainKey("sort_rank");
    assertThat(schema.isActive(beta)).isFalse();

    assertThat(closureTables.deleteSubtreePaths(UnrankedCategoryFixture.CLOSURE, beta)).isEqualTo(
      2
    );
    schema.deleteNode(beta);
    assertThat(closureTables.findDescendantIds(UnrankedCategoryFixture.CLOSURE, zRoot)).isEmpty();
    assertThat(schema.hasColumn("sort_rank")).isFalse();
    verifyNoInteractions(rankOperations);
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
