package com.ferozfaiz.uniapi.data.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.data.domain.exception.CategoryHierarchyCycleException;
import com.ferozfaiz.uniapi.data.domain.model.Category;
import com.ferozfaiz.uniapi.data.domain.model.CategoryDeleteEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveMode;
import com.ferozfaiz.uniapi.data.domain.model.CategoryNode;
import com.ferozfaiz.uniapi.data.domain.repository.CategoryRepository;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Transactional
class CategoryRepositoryAdapterTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T06:00:00Z");
  private static final Duration STATEMENT_TIMEOUT = Duration.ofSeconds(25);

  @Autowired
  private CategoryRepository categories;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void createsRootAndChildClosurePaths() {
    Category root = categories.saveRoot(category(null, "finance", "Finance"));
    Category child = categories.saveChild(category(root.id(), "reports", "Reports"), root.id());

    assertThat(categories.findRoots()).extracting(Category::slug).containsExactly("finance");
    assertThat(categories.findChildren(root.id()))
      .extracting(Category::slug)
      .containsExactly("reports");
    assertThat(categories.findAncestors(child.id()))
      .extracting(Category::slug)
      .containsExactly("finance");
    assertThat(depth(root.id(), child.id())).isEqualTo(1);
    assertThat(depth(child.id(), child.id())).isZero();
  }

  @Test
  void ranksInsertedRootsAndReadsThemBySparseRank() {
    Category second = categories.saveRoot(category(null, "second", "Second"));
    Category first = categories.saveRoot(
      category(null, "first", "First"),
      SparseOrderTarget.from(second.id(), null, null)
    );

    assertThat(categories.findRoots())
      .extracting(Category::slug)
      .containsExactly("first", "second");
    assertThat(sortRank(first.id())).isEqualTo(1_000_000_000L);
    assertThat(sortRank(second.id())).isEqualTo(2_000_000_000L);
  }

  @Test
  void reordersSameParentCategoriesWithoutChangingHierarchyPaths() {
    Category first = categories.saveRoot(category(null, "first", "First"));
    Category second = categories.saveRoot(category(null, "second", "Second"));
    Category third = categories.saveRoot(category(null, "third", "Third"));

    categories.move(
      third.id(),
      null,
      CategoryMoveMode.LEAF_ONLY,
      STATEMENT_TIMEOUT,
      SparseOrderTarget.from(null, first.id(), null)
    );

    assertThat(categories.findRoots())
      .extracting(Category::slug)
      .containsExactly("first", "third", "second");
    assertThat(depth(third.id(), third.id())).isZero();
  }

  @Test
  void movesCategoryIntoDestinationSiblingRankPosition() {
    Category source = categories.saveRoot(category(null, "source", "Source"));
    Category target = categories.saveRoot(category(null, "target", "Target"));
    Category existing = categories.saveChild(
      category(target.id(), "existing", "Existing"),
      target.id()
    );
    Category moving = categories.saveChild(category(source.id(), "moving", "Moving"), source.id());

    categories.move(
      moving.id(),
      target.id(),
      CategoryMoveMode.LEAF_ONLY,
      STATEMENT_TIMEOUT,
      SparseOrderTarget.from(existing.id(), null, null)
    );

    assertThat(categories.findChildren(target.id()))
      .extracting(Category::slug)
      .containsExactly("moving", "existing");
    assertThat(categories.findById(moving.id()).orElseThrow().parentId()).isEqualTo(target.id());
  }

  @Test
  void movesSubtreeAndRejectsCycles() {
    Category finance = categories.saveRoot(category(null, "finance", "Finance"));
    Category reports = categories.saveChild(
      category(finance.id(), "reports", "Reports"),
      finance.id()
    );
    Category monthly = categories.saveChild(
      category(reports.id(), "monthly", "Monthly"),
      reports.id()
    );
    Category operations = categories.saveRoot(category(null, "operations", "Operations"));

    CategoryMoveEstimate estimate = categories.estimateMove(reports.id(), operations.id());

    assertThat(estimate.subtreeSize()).isEqualTo(2);
    assertThat(estimate.oldAncestorCount()).isEqualTo(1);
    assertThat(estimate.newAncestorCount()).isEqualTo(1);
    assertThat(estimate.estimatedDeleteRows()).isEqualTo(2);
    assertThat(estimate.estimatedInsertRows()).isEqualTo(2);
    assertThat(estimate.estimatedTotalRows()).isEqualTo(4);
    assertThat(estimate.subtreeMaxDepth()).isEqualTo(1);
    assertThat(estimate.newParentDepth()).isZero();
    assertThat(estimate.resultingMaxDepth()).isEqualTo(2);
    assertThat(estimate.parentChangeRequired()).isTrue();

    categories.move(reports.id(), operations.id(), CategoryMoveMode.SUBTREE, STATEMENT_TIMEOUT);

    Category moved = categories.findById(reports.id()).orElseThrow();
    assertThat(moved.parentId()).isEqualTo(operations.id());
    assertThat(categories.findDescendants(finance.id())).isEmpty();
    assertThat(categories.findDescendants(operations.id()))
      .extracting(Category::slug)
      .containsExactly("reports", "monthly");
    assertThat(categories.findAncestors(monthly.id()))
      .extracting(Category::slug)
      .containsExactly("operations", "reports");
    assertThat(
      categories.estimateMove(reports.id(), operations.id()).parentChangeRequired()
    ).isFalse();
    assertThat(
      categories.estimateMove(reports.id(), operations.id()).estimatedTotalRows()
    ).isZero();

    assertThatThrownBy(() ->
      categories.move(operations.id(), monthly.id(), CategoryMoveMode.SUBTREE, STATEMENT_TIMEOUT)
    ).isInstanceOf(CategoryHierarchyCycleException.class);
  }

  @Test
  void estimatesSubtreeDeleteCost() {
    Category finance = categories.saveRoot(category(null, "finance", "Finance"));
    Category reports = categories.saveChild(
      category(finance.id(), "reports", "Reports"),
      finance.id()
    );
    categories.saveChild(category(reports.id(), "monthly", "Monthly"), reports.id());

    CategoryDeleteEstimate estimate = categories.estimateDelete(reports.id());

    assertThat(estimate.subtreeSize()).isEqualTo(2);
    assertThat(estimate.estimatedClosureRows()).isEqualTo(5);
    assertThat(categories.countSelfAndDescendants(finance.id())).isEqualTo(3);
  }

  @Test
  void fetchesFlatTreeNodeSets() {
    Category finance = categories.saveRoot(category(null, "finance", "Finance"));
    Category reports = categories.saveChild(
      category(finance.id(), "reports", "Reports"),
      finance.id()
    );
    Category monthly = categories.saveChild(
      category(reports.id(), "monthly", "Monthly"),
      reports.id()
    );
    categories.saveRoot(category(null, "operations", "Operations"));

    assertThat(categories.findAllForTree())
      .extracting(Category::slug)
      .containsExactlyInAnyOrder("finance", "operations", "reports", "monthly");
    assertThat(categories.findSelfAndDescendants(reports.id()))
      .extracting(Category::slug)
      .containsExactly("reports", "monthly");
  }

  @Test
  void listsFlatCategoriesOrderedByDepth() {
    Category finance = categories.saveRoot(category(null, "finance", "Finance"));
    Category reports = categories.saveChild(
      category(finance.id(), "reports", "Reports"),
      finance.id()
    );
    Category monthly = categories.saveChild(
      category(reports.id(), "monthly", "Monthly"),
      reports.id()
    );
    categories.saveRoot(category(null, "operations", "Operations"));

    PageResult<CategoryNode> result = categories.findAll(
      new PageCriteria(0, 10),
      new SortCriteria(
        List.of(new SortOrder("depth", SortDirection.ASC), new SortOrder("name", SortDirection.ASC))
      )
    );

    assertThat(result.content())
      .extracting(node -> node.category().slug())
      .containsExactly("finance", "operations", "reports", "monthly");
    assertThat(result.content()).extracting(CategoryNode::depth).containsExactly(0, 0, 1, 2);
    assertThat(result.totalElements()).isEqualTo(4);
    assertThat(result.totalPages()).isEqualTo(1);
    assertThat(result.first()).isTrue();
    assertThat(result.last()).isTrue();

    PageResult<CategoryNode> secondPage = categories.findAll(
      new PageCriteria(1, 2),
      new SortCriteria(
        List.of(new SortOrder("depth", SortDirection.ASC), new SortOrder("name", SortDirection.ASC))
      )
    );

    assertThat(secondPage.content())
      .extracting(node -> node.category().slug())
      .containsExactly("reports", "monthly");
  }

  @Test
  void findsCategoryNodeByIdWithDepth() {
    Category finance = categories.saveRoot(category(null, "finance", "Finance"));
    Category reports = categories.saveChild(
      category(finance.id(), "reports", "Reports"),
      finance.id()
    );
    Category monthly = categories.saveChild(
      category(reports.id(), "monthly", "Monthly"),
      reports.id()
    );

    assertThat(categories.findNodeById(finance.id()).orElseThrow().depth()).isZero();
    assertThat(categories.findNodeById(monthly.id()).orElseThrow().depth()).isEqualTo(2);
    assertThat(categories.findNodeById(999_999L)).isEmpty();
  }

  @Test
  void listsFlatCategoriesInTreeOrderWithSiblingSorts() {
    Category rootB = categories.saveRoot(category(null, "root-b", "Root B", NOW.plusDays(1)));
    Category rootA = categories.saveRoot(category(null, "root-a", "Root A", NOW));
    categories.saveChild(category(rootA.id(), "child-b", "Child B", NOW.plusDays(2)), rootA.id());
    categories.saveChild(category(rootA.id(), "child-a", "Child A", NOW.plusDays(3)), rootA.id());
    categories.saveChild(category(rootB.id(), "child-c", "Child C", NOW.plusDays(4)), rootB.id());

    PageResult<CategoryNode> nameSorted = categories.findAll(
      new PageCriteria(0, 10),
      new SortCriteria(
        List.of(new SortOrder("tree", SortDirection.ASC), new SortOrder("name", SortDirection.ASC))
      )
    );

    assertThat(nameSorted.content())
      .extracting(node -> node.category().slug())
      .containsExactly("root-a", "child-a", "child-b", "root-b", "child-c");
    assertThat(nameSorted.content()).extracting(CategoryNode::depth).containsExactly(0, 1, 1, 0, 1);

    PageResult<CategoryNode> dateSorted = categories.findAll(
      new PageCriteria(0, 10),
      new SortCriteria(
        List.of(
          new SortOrder("tree", SortDirection.ASC),
          new SortOrder("createdAt", SortDirection.DESC)
        )
      )
    );

    assertThat(dateSorted.content())
      .extracting(node -> node.category().slug())
      .containsExactly("root-b", "child-c", "root-a", "child-a", "child-b");
  }

  @Test
  void rollsBackInsertedCategories() {
    categories.saveRoot(category(null, "temporary", "Temporary"));

    TestTransaction.flagForRollback();
    TestTransaction.end();
    TestTransaction.start();

    assertThat(categories.findRoots()).isEmpty();
  }

  private Integer depth(Long ancestorId, Long descendantId) {
    return jdbc.queryForObject(
      """
      SELECT depth
      FROM data_category_closure
      WHERE ancestor_id = ?
        AND descendant_id = ?
      """,
      Integer.class,
      ancestorId,
      descendantId
    );
  }

  private Long sortRank(Long id) {
    return jdbc.queryForObject(
      """
      SELECT sort_rank
      FROM data_category
      WHERE id = ?
      """,
      Long.class,
      id
    );
  }

  private static Category category(Long parentId, String slug, String name) {
    return category(parentId, slug, name, NOW);
  }

  private static Category category(
    Long parentId,
    String slug,
    String name,
    OffsetDateTime createdAt
  ) {
    return new Category(
      null,
      parentId,
      slug,
      name,
      null,
      true,
      createdAt,
      createdAt,
      "test",
      "test"
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
