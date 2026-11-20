package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class AdminCategoryJdbcSql {

  private final AdminCategoryNodeTableDescriptor node;
  private final CategorySystemDescriptor categorySystem;

  public AdminCategoryJdbcSql(
    AdminCategoryNodeTableDescriptor node,
    CategorySystemDescriptor categorySystem
  ) {
    this.node = Objects.requireNonNull(node, "node");
    this.categorySystem = Objects.requireNonNull(categorySystem, "categorySystem");
  }

  public Statement list(AdminCategoryListQuery query) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    String where = whereClause(query.parentScope(), query.filters(), parameters);
    parameters.put("limit", query.page().size());
    parameters.put("offset", (long) query.page().page() * query.page().size());
    return new Statement(
      """
      SELECT
        node.%s AS id,
        node.%s AS parent_id,
        %s AS sort_rank,
        node.%s AS name,
        node.%s AS slug,
        node.%s AS active,
        node.%s AS created_at,
        node.%s AS updated_at,
        COALESCE(depths.depth, 0) AS depth,
        EXISTS (
          SELECT 1
          FROM %s child
          WHERE child.%s = node.%s
        ) AS has_children
      FROM %s node
      LEFT JOIN (
        SELECT %s, MAX(%s) AS depth
        FROM %s
        GROUP BY %s
      ) depths ON depths.%s = node.%s
      %s
      ORDER BY %s
      LIMIT :limit OFFSET :offset
      """.formatted(
        node.idColumn(),
        node.parentIdColumn(),
        sortRankSelectExpression(),
        node.nameColumn(),
        node.slugColumn(),
        node.activeColumn(),
        node.createdAtColumn(),
        node.updatedAtColumn(),
        node.nodeTable(),
        node.parentIdColumn(),
        node.idColumn(),
        node.nodeTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().depthColumn(),
        categorySystem.closureTableDescriptor().closureTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        node.idColumn(),
        where,
        orderBy(query.sort().orders())
      ),
      parameters
    );
  }

  public Statement count(AdminCategoryListQuery query) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    String where = whereClause(query.parentScope(), query.filters(), parameters);
    return new Statement(
      """
      SELECT COUNT(*)
      FROM %s node
      %s
      """.formatted(node.nodeTable(), where),
      parameters
    );
  }

  public Statement countAll() {
    return new Statement("SELECT COUNT(*) FROM %s".formatted(node.nodeTable()), Map.of());
  }

  public Statement insertNode(
    Long parentId,
    Long sortRank,
    String name,
    String slug,
    boolean active
  ) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("parentId", parentId);
    parameters.put("name", name);
    parameters.put("slug", slug);
    parameters.put("active", active);

    List<String> columns = new ArrayList<>(
      List.of(node.parentIdColumn(), node.nameColumn(), node.slugColumn(), node.activeColumn())
    );
    List<String> values = new ArrayList<>(List.of(":parentId", ":name", ":slug", ":active"));
    node.sortRankColumn().ifPresent(column -> {
      columns.add(1, column);
      values.add(1, ":sortRank");
      parameters.put("sortRank", sortRank);
    });

    return new Statement(
      """
      INSERT INTO %s (%s)
      VALUES (%s)
      """.formatted(node.nodeTable(), String.join(", ", columns), String.join(", ", values)),
      parameters
    );
  }

  public Statement updateNode(
    Long id,
    Long parentId,
    Long sortRank,
    String name,
    String slug,
    boolean active
  ) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("id", id);
    parameters.put("parentId", parentId);
    parameters.put("name", name);
    parameters.put("slug", slug);
    parameters.put("active", active);
    List<String> assignments = new ArrayList<>(
      List.of(
        "%s = :parentId".formatted(node.parentIdColumn()),
        "%s = :name".formatted(node.nameColumn()),
        "%s = :slug".formatted(node.slugColumn()),
        "%s = :active".formatted(node.activeColumn()),
        "%s = %s".formatted(node.updatedAtColumn(), node.updatedAtExpression())
      )
    );
    node.sortRankColumn().ifPresent(column -> {
      assignments.add(1, "%s = :sortRank".formatted(column));
      parameters.put("sortRank", sortRank);
    });
    return new Statement(
      """
      UPDATE %s
      SET %s
      WHERE %s = :id
      """.formatted(node.nodeTable(), String.join(",\n  ", assignments), node.idColumn()),
      parameters
    );
  }

  public Statement updateActive(Long id, boolean active) {
    return new Statement(
      """
      UPDATE %s
      SET %s = :active,
        %s = %s
      WHERE %s = :id
      """.formatted(
        node.nodeTable(),
        node.activeColumn(),
        node.updatedAtColumn(),
        node.updatedAtExpression(),
        node.idColumn()
      ),
      Map.of("id", id, "active", active)
    );
  }

  public Statement touch(Long id) {
    return new Statement(
      """
      UPDATE %s
      SET %s = %s
      WHERE %s = :id
      """.formatted(
        node.nodeTable(),
        node.updatedAtColumn(),
        node.updatedAtExpression(),
        node.idColumn()
      ),
      Map.of("id", id)
    );
  }

  public Statement deleteNode(Long id) {
    return new Statement(
      """
      DELETE FROM %s
      WHERE %s = :id
      """.formatted(node.nodeTable(), node.idColumn()),
      Map.of("id", id)
    );
  }

  public Statement hasChildren(Long id) {
    return new Statement(
      """
      SELECT COUNT(*)
      FROM %s
      WHERE %s = :id
      """.formatted(node.nodeTable(), node.parentIdColumn()),
      Map.of("id", id)
    );
  }

  public Statement existsSlug(String slug, Long excludingId) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("slug", slug);
    String excludingPredicate = "";
    if (excludingId != null) {
      excludingPredicate = "\n  AND %s <> :excludingId".formatted(node.idColumn());
      parameters.put("excludingId", excludingId);
    }
    return new Statement(
      """
      SELECT COUNT(*)
      FROM %s
      WHERE %s = :slug%s
      """.formatted(node.nodeTable(), node.slugColumn(), excludingPredicate),
      parameters
    );
  }

  public Statement category(Long id) {
    return new Statement(
      """
      SELECT
        node.%s AS id,
        node.%s AS parent_id,
        %s AS sort_rank,
        node.%s AS name,
        node.%s AS slug,
        node.%s AS active,
        node.%s AS created_at,
        node.%s AS updated_at
      FROM %s node
      WHERE node.%s = :id
      """.formatted(
        node.idColumn(),
        node.parentIdColumn(),
        sortRankSelectExpression(),
        node.nameColumn(),
        node.slugColumn(),
        node.activeColumn(),
        node.createdAtColumn(),
        node.updatedAtColumn(),
        node.nodeTable(),
        node.idColumn()
      ),
      Map.of("id", id)
    );
  }

  public Statement categoryNode(Long id) {
    return new Statement(
      """
      SELECT
        node.%s AS id,
        node.%s AS parent_id,
        %s AS sort_rank,
        node.%s AS name,
        node.%s AS slug,
        node.%s AS active,
        node.%s AS created_at,
        node.%s AS updated_at,
        COALESCE(depths.depth, 0) AS depth,
        EXISTS (
          SELECT 1
          FROM %s child
          WHERE child.%s = node.%s
        ) AS has_children
      FROM %s node
      LEFT JOIN (
        SELECT %s, MAX(%s) AS depth
        FROM %s
        GROUP BY %s
      ) depths ON depths.%s = node.%s
      WHERE node.%s = :id
      """.formatted(
        node.idColumn(),
        node.parentIdColumn(),
        sortRankSelectExpression(),
        node.nameColumn(),
        node.slugColumn(),
        node.activeColumn(),
        node.createdAtColumn(),
        node.updatedAtColumn(),
        node.nodeTable(),
        node.parentIdColumn(),
        node.idColumn(),
        node.nodeTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().depthColumn(),
        categorySystem.closureTableDescriptor().closureTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        node.idColumn(),
        node.idColumn()
      ),
      Map.of("id", id)
    );
  }

  public Statement tree(AdminCategoryTreeQuery query) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("limit", query.page().size());
    parameters.put("offset", (long) query.page().page() * query.page().size());
    return new Statement(
      """
      SELECT
        node.%s AS id,
        node.%s AS parent_id,
        %s AS sort_rank,
        node.%s AS name,
        node.%s AS slug,
        node.%s AS active,
        node.%s AS created_at,
        node.%s AS updated_at,
        COALESCE(depths.depth, 0) AS depth,
        EXISTS (
          SELECT 1
          FROM %s child
          WHERE child.%s = node.%s
        ) AS has_children
      FROM %s node
      LEFT JOIN (
        SELECT %s, MAX(%s) AS depth
        FROM %s
        GROUP BY %s
      ) depths ON depths.%s = node.%s
      ORDER BY COALESCE(depths.depth, 0) ASC, node.%s ASC
      LIMIT :limit OFFSET :offset
      """.formatted(
        node.idColumn(),
        node.parentIdColumn(),
        sortRankSelectExpression(),
        node.nameColumn(),
        node.slugColumn(),
        node.activeColumn(),
        node.createdAtColumn(),
        node.updatedAtColumn(),
        node.nodeTable(),
        node.parentIdColumn(),
        node.idColumn(),
        node.nodeTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().depthColumn(),
        categorySystem.closureTableDescriptor().closureTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        node.idColumn(),
        node.idColumn()
      ),
      parameters
    );
  }

  public Statement breadcrumb(Long id) {
    return new Statement(
      """
      SELECT
        node.%s AS id,
        node.%s AS parent_id,
        %s AS sort_rank,
        node.%s AS name,
        node.%s AS slug,
        node.%s AS active,
        node.%s AS created_at,
        node.%s AS updated_at,
        closure.%s AS depth
      FROM %s closure
      JOIN %s node ON node.%s = closure.%s
      WHERE closure.%s = :id
      ORDER BY closure.%s DESC
      """.formatted(
        node.idColumn(),
        node.parentIdColumn(),
        sortRankSelectExpression(),
        node.nameColumn(),
        node.slugColumn(),
        node.activeColumn(),
        node.createdAtColumn(),
        node.updatedAtColumn(),
        categorySystem.closureTableDescriptor().depthColumn(),
        categorySystem.closureTableDescriptor().closureTable(),
        node.nodeTable(),
        node.idColumn(),
        categorySystem.closureTableDescriptor().ancestorColumn(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().depthColumn()
      ),
      Map.of("id", id)
    );
  }

  public Statement parentOptionBreadcrumbsByIds(List<Long> parentOptionIds) {
    return new Statement(
      """
      SELECT
        closure.%s AS parent_option_id,
        node.%s AS id,
        node.%s AS parent_id,
        %s AS sort_rank,
        node.%s AS name,
        node.%s AS slug,
        node.%s AS active,
        node.%s AS created_at,
        node.%s AS updated_at,
        closure.%s AS depth
      FROM %s closure
      JOIN %s node ON node.%s = closure.%s
      WHERE closure.%s IN (:parentOptionIds)
      ORDER BY closure.%s ASC, closure.%s DESC
      """.formatted(
        categorySystem.closureTableDescriptor().descendantColumn(),
        node.idColumn(),
        node.parentIdColumn(),
        sortRankSelectExpression(),
        node.nameColumn(),
        node.slugColumn(),
        node.activeColumn(),
        node.createdAtColumn(),
        node.updatedAtColumn(),
        categorySystem.closureTableDescriptor().depthColumn(),
        categorySystem.closureTableDescriptor().closureTable(),
        node.nodeTable(),
        node.idColumn(),
        categorySystem.closureTableDescriptor().ancestorColumn(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().depthColumn()
      ),
      Map.of("parentOptionIds", List.copyOf(parentOptionIds))
    );
  }

  public Statement parentOptions(
    AdminCategoryParentOptionsQuery query,
    AdminCategoryParentOptionsCursorCodec.Position cursorPosition
  ) {
    return parentOptions(query, cursorPosition, query.limit() + 1);
  }

  public Statement parentOptions(
    AdminCategoryParentOptionsQuery query,
    AdminCategoryParentOptionsCursorCodec.Position cursorPosition,
    int fetchLimit
  ) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    List<String> predicates = new ArrayList<>();
    String normalizedName = "LOWER(node.%s)".formatted(node.nameColumn());
    String activeSort = "CASE WHEN node.%s THEN 0 ELSE 1 END".formatted(node.activeColumn());
    String searchRank = searchRankExpression(query);

    if (!query.includeInactive()) {
      predicates.add("node.%s = TRUE".formatted(node.activeColumn()));
    }
    if (query.q() != null && !query.q().isBlank()) {
      String normalized = query.q().trim().toLowerCase(Locale.ROOT);
      parameters.put("qExact", normalized);
      parameters.put("qPrefix", escapeLike(normalized) + "%");
      parameters.put("search", "%" + escapeLike(normalized) + "%");
      predicates.add(
        """
        (
          CAST(node.%s AS TEXT) = :qExact
          OR LOWER(node.%s) LIKE :search ESCAPE '\\'
          OR LOWER(node.%s) LIKE :search ESCAPE '\\'
        )
        """.formatted(node.idColumn(), node.slugColumn(), node.nameColumn())
      );
    }
    if (query.excludeSubtreeId() != null) {
      parameters.put("excludeSubtreeId", query.excludeSubtreeId());
      predicates.add(
        """
        NOT EXISTS (
          SELECT 1
          FROM %s excluded
          WHERE excluded.%s = :excludeSubtreeId
            AND excluded.%s = node.%s
        )
        """.formatted(
          categorySystem.closureTableDescriptor().closureTable(),
          categorySystem.closureTableDescriptor().ancestorColumn(),
          categorySystem.closureTableDescriptor().descendantColumn(),
          node.idColumn()
        )
      );
    }
    if (!query.includeIds().isEmpty()) {
      parameters.put("includeIds", query.includeIds());
      predicates.add("node.%s NOT IN (:includeIds)".formatted(node.idColumn()));
    }
    if (cursorPosition != null) {
      parameters.put("cursorSearchRank", cursorPosition.searchRank());
      parameters.put("cursorActiveSort", cursorPosition.active() ? 0 : 1);
      parameters.put("cursorName", cursorPosition.normalizedName());
      parameters.put("cursorId", cursorPosition.id());
      predicates.add(
        "(%s, %s, %s, node.%s) > (:cursorSearchRank, :cursorActiveSort, :cursorName, :cursorId)".formatted(
          searchRank,
          activeSort,
          normalizedName,
          node.idColumn()
        )
      );
    }

    parameters.put("limit", fetchLimit);
    String where = predicates.isEmpty() ? "" : "WHERE " + String.join("\n  AND ", predicates);
    return new Statement(
      """
      SELECT
        node.%s AS id,
        node.%s AS parent_id,
        %s AS sort_rank,
        node.%s AS name,
        node.%s AS slug,
        node.%s AS active,
        node.%s AS created_at,
        node.%s AS updated_at,
        COALESCE(depths.depth, 0) AS depth,
        node.%s AS label,
        TRUE AS selectable,
        NULL::TEXT AS disabled_reason,
        %s AS normalized_name,
        %s AS search_rank
      FROM %s node
      LEFT JOIN (
        SELECT %s, MAX(%s) AS depth
        FROM %s
        GROUP BY %s
      ) depths ON depths.%s = node.%s
      %s
      ORDER BY search_rank ASC, %s ASC, normalized_name ASC, node.%s ASC
      LIMIT :limit
      """.formatted(
        node.idColumn(),
        node.parentIdColumn(),
        sortRankSelectExpression(),
        node.nameColumn(),
        node.slugColumn(),
        node.activeColumn(),
        node.createdAtColumn(),
        node.updatedAtColumn(),
        node.nameColumn(),
        normalizedName,
        searchRank,
        node.nodeTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().depthColumn(),
        categorySystem.closureTableDescriptor().closureTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        node.idColumn(),
        where,
        activeSort,
        node.idColumn()
      ),
      parameters
    );
  }

  public Statement parentOptionsByIds(List<Long> includeIds, Long excludeSubtreeId) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("includeIds", includeIds);
    List<String> predicates = new ArrayList<>();
    predicates.add("node.%s IN (:includeIds)".formatted(node.idColumn()));
    if (excludeSubtreeId != null) {
      parameters.put("excludeSubtreeId", excludeSubtreeId);
      predicates.add(
        """
        NOT EXISTS (
          SELECT 1
          FROM %s excluded
          WHERE excluded.%s = :excludeSubtreeId
            AND excluded.%s = node.%s
        )
        """.formatted(
          categorySystem.closureTableDescriptor().closureTable(),
          categorySystem.closureTableDescriptor().ancestorColumn(),
          categorySystem.closureTableDescriptor().descendantColumn(),
          node.idColumn()
        )
      );
    }
    return new Statement(
      """
      SELECT
        node.%s AS id,
        node.%s AS parent_id,
        %s AS sort_rank,
        node.%s AS name,
        node.%s AS slug,
        node.%s AS active,
        node.%s AS created_at,
        node.%s AS updated_at,
        COALESCE(depths.depth, 0) AS depth,
        node.%s AS label,
        TRUE AS selectable,
        NULL::TEXT AS disabled_reason,
        LOWER(node.%s) AS normalized_name,
        0 AS search_rank
      FROM %s node
      LEFT JOIN (
        SELECT %s, MAX(%s) AS depth
        FROM %s
        GROUP BY %s
      ) depths ON depths.%s = node.%s
      WHERE %s
      """.formatted(
        node.idColumn(),
        node.parentIdColumn(),
        sortRankSelectExpression(),
        node.nameColumn(),
        node.slugColumn(),
        node.activeColumn(),
        node.createdAtColumn(),
        node.updatedAtColumn(),
        node.nameColumn(),
        node.nameColumn(),
        node.nodeTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().depthColumn(),
        categorySystem.closureTableDescriptor().closureTable(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        categorySystem.closureTableDescriptor().descendantColumn(),
        node.idColumn(),
        String.join("\n  AND ", predicates)
      ),
      parameters
    );
  }

  private String whereClause(
    AdminCategoryParentScope parentScope,
    AdminCategoryFilters filters,
    Map<String, Object> parameters
  ) {
    List<String> predicates = new ArrayList<>();
    switch (parentScope.mode()) {
      case ALL -> {
      }
      case ROOTS -> predicates.add("node.%s IS NULL".formatted(node.parentIdColumn()));
      case CHILDREN -> {
        predicates.add("node.%s = :parentId".formatted(node.parentIdColumn()));
        parameters.put("parentId", parentScope.parentId());
      }
    }
    if (filters.active() != null) {
      predicates.add("node.%s = :active".formatted(node.activeColumn()));
      parameters.put("active", filters.active());
    }
    if (filters.slug() != null && !filters.slug().isBlank()) {
      predicates.add("node.%s = :slug".formatted(node.slugColumn()));
      parameters.put("slug", filters.slug().trim());
    }
    if (filters.q() != null && !filters.q().isBlank()) {
      predicates.add(
        """
        (
          LOWER(node.%s) LIKE :search ESCAPE '\\'
          OR LOWER(node.%s) LIKE :search ESCAPE '\\'
        )
        """.formatted(node.nameColumn(), node.slugColumn())
      );
      parameters.put("search", "%" + escapeLike(filters.q().trim().toLowerCase(Locale.ROOT)) + "%");
    }
    return predicates.isEmpty() ? "" : "WHERE " + String.join("\n  AND ", predicates);
  }

  private String orderBy(List<SortOrder> orders) {
    List<String> fragments = new ArrayList<>();
    boolean hasId = false;
    for (SortOrder order : orders) {
      String column = sortColumn(order.property());
      hasId = hasId || "id".equals(order.property());
      fragments.add("%s %s".formatted(column, order.direction().name()));
    }
    if (!hasId) {
      fragments.add("node.%s ASC".formatted(node.idColumn()));
    }
    return String.join(", ", fragments);
  }

  private String sortColumn(String property) {
    return switch (property) {
      case "id" -> "node.%s".formatted(node.idColumn());
      case "parentId" -> "node.%s".formatted(node.parentIdColumn());
      case "slug" -> "node.%s".formatted(node.slugColumn());
      case "name" -> "node.%s".formatted(node.nameColumn());
      case "sortRank" -> node
        .sortRankColumn()
        .map(column -> "node.%s".formatted(column))
        .orElseThrow(() -> new IllegalArgumentException("Unsupported category sort: " + property));
      case "active" -> "node.%s".formatted(node.activeColumn());
      case "createdAt" -> "node.%s".formatted(node.createdAtColumn());
      case "updatedAt" -> "node.%s".formatted(node.updatedAtColumn());
      default -> throw new IllegalArgumentException("Unsupported category sort: " + property);
    };
  }

  private String sortRankSelectExpression() {
    return node
      .sortRankColumn()
      .map(column -> "node.%s".formatted(column))
      .orElse("NULL::BIGINT");
  }

  private String searchRankExpression(AdminCategoryParentOptionsQuery query) {
    if (query.q() == null || query.q().isBlank()) {
      return "0";
    }
    return """
    CASE
      WHEN CAST(node.%s AS TEXT) = :qExact THEN 0
      WHEN LOWER(node.%s) = :qExact THEN 1
      WHEN LOWER(node.%s) LIKE :qPrefix ESCAPE '\\' THEN 2
      WHEN LOWER(node.%s) LIKE :qPrefix ESCAPE '\\' THEN 3
      ELSE 4
    END
    """.formatted(node.idColumn(), node.slugColumn(), node.slugColumn(), node.nameColumn());
  }

  static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  public record Statement(String sql, Map<String, Object> parameters) {
    public Statement {
      parameters = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
  }
}
