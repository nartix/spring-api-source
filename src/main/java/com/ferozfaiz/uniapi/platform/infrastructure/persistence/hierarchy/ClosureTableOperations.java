package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ClosureTableOperations {

  private static final String NODE_ID = "nodeId";
  private static final String PARENT_ID = "parentId";
  private static final String NEW_PARENT_ID = "newParentId";
  private static final String ANCESTOR_ID = "ancestorId";
  private static final String DESCENDANT_ID = "descendantId";
  private static final String MAX_DEPTH = "maxDepth";

  private final NamedParameterJdbcTemplate jdbc;

  public ClosureTableOperations(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void createRoot(ClosureTableDescriptor hierarchy, Long nodeId) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    updateParent(hierarchy, nodeId, null);
    insertSelfPath(hierarchy, nodeId);
  }

  @Transactional
  public void createChild(ClosureTableDescriptor hierarchy, Long nodeId, Long parentId) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);
    Objects.requireNonNull(parentId, PARENT_ID);

    if (nodeId.equals(parentId)) {
      throw new HierarchyCycleException(nodeId, parentId);
    }
    requireInitialized(hierarchy, parentId);

    updateParent(hierarchy, nodeId, parentId);
    jdbc.update(
      """
      INSERT INTO %s (%s, %s, %s)
      SELECT %s, :nodeId, %s + 1
      FROM %s
      WHERE %s = :parentId
      UNION ALL
      SELECT :nodeId, :nodeId, 0
      """.formatted(
        hierarchy.closureTable(),
        hierarchy.ancestorColumn(),
        hierarchy.descendantColumn(),
        hierarchy.depthColumn(),
        hierarchy.ancestorColumn(),
        hierarchy.depthColumn(),
        hierarchy.closureTable(),
        hierarchy.descendantColumn()
      ),
      Map.of(NODE_ID, nodeId, PARENT_ID, parentId)
    );
  }

  @Transactional
  public void moveSubtree(
    ClosureTableDescriptor hierarchy,
    Long nodeId,
    @Nullable Long newParentId
  ) {
    moveSubtree(hierarchy, nodeId, newParentId, HierarchyMoveMode.SUBTREE);
  }

  public MoveSubtreeEstimate estimateMove(
    ClosureTableDescriptor hierarchy,
    Long nodeId,
    @Nullable Long newParentId
  ) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    long subtreeSize = countSelfAndDescendants(hierarchy, nodeId);
    long oldAncestorCount = countAncestorsExcludingSelf(hierarchy, nodeId);
    long newAncestorCount =
      newParentId == null ? 0 : countAncestorsIncludingSelf(hierarchy, newParentId);
    int subtreeMaxDepth = maxSubtreeDepth(hierarchy, nodeId);
    int newParentDepth = newParentId == null ? -1 : maxDepth(hierarchy, newParentId);
    int resultingMaxDepth =
      newParentId == null ? subtreeMaxDepth : newParentDepth + 1 + subtreeMaxDepth;
    boolean parentChangeRequired = !hasDirectParent(hierarchy, nodeId, newParentId);

    long estimatedDeleteRows = parentChangeRequired
      ? multiplySaturated(subtreeSize, oldAncestorCount)
      : 0;
    long estimatedInsertRows = parentChangeRequired
      ? multiplySaturated(subtreeSize, newAncestorCount)
      : 0;

    return new MoveSubtreeEstimate(
      subtreeSize,
      oldAncestorCount,
      newAncestorCount,
      estimatedDeleteRows,
      estimatedInsertRows,
      addSaturated(estimatedDeleteRows, estimatedInsertRows),
      subtreeMaxDepth,
      newParentDepth,
      resultingMaxDepth,
      parentChangeRequired
    );
  }

  public DeleteSubtreeEstimate estimateDelete(ClosureTableDescriptor hierarchy, Long nodeId) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    return new DeleteSubtreeEstimate(
      countSelfAndDescendants(hierarchy, nodeId),
      countClosureRowsForSubtree(hierarchy, nodeId)
    );
  }

  public long countSelfAndDescendants(ClosureTableDescriptor hierarchy, Long nodeId) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    Long count = jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM %s
      WHERE %s = :nodeId
      """.formatted(hierarchy.closureTable(), hierarchy.ancestorColumn()),
      Map.of(NODE_ID, nodeId),
      Long.class
    );

    return count == null ? 0 : count;
  }

  public void setLocalStatementTimeout(Duration timeout) {
    Objects.requireNonNull(timeout, "timeout");
    long timeoutMillis = timeout.toMillis();
    if (timeoutMillis <= 0) {
      throw new IllegalArgumentException("timeout must be greater than zero");
    }

    jdbc.queryForObject(
      "SELECT set_config('statement_timeout', :timeout, true)",
      Map.of("timeout", timeoutMillis + "ms"),
      String.class
    );
  }

  public void acquireHierarchyMoveLock(ClosureTableDescriptor hierarchy) {
    Objects.requireNonNull(hierarchy, "hierarchy");

    jdbc.query(
      "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
      Map.of("lockKey", "hierarchy:" + hierarchy.closureTable()),
      resultSet -> null
    );
  }

  public void lockMoveEndpointsSafely(
    ClosureTableDescriptor hierarchy,
    Long nodeId,
    @Nullable Long newParentId
  ) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    if (newParentId == null) {
      lockNode(hierarchy, nodeId);
      return;
    }

    Long first = Math.min(nodeId, newParentId);
    Long second = Math.max(nodeId, newParentId);

    lockNode(hierarchy, first);
    if (!first.equals(second)) {
      lockNode(hierarchy, second);
    }
  }

  public boolean hasDirectParent(
    ClosureTableDescriptor hierarchy,
    Long nodeId,
    @Nullable Long parentId
  ) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    String parentPredicate =
      parentId == null
        ? "%s IS NULL".formatted(hierarchy.parentIdColumn())
        : "%s = :parentId".formatted(hierarchy.parentIdColumn());
    Long count = jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM %s
      WHERE %s = :nodeId
        AND %s
      """.formatted(hierarchy.nodeTable(), hierarchy.nodeIdColumn(), parentPredicate),
      new MapSqlParameterSource().addValue(NODE_ID, nodeId).addValue(PARENT_ID, parentId),
      Long.class
    );

    return count != null && count > 0;
  }

  @Transactional
  public void moveSubtree(
    ClosureTableDescriptor hierarchy,
    Long nodeId,
    @Nullable Long newParentId,
    HierarchyMoveMode moveMode
  ) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);
    Objects.requireNonNull(moveMode, "moveMode");

    requireInitialized(hierarchy, nodeId);
    if (newParentId != null) {
      requireInitialized(hierarchy, newParentId);
      if (nodeId.equals(newParentId) || isDescendantOf(hierarchy, newParentId, nodeId)) {
        throw new HierarchyCycleException(nodeId, newParentId);
      }
    }
    if (moveMode == HierarchyMoveMode.LEAF_ONLY && hasChildren(hierarchy, nodeId)) {
      throw new IllegalStateException(
        "Node %d cannot be moved because it has children".formatted(nodeId)
      );
    }

    updateParent(hierarchy, nodeId, newParentId);
    deleteExternalAncestorPaths(hierarchy, nodeId);
    if (newParentId != null) {
      insertNewAncestorPaths(hierarchy, nodeId, newParentId);
    }
  }

  @Transactional
  public int deleteSubtreePaths(ClosureTableDescriptor hierarchy, Long nodeId) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    return jdbc.update(
      """
      DELETE FROM %s
      WHERE %s IN (
        SELECT subtree.%s
        FROM %s subtree
        WHERE subtree.%s = :nodeId
      )
      """.formatted(
        hierarchy.closureTable(),
        hierarchy.descendantColumn(),
        hierarchy.descendantColumn(),
        hierarchy.closureTable(),
        hierarchy.ancestorColumn()
      ),
      Map.of(NODE_ID, nodeId)
    );
  }

  public boolean isAncestorOf(
    ClosureTableDescriptor hierarchy,
    Long ancestorId,
    Long descendantId
  ) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(ancestorId, ANCESTOR_ID);
    Objects.requireNonNull(descendantId, DESCENDANT_ID);

    return pathExists(hierarchy, ancestorId, descendantId, false);
  }

  public boolean isDescendantOf(
    ClosureTableDescriptor hierarchy,
    Long descendantId,
    Long ancestorId
  ) {
    return isAncestorOf(hierarchy, ancestorId, descendantId);
  }

  public List<Long> findAncestorIds(ClosureTableDescriptor hierarchy, Long nodeId) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    return jdbc.queryForList(
      """
      SELECT %s
      FROM %s
      WHERE %s = :nodeId
        AND %s > 0
      ORDER BY %s DESC
      """.formatted(
        hierarchy.ancestorColumn(),
        hierarchy.closureTable(),
        hierarchy.descendantColumn(),
        hierarchy.depthColumn(),
        hierarchy.depthColumn()
      ),
      Map.of(NODE_ID, nodeId),
      Long.class
    );
  }

  public List<Long> findDescendantIds(ClosureTableDescriptor hierarchy, Long nodeId) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    return jdbc.queryForList(
      """
      SELECT %s
      FROM %s
      WHERE %s = :nodeId
        AND %s > 0
      ORDER BY %s ASC, %s ASC
      """.formatted(
        hierarchy.descendantColumn(),
        hierarchy.closureTable(),
        hierarchy.ancestorColumn(),
        hierarchy.depthColumn(),
        hierarchy.depthColumn(),
        hierarchy.descendantColumn()
      ),
      Map.of(NODE_ID, nodeId),
      Long.class
    );
  }

  public List<Long> findDescendantIds(ClosureTableDescriptor hierarchy, Long nodeId, int maxDepth) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);
    if (maxDepth < 0) {
      throw new IllegalArgumentException("maxDepth must be greater than or equal to 0");
    }

    return jdbc.queryForList(
      """
      SELECT %s
      FROM %s
      WHERE %s = :nodeId
        AND %s > 0
        AND %s <= :maxDepth
      ORDER BY %s ASC, %s ASC
      """.formatted(
        hierarchy.descendantColumn(),
        hierarchy.closureTable(),
        hierarchy.ancestorColumn(),
        hierarchy.depthColumn(),
        hierarchy.depthColumn(),
        hierarchy.depthColumn(),
        hierarchy.descendantColumn()
      ),
      Map.of(NODE_ID, nodeId, MAX_DEPTH, maxDepth),
      Long.class
    );
  }

  public List<Long> findChildIds(ClosureTableDescriptor hierarchy, Long nodeId) {
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(nodeId, NODE_ID);

    return jdbc.queryForList(
      """
      SELECT %s
      FROM %s
      WHERE %s = :nodeId
      ORDER BY %s ASC
      """.formatted(
        hierarchy.nodeIdColumn(),
        hierarchy.nodeTable(),
        hierarchy.parentIdColumn(),
        hierarchy.nodeIdColumn()
      ),
      Map.of(NODE_ID, nodeId),
      Long.class
    );
  }

  public List<Long> findRootIds(ClosureTableDescriptor hierarchy) {
    Objects.requireNonNull(hierarchy, "hierarchy");

    return jdbc.queryForList(
      """
      SELECT %s
      FROM %s
      WHERE %s IS NULL
      ORDER BY %s ASC
      """.formatted(
        hierarchy.nodeIdColumn(),
        hierarchy.nodeTable(),
        hierarchy.parentIdColumn(),
        hierarchy.nodeIdColumn()
      ),
      Map.of(),
      Long.class
    );
  }

  private void updateParent(
    ClosureTableDescriptor hierarchy,
    Long nodeId,
    @Nullable Long parentId
  ) {
    int updated = jdbc.update(
      """
      UPDATE %s
      SET %s = :parentId
      WHERE %s = :nodeId
      """.formatted(hierarchy.nodeTable(), hierarchy.parentIdColumn(), hierarchy.nodeIdColumn()),
      new MapSqlParameterSource().addValue(PARENT_ID, parentId).addValue(NODE_ID, nodeId)
    );
    if (updated != 1) {
      throw new IllegalArgumentException("Hierarchy node %d does not exist".formatted(nodeId));
    }
  }

  private void insertSelfPath(ClosureTableDescriptor hierarchy, Long nodeId) {
    jdbc.update(
      """
      INSERT INTO %s (%s, %s, %s)
      VALUES (:nodeId, :nodeId, 0)
      """.formatted(
        hierarchy.closureTable(),
        hierarchy.ancestorColumn(),
        hierarchy.descendantColumn(),
        hierarchy.depthColumn()
      ),
      Map.of(NODE_ID, nodeId)
    );
  }

  private void deleteExternalAncestorPaths(ClosureTableDescriptor hierarchy, Long nodeId) {
    jdbc.update(
      """
      DELETE FROM %s
      WHERE %s IN (
        SELECT subtree.%s
        FROM %s subtree
        WHERE subtree.%s = :nodeId
      )
        AND %s IN (
          SELECT ancestors.%s
          FROM %s ancestors
          WHERE ancestors.%s = :nodeId
            AND ancestors.%s <> ancestors.%s
        )
      """.formatted(
        hierarchy.closureTable(),
        hierarchy.descendantColumn(),
        hierarchy.descendantColumn(),
        hierarchy.closureTable(),
        hierarchy.ancestorColumn(),
        hierarchy.ancestorColumn(),
        hierarchy.ancestorColumn(),
        hierarchy.closureTable(),
        hierarchy.descendantColumn(),
        hierarchy.ancestorColumn(),
        hierarchy.descendantColumn()
      ),
      Map.of(NODE_ID, nodeId)
    );
  }

  private void insertNewAncestorPaths(
    ClosureTableDescriptor hierarchy,
    Long nodeId,
    Long newParentId
  ) {
    jdbc.update(
      """
      INSERT INTO %s (%s, %s, %s)
      SELECT supertree.%s, subtree.%s, supertree.%s + subtree.%s + 1
      FROM %s supertree
      CROSS JOIN %s subtree
      WHERE supertree.%s = :newParentId
        AND subtree.%s = :nodeId
      """.formatted(
        hierarchy.closureTable(),
        hierarchy.ancestorColumn(),
        hierarchy.descendantColumn(),
        hierarchy.depthColumn(),
        hierarchy.ancestorColumn(),
        hierarchy.descendantColumn(),
        hierarchy.depthColumn(),
        hierarchy.depthColumn(),
        hierarchy.closureTable(),
        hierarchy.closureTable(),
        hierarchy.descendantColumn(),
        hierarchy.ancestorColumn()
      ),
      Map.of(NODE_ID, nodeId, NEW_PARENT_ID, newParentId)
    );
  }

  private void requireInitialized(ClosureTableDescriptor hierarchy, Long nodeId) {
    if (!pathExists(hierarchy, nodeId, nodeId, true)) {
      throw new IllegalArgumentException(
        "Hierarchy node %d has not been initialized in the closure table".formatted(nodeId)
      );
    }
  }

  private boolean hasChildren(ClosureTableDescriptor hierarchy, Long nodeId) {
    Integer count = jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM %s
      WHERE %s = :nodeId
      """.formatted(hierarchy.nodeTable(), hierarchy.parentIdColumn()),
      Map.of(NODE_ID, nodeId),
      Integer.class
    );
    return count != null && count > 0;
  }

  private long countAncestorsIncludingSelf(ClosureTableDescriptor hierarchy, Long nodeId) {
    Long count = jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM %s
      WHERE %s = :nodeId
      """.formatted(hierarchy.closureTable(), hierarchy.descendantColumn()),
      Map.of(NODE_ID, nodeId),
      Long.class
    );

    return count == null ? 0 : count;
  }

  private long countAncestorsExcludingSelf(ClosureTableDescriptor hierarchy, Long nodeId) {
    Long count = jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM %s
      WHERE %s = :nodeId
        AND %s > 0
      """.formatted(
        hierarchy.closureTable(),
        hierarchy.descendantColumn(),
        hierarchy.depthColumn()
      ),
      Map.of(NODE_ID, nodeId),
      Long.class
    );

    return count == null ? 0 : count;
  }

  private int maxSubtreeDepth(ClosureTableDescriptor hierarchy, Long nodeId) {
    Integer maxDepth = jdbc.queryForObject(
      """
      SELECT COALESCE(MAX(%s), 0)
      FROM %s
      WHERE %s = :nodeId
      """.formatted(hierarchy.depthColumn(), hierarchy.closureTable(), hierarchy.ancestorColumn()),
      Map.of(NODE_ID, nodeId),
      Integer.class
    );

    return maxDepth == null ? 0 : maxDepth;
  }

  private int maxDepth(ClosureTableDescriptor hierarchy, Long nodeId) {
    Integer maxDepth = jdbc.queryForObject(
      """
      SELECT COALESCE(MAX(%s), 0)
      FROM %s
      WHERE %s = :nodeId
      """.formatted(
        hierarchy.depthColumn(),
        hierarchy.closureTable(),
        hierarchy.descendantColumn()
      ),
      Map.of(NODE_ID, nodeId),
      Integer.class
    );

    return maxDepth == null ? 0 : maxDepth;
  }

  private long countClosureRowsForSubtree(ClosureTableDescriptor hierarchy, Long nodeId) {
    Long count = jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM %s path
      WHERE path.%s IN (
        SELECT subtree.%s
        FROM %s subtree
        WHERE subtree.%s = :nodeId
      )
      """.formatted(
        hierarchy.closureTable(),
        hierarchy.descendantColumn(),
        hierarchy.descendantColumn(),
        hierarchy.closureTable(),
        hierarchy.ancestorColumn()
      ),
      Map.of(NODE_ID, nodeId),
      Long.class
    );

    return count == null ? 0 : count;
  }

  private void lockNode(ClosureTableDescriptor hierarchy, Long nodeId) {
    jdbc.queryForObject(
      """
      SELECT %s
      FROM %s
      WHERE %s = :nodeId
      FOR UPDATE
      """.formatted(hierarchy.nodeIdColumn(), hierarchy.nodeTable(), hierarchy.nodeIdColumn()),
      Map.of(NODE_ID, nodeId),
      Long.class
    );
  }

  private boolean pathExists(
    ClosureTableDescriptor hierarchy,
    Long ancestorId,
    Long descendantId,
    boolean includeSelf
  ) {
    Boolean exists = jdbc.queryForObject(
      """
      SELECT EXISTS (
          SELECT 1
          FROM %s
          WHERE %s = :ancestorId
            AND %s = :descendantId
            AND (:includeSelf = true OR %s > 0)
      )
      """.formatted(
        hierarchy.closureTable(),
        hierarchy.ancestorColumn(),
        hierarchy.descendantColumn(),
        hierarchy.depthColumn()
      ),
      Map.of(ANCESTOR_ID, ancestorId, DESCENDANT_ID, descendantId, "includeSelf", includeSelf),
      Boolean.class
    );

    return Boolean.TRUE.equals(exists);
  }

  private static long multiplySaturated(long left, long right) {
    try {
      return Math.multiplyExact(left, right);
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
  }

  private static long addSaturated(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
  }
}
