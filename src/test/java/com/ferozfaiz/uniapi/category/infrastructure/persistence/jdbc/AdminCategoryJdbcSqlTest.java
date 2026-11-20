package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.DisabledRankingRequestPolicy;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.PlacementMode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankingCapabilityDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankTableDescriptor;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminCategoryJdbcSqlTest {

  private static final CategorySystemDescriptor CATEGORY_SYSTEM = new CategorySystemDescriptor(
    "data",
    "data",
    ClosureTableDescriptor.of("data_category", "data_category_closure"),
    RankingCapabilityDescriptor.ranked(
      "sort_rank",
      1_000_000_000L,
      1_000_000_000L,
      1_000L,
      DisabledRankingRequestPolicy.REJECT,
      List.of(PlacementMode.APPEND, PlacementMode.BEFORE_ID, PlacementMode.AFTER_ID),
      true
    ),
    new FallbackOrderDescriptor(
      List.of(
        new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
        new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
      )
    ),
    Optional.of(new SparseRankTableDescriptor("data_category", "id", "parent_id", "sort_rank"))
  );
  private static final AdminCategoryNodeTableDescriptor NODE =
    AdminCategoryNodeTableDescriptor.from(
      CATEGORY_SYSTEM,
      new AdminCategoryJdbcProviderProperties.SystemProperties()
    );

  @Test
  void buildsListSqlWithParentScopeFiltersPublicSortMappingAndStableTieBreaker() {
    AdminCategoryJdbcSql.Statement statement = new AdminCategoryJdbcSql(NODE, CATEGORY_SYSTEM).list(
      new AdminCategoryListQuery(
        AdminCategoryParentScope.children(42L),
        new AdminCategoryFilters(true, "100%_GPU\\x", "gpu"),
        new PageCriteria(2, 25),
        new SortCriteria(List.of(new SortOrder("name", SortDirection.DESC)))
      )
    );

    assertThat(statement.sql()).contains("FROM data_category node");
    assertThat(statement.sql()).contains("node.parent_id = :parentId");
    assertThat(statement.sql()).contains("node.active = :active");
    assertThat(statement.sql()).contains("node.slug = :slug");
    assertThat(statement.sql()).contains("LIKE :search ESCAPE '\\'");
    assertThat(statement.sql()).contains("ORDER BY node.name DESC, node.id ASC");
    assertThat(statement.sql()).contains("LIMIT :limit OFFSET :offset");
    assertThat(statement.parameters())
      .containsEntry("parentId", 42L)
      .containsEntry("active", true)
      .containsEntry("slug", "gpu")
      .containsEntry("search", "%100\\%\\_gpu\\\\x%")
      .containsEntry("limit", 25)
      .containsEntry("offset", 50L);
  }

  @Test
  void buildsRootAndAllScopesWithoutChangingRuntimeValuesIntoSql() {
    AdminCategoryJdbcSql sql = new AdminCategoryJdbcSql(NODE, CATEGORY_SYSTEM);

    assertThat(
      sql
        .list(
          new AdminCategoryListQuery(
            AdminCategoryParentScope.roots(),
            new AdminCategoryFilters(null, null, null),
            new PageCriteria(0, 20),
            new SortCriteria(List.of(new SortOrder("id", SortDirection.ASC)))
          )
        )
        .sql()
    ).contains("node.parent_id IS NULL");

    AdminCategoryJdbcSql.Statement allStatement = sql.list(
      new AdminCategoryListQuery(
        AdminCategoryParentScope.all(),
        new AdminCategoryFilters(null, "gpu'); DROP TABLE data_category; --", null),
        new PageCriteria(0, 20),
        new SortCriteria(List.of(new SortOrder("updatedAt", SortDirection.DESC)))
      )
    );

    assertThat(allStatement.sql()).doesNotContain("DROP TABLE");
    assertThat(allStatement.parameters().get("search").toString()).contains("drop table");
  }

  @Test
  void rejectsUnknownSortKeysAtSqlBoundary() {
    AdminCategoryJdbcSql sql = new AdminCategoryJdbcSql(NODE, CATEGORY_SYSTEM);

    assertThatThrownBy(() ->
      sql.list(
        new AdminCategoryListQuery(
          AdminCategoryParentScope.all(),
          new AdminCategoryFilters(null, null, null),
          new PageCriteria(0, 20),
          new SortCriteria(List.of(new SortOrder("node.name desc", SortDirection.ASC)))
        )
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unsupported category sort");
  }

  @Test
  void buildsExactSystemOwnedBatchBreadcrumbSqlWithStablePathOrder() {
    AdminCategoryJdbcSql.Statement statement = new AdminCategoryJdbcSql(
      NODE,
      CATEGORY_SYSTEM
    ).parentOptionBreadcrumbsByIds(List.of(7L, 11L, 13L));

    assertThat(statement.sql()).isEqualTo(
      """
      SELECT
        closure.descendant_id AS parent_option_id,
        node.id AS id,
        node.parent_id AS parent_id,
        node.sort_rank AS sort_rank,
        node.name AS name,
        node.slug AS slug,
        node.active AS active,
        node.created_at AS created_at,
        node.updated_at AS updated_at,
        closure.depth AS depth
      FROM data_category_closure closure
      JOIN data_category node ON node.id = closure.ancestor_id
      WHERE closure.descendant_id IN (:parentOptionIds)
      ORDER BY closure.descendant_id ASC, closure.depth DESC
      """
    );
    assertThat(statement.parameters())
      .containsOnlyKeys("parentOptionIds")
      .containsEntry("parentOptionIds", List.of(7L, 11L, 13L));
    assertThat(statement.sql())
      .containsOnlyOnce("FROM data_category_closure closure")
      .containsOnlyOnce("JOIN data_category node")
      .doesNotContain(":systemKey", "catalogue_category", "test_category_a", "test_category_b");
  }

  @Test
  void buildsMutationSqlWithDescriptorIdentifiersAndBoundRuntimeValues() {
    AdminCategoryJdbcSql sql = new AdminCategoryJdbcSql(NODE, CATEGORY_SYSTEM);

    AdminCategoryJdbcSql.Statement insert = sql.insertNode(
      42L,
      1_500_000_000L,
      "GPU'); DROP TABLE data_category; --",
      "gpu-drop",
      true
    );
    AdminCategoryJdbcSql.Statement update = sql.updateNode(
      10L,
      null,
      2_000_000_000L,
      "Storage",
      "storage",
      false
    );
    AdminCategoryJdbcSql.Statement active = sql.updateActive(10L, true);
    AdminCategoryJdbcSql.Statement delete = sql.deleteNode(10L);

    assertThat(insert.sql())
      .contains("INSERT INTO data_category")
      .contains("parent_id, sort_rank, name, slug, active")
      .doesNotContain("DROP TABLE");
    assertThat(insert.parameters())
      .containsEntry("parentId", 42L)
      .containsEntry("sortRank", 1_500_000_000L)
      .containsEntry("name", "GPU'); DROP TABLE data_category; --")
      .containsEntry("slug", "gpu-drop")
      .containsEntry("active", true);
    assertThat(update.sql())
      .contains("UPDATE data_category")
      .contains("updated_at = CURRENT_TIMESTAMP")
      .contains("WHERE id = :id");
    assertThat(update.parameters())
      .containsEntry("id", 10L)
      .containsEntry("parentId", null)
      .containsEntry("sortRank", 2_000_000_000L);
    assertThat(active.sql())
      .contains("active = :active")
      .contains("updated_at = CURRENT_TIMESTAMP");
    assertThat(delete.sql()).contains("DELETE FROM data_category").contains("WHERE id = :id");
  }

  @Test
  void buildsMutationGuardQueriesWithSelectedSystemTablesOnly() {
    AdminCategoryJdbcSql sql = new AdminCategoryJdbcSql(NODE, CATEGORY_SYSTEM);

    AdminCategoryJdbcSql.Statement duplicateSlug = sql.existsSlug("gpu", 10L);
    AdminCategoryJdbcSql.Statement hasChildren = sql.hasChildren(10L);
    AdminCategoryJdbcSql.Statement touch = sql.touch(10L);

    assertThat(duplicateSlug.sql())
      .contains("FROM data_category")
      .contains("slug = :slug")
      .contains("id <> :excludingId");
    assertThat(duplicateSlug.parameters())
      .containsEntry("slug", "gpu")
      .containsEntry("excludingId", 10L);
    assertThat(hasChildren.sql()).contains("WHERE parent_id = :id");
    assertThat(touch.sql()).contains("SET updated_at = CURRENT_TIMESTAMP");
  }
}
