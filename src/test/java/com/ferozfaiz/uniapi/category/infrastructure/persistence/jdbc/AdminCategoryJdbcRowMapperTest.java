package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminCategoryJdbcRowMapperTest {

  private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-07-01T12:00:00Z");
  private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-07-02T12:00:00Z");

  @Test
  void mapsCategoryNodeProjection() throws Exception {
    ResultSet row = categoryRow();
    when(row.getInt("depth")).thenReturn(2);
    when(row.getBoolean("has_children")).thenReturn(true);

    var node = AdminCategoryJdbcRowMapper.categoryNode().mapRow(row, 0);

    assertThat(node.category().id()).isEqualTo(7L);
    assertThat(node.category().parentId()).isEqualTo(3L);
    assertThat(node.category().sortRank()).isEqualTo(1000L);
    assertThat(node.category().name()).isEqualTo("GPUs");
    assertThat(node.category().slug()).isEqualTo("gpus");
    assertThat(node.category().active()).isTrue();
    assertThat(node.category().createdAt()).isEqualTo(CREATED_AT);
    assertThat(node.category().updatedAt()).isEqualTo(UPDATED_AT);
    assertThat(node.depth()).isEqualTo(2);
    assertThat(node.hasChildren()).isTrue();
  }

  @Test
  void mapsBreadcrumbProjection() throws Exception {
    ResultSet row = categoryRow();
    when(row.getInt("depth")).thenReturn(1);

    var item = AdminCategoryJdbcRowMapper.breadcrumbItem().mapRow(row, 0);

    assertThat(item.category().id()).isEqualTo(7L);
    assertThat(item.depth()).isEqualTo(1);
  }

  @Test
  void mapsKeyedParentOptionBreadcrumbProjection() throws Exception {
    ResultSet row = categoryRow();
    when(row.getLong("parent_option_id")).thenReturn(23L);
    when(row.getInt("depth")).thenReturn(2);

    var keyedItem = AdminCategoryJdbcRowMapper.parentOptionBreadcrumbRow().mapRow(row, 0);

    assertThat(keyedItem.parentOptionId()).isEqualTo(23L);
    assertThat(keyedItem.breadcrumbItem().category().id()).isEqualTo(7L);
    assertThat(keyedItem.breadcrumbItem().depth()).isEqualTo(2);
  }

  @Test
  void preservesPathOrderWhenKeyedBreadcrumbRowsAreRegrouped() throws Exception {
    List<AdminCategoryJdbcRowMapper.ParentOptionBreadcrumbRow> rows = List.of(
      parentOptionBreadcrumbRow(21L, 1L, 2),
      parentOptionBreadcrumbRow(21L, 7L, 1),
      parentOptionBreadcrumbRow(21L, 21L, 0),
      parentOptionBreadcrumbRow(34L, 1L, 1),
      parentOptionBreadcrumbRow(34L, 34L, 0)
    );

    Map<Long, List<AdminCategoryJdbcRowMapper.ParentOptionBreadcrumbRow>> rowsByParentOption =
      new LinkedHashMap<>();
    for (AdminCategoryJdbcRowMapper.ParentOptionBreadcrumbRow row : rows) {
      rowsByParentOption
        .computeIfAbsent(row.parentOptionId(), ignored -> new ArrayList<>())
        .add(row);
    }

    assertThat(rowsByParentOption.keySet()).containsExactly(21L, 34L);
    assertThat(rowsByParentOption.get(21L))
      .extracting(row -> row.breadcrumbItem().category().id())
      .containsExactly(1L, 7L, 21L);
    assertThat(rowsByParentOption.get(21L))
      .extracting(row -> row.breadcrumbItem().depth())
      .containsExactly(2, 1, 0);
    assertThat(rowsByParentOption.get(34L))
      .extracting(row -> row.breadcrumbItem().category().id())
      .containsExactly(1L, 34L);
    assertThat(rowsByParentOption.get(34L))
      .extracting(row -> row.breadcrumbItem().depth())
      .containsExactly(1, 0);
  }

  @Test
  void mapsParentOptionProjection() throws Exception {
    ResultSet row = categoryRow();
    when(row.getInt("depth")).thenReturn(0);
    when(row.getString("label")).thenReturn("GPUs");
    when(row.getBoolean("selectable")).thenReturn(true);
    when(row.getString("disabled_reason")).thenReturn(null);
    when(row.getString("normalized_name")).thenReturn("gpus");
    when(row.getInt("search_rank")).thenReturn(3);

    var option = AdminCategoryJdbcRowMapper.parentOptionRow().mapRow(row, 0);

    assertThat(option.option().category().id()).isEqualTo(7L);
    assertThat(option.option().label()).isEqualTo("GPUs");
    assertThat(option.normalizedName()).isEqualTo("gpus");
    assertThat(option.searchRank()).isEqualTo(3);
  }

  private static ResultSet categoryRow() throws Exception {
    return categoryRow(7L);
  }

  private static ResultSet categoryRow(Long id) throws Exception {
    ResultSet row = mock(ResultSet.class);
    when(row.getLong("id")).thenReturn(id);
    when(row.getObject("parent_id", Long.class)).thenReturn(3L);
    when(row.getObject("sort_rank", Long.class)).thenReturn(1000L);
    when(row.getString("name")).thenReturn("GPUs");
    when(row.getString("slug")).thenReturn("gpus");
    when(row.getBoolean("active")).thenReturn(true);
    when(row.getObject("created_at", OffsetDateTime.class)).thenReturn(CREATED_AT);
    when(row.getObject("updated_at", OffsetDateTime.class)).thenReturn(UPDATED_AT);
    return row;
  }

  private static AdminCategoryJdbcRowMapper.ParentOptionBreadcrumbRow parentOptionBreadcrumbRow(
    Long parentOptionId,
    Long categoryId,
    int depth
  ) throws Exception {
    ResultSet row = categoryRow(categoryId);
    when(row.getLong("parent_option_id")).thenReturn(parentOptionId);
    when(row.getInt("depth")).thenReturn(depth);
    return AdminCategoryJdbcRowMapper.parentOptionBreadcrumbRow().mapRow(row, 0);
  }
}
