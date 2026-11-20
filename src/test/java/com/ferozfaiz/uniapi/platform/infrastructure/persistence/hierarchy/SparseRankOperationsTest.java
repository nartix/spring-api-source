package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
class SparseRankOperationsTest {

  @Autowired
  private SparseRankOperations operations;

  @Autowired
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc.execute("DROP TABLE IF EXISTS test_ranked_category");
    jdbc.execute(
      """
      CREATE TABLE test_ranked_category (
        id BIGSERIAL PRIMARY KEY,
        parent_id BIGINT,
        name VARCHAR(255) NOT NULL,
        sort_rank BIGINT
      )
      """
    );
  }

  @Test
  void appendsAfterExistingSiblingsWithoutChangingTheirRanks() {
    Long first = insert("First", null, 1_000_000_000L);
    Long second = insert("Second", null, 2_000_000_000L);
    Long inserted = insert("Inserted", null, null);

    RankAssignmentResult result = operations.assignRank(
      descriptor(1_000),
      inserted,
      null,
      SparseOrderTarget.append()
    );

    assertThat(result.assignedRank()).isEqualTo(3_000_000_000L);
    assertThat(result.updatedRows()).isEqualTo(1);
    assertThat(rank(first)).isEqualTo(1_000_000_000L);
    assertThat(rank(second)).isEqualTo(2_000_000_000L);
    assertThat(rank(inserted)).isEqualTo(3_000_000_000L);
  }

  @Test
  void allocatesMidpointForBeforeAndAfterPlacement() {
    Long first = insert("First", null, 1_000_000_000L);
    Long second = insert("Second", null, 2_000_000_000L);
    Long beforeSecond = insert("Before Second", null, null);
    Long afterFirst = insert("After First", null, null);

    operations.assignRank(
      descriptor(1_000),
      beforeSecond,
      null,
      SparseOrderTarget.from(second, null, null)
    );
    operations.assignRank(
      descriptor(1_000),
      afterFirst,
      null,
      SparseOrderTarget.from(null, first, null)
    );

    assertThat(rank(beforeSecond)).isEqualTo(1_500_000_000L);
    assertThat(rank(afterFirst)).isEqualTo(1_250_000_000L);
  }

  @Test
  void rejectsOutOfRangeTargetIndex() {
    insert("First", null, 1_000_000_000L);

    assertThatThrownBy(() ->
      operations.allocateRank(descriptor(1_000), null, SparseOrderTarget.from(null, null, 2))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("targetIndex");
  }

  @Test
  void allocatesTargetIndexForFirstMiddleAndAppendAtCount() {
    Long first = insert("First", null, 2_000_000_000L);
    Long second = insert("Second", null, 4_000_000_000L);
    Long atStart = insert("At Start", null, null);
    Long atMiddle = insert("At Middle", null, null);
    Long atEnd = insert("At End", null, null);

    operations.assignRank(descriptor(1_000), atStart, null, SparseOrderTarget.from(null, null, 0));
    operations.assignRank(descriptor(1_000), atMiddle, null, SparseOrderTarget.from(null, null, 2));
    operations.assignRank(descriptor(1_000), atEnd, null, SparseOrderTarget.from(null, null, 4));

    assertThat(ranks(List.of(atStart, first, atMiddle, second, atEnd))).containsExactly(
      1_000_000_000L,
      2_000_000_000L,
      3_000_000_000L,
      4_000_000_000L,
      5_000_000_000L
    );
  }

  @Test
  void ordersEqualRankSiblingsByIdBeforeAllocating() {
    Long first = insert("First", null, 1_000_000_000L);
    Long second = insert("Second", null, 1_000_000_000L);
    Long inserted = insert("Inserted", null, null);

    RankAssignmentResult result = operations.assignRank(
      descriptor(1_000),
      inserted,
      null,
      SparseOrderTarget.from(null, first, null)
    );

    assertThat(result.rebalancePerformed()).isTrue();
    assertThat(orderedIds()).containsExactly(first, inserted, second);
    assertThat(ranks(List.of(first, inserted, second))).containsExactly(
      1_000_000_000L,
      2_000_000_000L,
      3_000_000_000L
    );
  }

  @Test
  void appendsAfterAtLeastOneThousandSiblingsWithoutUpdatingExistingRanks() {
    for (int index = 0; index < 1_000; index++) {
      insert("Sibling " + index, null, (index + 1L) * 1_000_000_000L);
    }
    Long inserted = insert("Inserted", null, null);

    RankAssignmentResult result = operations.assignRank(
      descriptor(1_000),
      inserted,
      null,
      SparseOrderTarget.append()
    );

    assertThat(result.assignedRank()).isEqualTo(1_001_000_000_000L);
    assertThat(result.rebalancePerformed()).isFalse();
    assertThat(result.updatedRows()).isEqualTo(1);
    assertThat(rank(inserted)).isEqualTo(1_001_000_000_000L);
    assertThat(rank(1L)).isEqualTo(1_000_000_000L);
    assertThat(rank(500L)).isEqualTo(500_000_000_000L);
    assertThat(rank(1_000L)).isEqualTo(1_000_000_000_000L);
  }

  @Test
  void rebalancesAffectedSiblingGroupWhenGapIsExhausted() {
    Long first = insert("First", null, 1_000_000_000L);
    Long second = insert("Second", null, 1_000_000_001L);
    Long otherParent = insert("Other Parent", 99L, 1_000_000_001L);
    Long inserted = insert("Inserted", null, null);

    RankAssignmentResult result = operations.assignRank(
      descriptor(1_000),
      inserted,
      null,
      SparseOrderTarget.from(second, null, null)
    );

    assertThat(result.rebalancePerformed()).isTrue();
    assertThat(result.assignedRank()).isEqualTo(2_000_000_000L);
    assertThat(ranks(List.of(first, inserted, second))).containsExactly(
      1_000_000_000L,
      2_000_000_000L,
      3_000_000_000L
    );
    assertThat(rank(otherParent)).isEqualTo(1_000_000_001L);
  }

  @Test
  void emitsStructuredLogsForAssignmentAndRebalance() {
    Long first = insert("First", null, 1_000_000_000L);
    Long second = insert("Second", null, 1_000_000_001L);
    Long inserted = insert("Inserted", null, null);
    RankLogCapture logs = captureRankLogs();

    try {
      RankAssignmentResult result = operations.assignRank(
        descriptor(1_000),
        inserted,
        null,
        SparseOrderTarget.from(second, null, null)
      );

      assertThat(result.rebalancePerformed()).isTrue();
    } finally {
      detachRankLogs(logs);
    }

    assertThat(logs.appender().list)
      .anySatisfy(event -> {
        assertThat(event.getFormattedMessage()).isEqualTo("category rank rebalanced");
        assertThat(keyValue(event, "category.system")).isEqualTo("ranked-test-fixture");
        assertThat(keyValue(event, "category.operation")).isEqualTo("rebalance");
        assertThat(keyValue(event, "category.parent_id")).isNull();
        assertThat(keyValue(event, "category.rebalance.rows")).isEqualTo(3L);
        assertThat(keyValue(event, "category.ranking.result")).isEqualTo("rebalanced");
      })
      .anySatisfy(event -> {
        assertThat(event.getFormattedMessage()).isEqualTo("category rank assigned");
        assertThat(keyValue(event, "category.system")).isEqualTo("ranked-test-fixture");
        assertThat(keyValue(event, "category.id")).isEqualTo(inserted);
        assertThat(keyValue(event, "category.parent_id")).isNull();
        assertThat(keyValue(event, "category.rebalance.rows")).isEqualTo(3L);
        assertThat(keyValue(event, "category.ranking.result")).isEqualTo("assigned");
      });
  }

  @Test
  void rejectsOversizedRebalanceBeforeChangingRanks() {
    Long first = insert("First", null, 1_000_000_000L);
    Long second = insert("Second", null, 1_000_000_001L);
    Long inserted = insert("Inserted", null, null);

    assertThatThrownBy(() ->
      operations.assignRank(
        descriptor(1),
        inserted,
        null,
        SparseOrderTarget.from(second, null, null)
      )
    )
      .isInstanceOf(RankRebalanceLimitExceededException.class)
      .hasMessageContaining("would affect 2 rows")
      .satisfies(exception -> {
        RankRebalanceLimitExceededException rebalance =
          (RankRebalanceLimitExceededException) exception;
        assertThat(rebalance.systemKey()).isEqualTo("ranked-test-fixture");
        assertThat(rebalance.affectedRows()).isEqualTo(2);
        assertThat(rebalance.maxRows()).isEqualTo(1);
      });

    assertThat(rank(first)).isEqualTo(1_000_000_000L);
    assertThat(rank(second)).isEqualTo(1_000_000_001L);
    assertThat(rank(inserted)).isNull();
  }

  private Long insert(String name, Long parentId, Long rank) {
    return jdbc.queryForObject(
      """
      INSERT INTO test_ranked_category (name, parent_id, sort_rank)
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
      "SELECT sort_rank FROM test_ranked_category WHERE id = ?",
      Long.class,
      id
    );
  }

  private List<Long> ranks(List<Long> ids) {
    return ids.stream().map(this::rank).toList();
  }

  private List<Long> orderedIds() {
    return jdbc.queryForList(
      "SELECT id FROM test_ranked_category ORDER BY sort_rank ASC, id ASC",
      Long.class
    );
  }

  private static RankLogCapture captureRankLogs() {
    Logger logger = (Logger) LoggerFactory.getLogger(SparseRankOperations.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Level previousLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    logger.addAppender(appender);
    return new RankLogCapture(appender, previousLevel);
  }

  private static void detachRankLogs(RankLogCapture logs) {
    Logger logger = (Logger) LoggerFactory.getLogger(SparseRankOperations.class);
    logger.detachAppender(logs.appender());
    logger.setLevel(logs.previousLevel());
  }

  private static Object keyValue(ILoggingEvent event, String key) {
    if (event.getKeyValuePairs() == null) {
      return null;
    }
    return event
      .getKeyValuePairs()
      .stream()
      .filter(pair -> Objects.equals(pair.key, key))
      .findFirst()
      .map(pair -> pair.value)
      .orElse(null);
  }

  private record RankLogCapture(ListAppender<ILoggingEvent> appender, Level previousLevel) {}

  private static CategorySystemDescriptor descriptor(long maxSyncRebalanceRows) {
    return new CategorySystemDescriptor(
      "ranked-test-fixture",
      "test",
      ClosureTableDescriptor.of("test_ranked_category", "test_ranked_category_closure"),
      RankingCapabilityDescriptor.ranked(
        "sort_rank",
        1_000_000_000L,
        1_000_000_000L,
        maxSyncRebalanceRows,
        DisabledRankingRequestPolicy.REJECT,
        List.of(
          PlacementMode.APPEND,
          PlacementMode.BEFORE_ID,
          PlacementMode.AFTER_ID,
          PlacementMode.TARGET_INDEX
        ),
        true
      ),
      new FallbackOrderDescriptor(
        List.of(
          new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
          new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
        )
      ),
      Optional.of(
        new SparseRankTableDescriptor("test_ranked_category", "id", "parent_id", "sort_rank")
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
