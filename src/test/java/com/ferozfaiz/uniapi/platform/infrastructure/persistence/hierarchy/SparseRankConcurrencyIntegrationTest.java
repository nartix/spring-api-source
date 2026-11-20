package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
class SparseRankConcurrencyIntegrationTest {

  @Autowired
  private SparseRankOperations operations;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    jdbc.execute("DROP TABLE IF EXISTS test_ranked_concurrency_category");
    jdbc.execute(
      """
      CREATE TABLE test_ranked_concurrency_category (
        id BIGSERIAL PRIMARY KEY,
        parent_id BIGINT,
        name VARCHAR(255) NOT NULL,
        sort_rank BIGINT
      )
      """
    );
  }

  @Test
  void sameSystemAndParentSiblingGroupSerializesRankAssignment() throws Exception {
    insert("Root", null, 1_000_000_000L);
    Long inserted = insert("Inserted", null, null);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> lockHolder = holdSiblingGroupLock(executor, "system-a", null, locked, release);
      assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();

      Future<RankAssignmentResult> blockedAssignment = executor.submit(() ->
        operations.assignRank(descriptor("system-a"), inserted, null, SparseOrderTarget.append())
      );

      TimeUnit.MILLISECONDS.sleep(200);
      assertThat(blockedAssignment.isDone()).isFalse();

      release.countDown();
      assertThat(blockedAssignment.get(2, TimeUnit.SECONDS).assignedRank()).isEqualTo(
        2_000_000_000L
      );
      lockHolder.get(2, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void differentParentSiblingGroupsProceedIndependently() throws Exception {
    Long inserted = insert("Inserted", 99L, null);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> lockHolder = holdSiblingGroupLock(executor, "system-a", null, locked, release);
      assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();

      Future<RankAssignmentResult> assignment = executor.submit(() ->
        operations.assignRank(descriptor("system-a"), inserted, 99L, SparseOrderTarget.append())
      );

      assertThat(assignment.get(2, TimeUnit.SECONDS).assignedRank()).isEqualTo(1_000_000_000L);
      release.countDown();
      lockHolder.get(2, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void differentCategorySystemsProceedIndependently() throws Exception {
    Long inserted = insert("Inserted", null, null);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> lockHolder = holdSiblingGroupLock(executor, "system-a", null, locked, release);
      assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();

      Future<RankAssignmentResult> assignment = executor.submit(() ->
        operations.assignRank(descriptor("system-b"), inserted, null, SparseOrderTarget.append())
      );

      assertThat(assignment.get(2, TimeUnit.SECONDS).assignedRank()).isEqualTo(1_000_000_000L);
      release.countDown();
      lockHolder.get(2, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  private Future<?> holdSiblingGroupLock(
    ExecutorService executor,
    String systemKey,
    Long parentId,
    CountDownLatch locked,
    CountDownLatch release
  ) {
    return executor.submit(() -> {
      TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
      transactionTemplate.executeWithoutResult(status -> {
        acquireLock(systemKey, parentId);
        locked.countDown();
        try {
          assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(exception);
        }
      });
    });
  }

  private void acquireLock(String systemKey, Long parentId) {
    jdbc.query(
      "SELECT pg_advisory_xact_lock(hashtext(?))",
      preparedStatement ->
        preparedStatement.setString(
          1,
          systemKey + ":rank:" + (parentId == null ? "root" : parentId)
        ),
      resultSet -> null
    );
  }

  private Long insert(String name, Long parentId, Long rank) {
    return jdbc.queryForObject(
      """
      INSERT INTO test_ranked_concurrency_category (name, parent_id, sort_rank)
      VALUES (?, ?, ?)
      RETURNING id
      """,
      Long.class,
      name,
      parentId,
      rank
    );
  }

  private static CategorySystemDescriptor descriptor(String systemKey) {
    return new CategorySystemDescriptor(
      systemKey,
      "test",
      ClosureTableDescriptor.of(
        "test_ranked_concurrency_category",
        "test_ranked_concurrency_category_closure"
      ),
      RankingCapabilityDescriptor.ranked(
        "sort_rank",
        1_000_000_000L,
        1_000_000_000L,
        1_000,
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
        new SparseRankTableDescriptor(
          "test_ranked_concurrency_category",
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
