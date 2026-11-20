package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class SparseRankOperations {

  private static final Logger log = LoggerFactory.getLogger(SparseRankOperations.class);

  private final NamedParameterJdbcTemplate jdbc;

  public SparseRankOperations(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public RankAssignmentResult assignRank(
    CategorySystemDescriptor descriptor,
    Long categoryId,
    @Nullable Long parentId,
    SparseOrderTarget target
  ) {
    requireRanked(descriptor);
    Objects.requireNonNull(categoryId, "categoryId");
    Objects.requireNonNull(target, "target");
    try {
      Allocation allocation = allocateRankInternal(descriptor, parentId, target, categoryId);
      long updatedRows = allocation.updatedRows();
      if (!allocation.rebalancePerformed()) {
        updatedRows += updateRank(
          descriptor.rankTableDescriptor().orElseThrow(),
          categoryId,
          allocation.rank()
        );
      }
      log
        .atInfo()
        .addKeyValue("category.system", descriptor.systemKey())
        .addKeyValue("category.id", categoryId)
        .addKeyValue("category.parent_id", parentId)
        .addKeyValue("category.ranking.result", "assigned")
        .addKeyValue("category.rebalance.rows", allocation.rebalancePerformed() ? updatedRows : 0)
        .log("category rank assigned");
      return new RankAssignmentResult(
        categoryId,
        parentId,
        allocation.rank(),
        target.mode(),
        allocation.rebalancePerformed(),
        updatedRows
      );
    } catch (RuntimeException exception) {
      log
        .atWarn()
        .addKeyValue("category.system", descriptor.systemKey())
        .addKeyValue("category.id", categoryId)
        .addKeyValue("category.parent_id", parentId)
        .addKeyValue("category.ranking.result", "allocation_failed")
        .log("category rank allocation failed");
      throw exception;
    }
  }

  @Transactional
  public long allocateRank(
    CategorySystemDescriptor descriptor,
    @Nullable Long parentId,
    SparseOrderTarget target
  ) {
    requireRanked(descriptor);
    Objects.requireNonNull(target, "target");
    try {
      Allocation allocation = allocateRankInternal(descriptor, parentId, target, null);
      log
        .atInfo()
        .addKeyValue("category.system", descriptor.systemKey())
        .addKeyValue("category.parent_id", parentId)
        .addKeyValue("category.ranking.result", "allocated")
        .addKeyValue(
          "category.rebalance.rows",
          allocation.rebalancePerformed() ? allocation.updatedRows() : 0
        )
        .log("category rank allocated");
      return allocation.rank();
    } catch (RuntimeException exception) {
      log
        .atWarn()
        .addKeyValue("category.system", descriptor.systemKey())
        .addKeyValue("category.parent_id", parentId)
        .addKeyValue("category.ranking.result", "allocation_failed")
        .log("category rank allocation failed");
      throw exception;
    }
  }

  public void requireRanked(CategorySystemDescriptor descriptor) {
    Objects.requireNonNull(descriptor, "descriptor");
    if (!descriptor.ranking().rankingEnabled()) {
      throw new IllegalStateException(
        "Sparse rank operations are disabled for category system " + descriptor.systemKey()
      );
    }
    descriptor
      .rankTableDescriptor()
      .orElseThrow(() ->
        new IllegalStateException(
          "Rank table descriptor is missing for category system " + descriptor.systemKey()
        )
      );
  }

  NamedParameterJdbcTemplate jdbc() {
    return jdbc;
  }

  private Allocation allocateRankInternal(
    CategorySystemDescriptor descriptor,
    @Nullable Long parentId,
    SparseOrderTarget target,
    @Nullable Long movingCategoryId
  ) {
    SparseRankTableDescriptor table = descriptor.rankTableDescriptor().orElseThrow();
    acquireSiblingGroupLock(descriptor, parentId);
    List<SiblingRank> siblings = findSiblingRanks(table, parentId, movingCategoryId);
    int insertionIndex = insertionIndex(target, siblings);
    Long lowerRank = insertionIndex == 0 ? null : siblings.get(insertionIndex - 1).rank();
    Long upperRank = insertionIndex == siblings.size() ? null : siblings.get(insertionIndex).rank();
    Long sparseRank = rankBetween(descriptor.ranking(), lowerRank, upperRank);
    if (sparseRank != null) {
      return new Allocation(sparseRank, false, 0);
    }
    return rebalance(descriptor, parentId, movingCategoryId, siblings, insertionIndex);
  }

  private void acquireSiblingGroupLock(
    CategorySystemDescriptor descriptor,
    @Nullable Long parentId
  ) {
    String lockKey = descriptor.systemKey() + ":rank:" + (parentId == null ? "root" : parentId);
    long startedAt = System.nanoTime();
    try {
      jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
        new MapSqlParameterSource().addValue("lockKey", lockKey),
        resultSet -> null
      );
      log
        .atDebug()
        .addKeyValue("category.system", descriptor.systemKey())
        .addKeyValue("category.parent_id", parentId)
        .addKeyValue(
          "category.lock.wait_ms",
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        )
        .addKeyValue("category.ranking.result", "lock_acquired")
        .log("category rank sibling lock acquired");
    } catch (RuntimeException exception) {
      log
        .atWarn()
        .addKeyValue("category.system", descriptor.systemKey())
        .addKeyValue("category.parent_id", parentId)
        .addKeyValue("category.ranking.result", "lock_failed")
        .log("category rank sibling lock failed");
      throw exception;
    }
  }

  private List<SiblingRank> findSiblingRanks(
    SparseRankTableDescriptor table,
    @Nullable Long parentId,
    @Nullable Long movingCategoryId
  ) {
    String movingCategoryFilter =
      movingCategoryId == null ? "" : "\n  AND %s <> :movingCategoryId".formatted(table.idColumn());
    return jdbc.query(
      """
      SELECT %s AS id, %s AS sort_rank_value
      FROM %s
      WHERE %s IS NOT DISTINCT FROM :parentId
        %s
      ORDER BY %s ASC, %s ASC
      """.formatted(
        table.idColumn(),
        table.rankColumn(),
        table.tableName(),
        table.parentIdColumn(),
        movingCategoryFilter,
        table.rankColumn(),
        table.idColumn()
      ),
      new MapSqlParameterSource()
        .addValue("parentId", parentId)
        .addValue("movingCategoryId", movingCategoryId),
      (resultSet, rowNumber) ->
        new SiblingRank(resultSet.getLong("id"), resultSet.getLong("sort_rank_value"))
    );
  }

  private static int insertionIndex(SparseOrderTarget target, List<SiblingRank> siblings) {
    return switch (target.mode()) {
      case APPEND -> siblings.size();
      case BEFORE_ID -> indexOfTarget(siblings, target.beforeId());
      case AFTER_ID -> indexOfTarget(siblings, target.afterId()) + 1;
      case TARGET_INDEX -> {
        int targetIndex = target.targetIndex() == null ? siblings.size() : target.targetIndex();
        if (targetIndex > siblings.size()) {
          throw new IllegalArgumentException(
            "targetIndex must be between 0 and " + siblings.size()
          );
        }
        yield targetIndex;
      }
    };
  }

  private static int indexOfTarget(List<SiblingRank> siblings, @Nullable Long targetId) {
    for (int index = 0; index < siblings.size(); index++) {
      if (siblings.get(index).id().equals(targetId)) {
        return index;
      }
    }
    throw new IllegalArgumentException("Placement target is not in the destination sibling group");
  }

  @Nullable
  private static Long rankBetween(
    RankingCapabilityDescriptor ranking,
    @Nullable Long lowerRank,
    @Nullable Long upperRank
  ) {
    long step = ranking.rankStep();
    if (lowerRank == null && upperRank == null) {
      return ranking.startingRank();
    }
    if (lowerRank == null) {
      long candidate = upperRank - step;
      return candidate > 0 ? candidate : null;
    }
    if (upperRank == null) {
      return safeAdd(lowerRank, step);
    }
    long gap = upperRank - lowerRank;
    if (gap > 1) {
      return lowerRank + (gap / 2);
    }
    return null;
  }

  private Allocation rebalance(
    CategorySystemDescriptor descriptor,
    @Nullable Long parentId,
    @Nullable Long movingCategoryId,
    List<SiblingRank> siblings,
    int insertionIndex
  ) {
    RankingCapabilityDescriptor ranking = descriptor.ranking();
    if (siblings.size() > ranking.maxSyncRebalanceRows()) {
      log
        .atWarn()
        .addKeyValue("category.system", descriptor.systemKey())
        .addKeyValue("category.operation", "rebalance")
        .addKeyValue("category.parent_id", parentId)
        .addKeyValue("category.rebalance.rows", siblings.size())
        .addKeyValue("category.ranking.result", "rebalance_rejected")
        .log("category rank rebalance rejected");
      throw new RankRebalanceLimitExceededException(
        descriptor.systemKey(),
        siblings.size(),
        ranking.maxSyncRebalanceRows()
      );
    }

    SparseRankTableDescriptor table = descriptor.rankTableDescriptor().orElseThrow();
    long updatedRows = 0;
    long assignedRank = rankAt(ranking, insertionIndex);
    if (movingCategoryId == null) {
      for (int index = 0; index < siblings.size(); index++) {
        int targetPosition = index < insertionIndex ? index : index + 1;
        updatedRows += updateRank(table, siblings.get(index).id(), rankAt(ranking, targetPosition));
      }
      logRebalance(descriptor, parentId, updatedRows);
      return new Allocation(assignedRank, true, updatedRows);
    }

    List<Long> orderedIds = new ArrayList<>();
    for (int index = 0; index < siblings.size(); index++) {
      if (index == insertionIndex) {
        orderedIds.add(movingCategoryId);
      }
      orderedIds.add(siblings.get(index).id());
    }
    if (insertionIndex == siblings.size()) {
      orderedIds.add(movingCategoryId);
    }
    for (int index = 0; index < orderedIds.size(); index++) {
      Long id = orderedIds.get(index);
      long rank = rankAt(ranking, index);
      updatedRows += updateRank(table, id, rank);
      if (id.equals(movingCategoryId)) {
        assignedRank = rank;
      }
    }
    logRebalance(descriptor, parentId, updatedRows);
    return new Allocation(assignedRank, true, updatedRows);
  }

  private static void logRebalance(
    CategorySystemDescriptor descriptor,
    @Nullable Long parentId,
    long updatedRows
  ) {
    log
      .atInfo()
      .addKeyValue("category.system", descriptor.systemKey())
      .addKeyValue("category.operation", "rebalance")
      .addKeyValue("category.parent_id", parentId)
      .addKeyValue("category.rebalance.rows", updatedRows)
      .addKeyValue("category.ranking.result", "rebalanced")
      .log("category rank rebalanced");
  }

  private long updateRank(SparseRankTableDescriptor table, Long categoryId, long rank) {
    return jdbc.update(
      """
      UPDATE %s
      SET %s = :rank
      WHERE %s = :categoryId
      """.formatted(table.tableName(), table.rankColumn(), table.idColumn()),
      new MapSqlParameterSource().addValue("rank", rank).addValue("categoryId", categoryId)
    );
  }

  private static long rankAt(RankingCapabilityDescriptor ranking, int index) {
    return safeAdd(ranking.startingRank(), Math.multiplyExact(index, ranking.rankStep()));
  }

  private static long safeAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("Sparse rank value overflow", exception);
    }
  }

  private record SiblingRank(Long id, long rank) {}

  private record Allocation(long rank, boolean rebalancePerformed, long updatedRows) {}
}
