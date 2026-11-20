package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.BreadcrumbItem;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.Category;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.CategoryNode;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.ParentOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;

public final class AdminCategoryJdbcRowMapper {

  private AdminCategoryJdbcRowMapper() {}

  public static RowMapper<Category> category() {
    return (resultSet, rowNumber) -> mapCategory(resultSet);
  }

  public static RowMapper<CategoryNode> categoryNode() {
    return (resultSet, rowNumber) ->
      new CategoryNode(
        mapCategory(resultSet),
        resultSet.getInt("depth"),
        resultSet.getBoolean("has_children")
      );
  }

  public static RowMapper<BreadcrumbItem> breadcrumbItem() {
    return (resultSet, rowNumber) ->
      new BreadcrumbItem(mapCategory(resultSet), resultSet.getInt("depth"));
  }

  public static RowMapper<ParentOptionBreadcrumbRow> parentOptionBreadcrumbRow() {
    return (resultSet, rowNumber) ->
      new ParentOptionBreadcrumbRow(
        resultSet.getLong("parent_option_id"),
        new BreadcrumbItem(mapCategory(resultSet), resultSet.getInt("depth"))
      );
  }

  public static RowMapper<ParentOptionRow> parentOptionRow() {
    return (resultSet, rowNumber) -> {
      Category category = mapCategory(resultSet);
      ParentOption option = new ParentOption(
        category,
        resultSet.getInt("depth"),
        resultSet.getString("label"),
        List.of(),
        resultSet.getBoolean("selectable"),
        resultSet.getString("disabled_reason")
      );
      return new ParentOptionRow(
        option,
        resultSet.getString("normalized_name"),
        resultSet.getInt("search_rank")
      );
    };
  }

  private static Category mapCategory(ResultSet resultSet) throws SQLException {
    return new AdminCategoryModels.Category(
      resultSet.getLong("id"),
      resultSet.getObject("parent_id", Long.class),
      resultSet.getObject("sort_rank", Long.class),
      resultSet.getString("name"),
      resultSet.getString("slug"),
      resultSet.getBoolean("active"),
      resultSet.getObject("created_at", OffsetDateTime.class),
      resultSet.getObject("updated_at", OffsetDateTime.class)
    );
  }

  public record ParentOptionRow(ParentOption option, String normalizedName, int searchRank) {}

  public record ParentOptionBreadcrumbRow(Long parentOptionId, BreadcrumbItem breadcrumbItem) {}
}
