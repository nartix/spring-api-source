package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
class RankedSparseOrderingStrategyTest {

  @Autowired
  private SparseRankOperations operations;

  @Autowired
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc.execute("DROP TABLE IF EXISTS test_ranked_strategy_category");
    jdbc.execute(
      """
      CREATE TABLE test_ranked_strategy_category (
        id BIGSERIAL PRIMARY KEY,
        parent_id BIGINT,
        name VARCHAR(255) NOT NULL,
        sort_rank BIGINT
      )
      """
    );
  }

  @Test
  void rejectsUnsupportedPlacementModesBeforeAllocating() {
    RankedSparseOrderingStrategy strategy = new RankedSparseOrderingStrategy(
      descriptor(List.of(PlacementMode.APPEND)),
      operations
    );

    assertThatThrownBy(() -> strategy.validatePlacement(SparseOrderTarget.from(10L, null, null)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not supported");
  }

  @Test
  void rejectsPlacementTargetEqualToMovedCategory() {
    RankedSparseOrderingStrategy strategy = strategy();

    assertThatThrownBy(() ->
      strategy.assignRank(10L, null, SparseOrderTarget.from(10L, null, null))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("moved category");

    assertThatThrownBy(() ->
      strategy.assignRank(10L, null, SparseOrderTarget.from(null, 10L, null))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("moved category");
  }

  @Test
  void validatesSiblingTargetWithinDestinationParent() {
    Long root = insert("Root", null, 1_000_000_000L);
    Long otherParentChild = insert("Other Parent Child", 99L, 1_000_000_000L);
    RankedSparseOrderingStrategy strategy = strategy();

    assertThatThrownBy(() ->
      strategy.assignRank(root, null, SparseOrderTarget.from(otherParentChild, null, null))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("sibling group");
  }

  @Test
  void targetIndexBoundsExcludeSameParentMovedRow() {
    insert("First", null, 1_000_000_000L);
    Long moving = insert("Moving", null, 2_000_000_000L);
    insert("Second", null, 3_000_000_000L);
    RankedSparseOrderingStrategy strategy = strategy();

    assertThatThrownBy(() ->
      strategy.assignRank(moving, null, SparseOrderTarget.from(null, null, 3))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("targetIndex");
  }

  @Test
  void sameParentSparseGapReorderUpdatesOnlyMovedRow() {
    Long first = insert("First", null, 1_000_000_000L);
    Long moving = insert("Moving", null, 2_000_000_000L);
    Long second = insert("Second", null, 4_000_000_000L);
    RankedSparseOrderingStrategy strategy = strategy();

    RankAssignmentResult result = strategy.assignRank(
      moving,
      null,
      SparseOrderTarget.from(null, first, null)
    );

    assertThat(result.assignedRank()).isEqualTo(2_500_000_000L);
    assertThat(result.rebalancePerformed()).isFalse();
    assertThat(result.updatedRows()).isEqualTo(1);
    assertThat(ranks(List.of(first, moving, second))).containsExactly(
      1_000_000_000L,
      2_500_000_000L,
      4_000_000_000L
    );
  }

  private RankedSparseOrderingStrategy strategy() {
    return new RankedSparseOrderingStrategy(
      descriptor(
        List.of(
          PlacementMode.APPEND,
          PlacementMode.BEFORE_ID,
          PlacementMode.AFTER_ID,
          PlacementMode.TARGET_INDEX
        )
      ),
      operations
    );
  }

  private Long insert(String name, Long parentId, Long rank) {
    return jdbc.queryForObject(
      """
      INSERT INTO test_ranked_strategy_category (name, parent_id, sort_rank)
      VALUES (?, ?, ?)
      RETURNING id
      """,
      Long.class,
      name,
      parentId,
      rank
    );
  }

  private Long rank(Long id) {
    return jdbc.queryForObject(
      "SELECT sort_rank FROM test_ranked_strategy_category WHERE id = ?",
      Long.class,
      id
    );
  }

  private List<Long> ranks(List<Long> ids) {
    return ids.stream().map(this::rank).toList();
  }

  private static CategorySystemDescriptor descriptor(List<PlacementMode> placementModes) {
    return new CategorySystemDescriptor(
      "ranked-strategy-test-fixture",
      "test",
      ClosureTableDescriptor.of(
        "test_ranked_strategy_category",
        "test_ranked_strategy_category_closure"
      ),
      RankingCapabilityDescriptor.ranked(
        "sort_rank",
        1_000_000_000L,
        1_000_000_000L,
        1_000,
        DisabledRankingRequestPolicy.REJECT,
        placementModes,
        true
      ),
      new FallbackOrderDescriptor(
        List.of(
          new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
          new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
        )
      ),
      Optional.of(
        new SparseRankTableDescriptor(
          "test_ranked_strategy_category",
          "id",
          "parent_id",
          "sort_rank"
        )
      )
    );
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
