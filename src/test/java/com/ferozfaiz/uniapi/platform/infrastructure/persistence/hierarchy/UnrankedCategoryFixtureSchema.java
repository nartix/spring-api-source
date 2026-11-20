package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;

final class UnrankedCategoryFixtureSchema {

  private final JdbcTemplate jdbc;

  UnrankedCategoryFixtureSchema(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  void recreate() {
    jdbc.execute("DROP TABLE IF EXISTS test_unranked_category_closure");
    jdbc.execute("DROP TABLE IF EXISTS test_unranked_category");
    jdbc.execute(
      """
      CREATE TABLE test_unranked_category (
        id BIGSERIAL PRIMARY KEY,
        parent_id BIGINT REFERENCES test_unranked_category(id) ON DELETE SET NULL,
        slug VARCHAR(150) NOT NULL,
        name VARCHAR(255) NOT NULL,
        is_active BOOLEAN NOT NULL DEFAULT TRUE
      )
      """
    );
    jdbc.execute(
      """
      CREATE TABLE test_unranked_category_closure (
        ancestor_id BIGINT NOT NULL REFERENCES test_unranked_category(id) ON DELETE CASCADE,
        descendant_id BIGINT NOT NULL REFERENCES test_unranked_category(id) ON DELETE CASCADE,
        depth INTEGER NOT NULL,
        PRIMARY KEY (ancestor_id, descendant_id)
      )
      """
    );
  }

  Long insertNode(String slug, String name, @Nullable Long parentId) {
    return jdbc.queryForObject(
      """
      INSERT INTO test_unranked_category (slug, name, parent_id)
      VALUES (?, ?, ?)
      RETURNING id
      """,
      Long.class,
      slug,
      name,
      parentId
    );
  }

  void renameNode(Long id, String name) {
    jdbc.update("UPDATE test_unranked_category SET name = ? WHERE id = ?", name, id);
  }

  void updateSlug(Long id, String slug) {
    jdbc.update("UPDATE test_unranked_category SET slug = ? WHERE id = ?", slug, id);
  }

  void setActive(Long id, boolean active) {
    jdbc.update("UPDATE test_unranked_category SET is_active = ? WHERE id = ?", active, id);
  }

  void deleteNode(Long id) {
    jdbc.update("DELETE FROM test_unranked_category WHERE id = ?", id);
  }

  List<String> rootNamesInFallbackOrder() {
    return jdbc.queryForList(
      """
      SELECT name
      FROM test_unranked_category
      WHERE parent_id IS NULL
      ORDER BY name ASC, id ASC
      """,
      String.class
    );
  }

  List<String> childNamesInFallbackOrder(Long parentId) {
    return jdbc.queryForList(
      """
      SELECT name
      FROM test_unranked_category
      WHERE parent_id = ?
      ORDER BY name ASC, id ASC
      """,
      String.class,
      parentId
    );
  }

  List<String> treeNamesInFallbackOrder(Long rootId) {
    return jdbc.queryForList(
      """
      SELECT node.name
      FROM test_unranked_category node
      JOIN test_unranked_category_closure closure
        ON closure.descendant_id = node.id
      WHERE closure.ancestor_id = ?
        AND closure.depth > 0
      ORDER BY closure.depth ASC, node.name ASC, node.id ASC
      """,
      String.class,
      rootId
    );
  }

  List<String> allNamesInFallbackOrder() {
    return jdbc.queryForList(
      """
      SELECT name
      FROM test_unranked_category
      ORDER BY name ASC, id ASC
      """,
      String.class
    );
  }

  Map<String, Object> projectedNode(Long id) {
    return jdbc.queryForMap(
      """
      SELECT id, slug, name, parent_id, is_active
      FROM test_unranked_category
      WHERE id = ?
      """,
      id
    );
  }

  boolean isActive(Long id) {
    Boolean active = jdbc.queryForObject(
      """
      SELECT is_active
      FROM test_unranked_category
      WHERE id = ?
      """,
      Boolean.class,
      id
    );
    return Boolean.TRUE.equals(active);
  }

  boolean hasColumn(String columnName) {
    Integer count = jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_name = 'test_unranked_category'
        AND column_name = ?
      """,
      Integer.class,
      columnName
    );
    return count != null && count > 0;
  }
}
