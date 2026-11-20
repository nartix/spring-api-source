package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.ReorderCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryTreeQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.CategoryParentScope;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueCategoryPlacementTargetException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryTreeNode;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogueCategoryHierarchyJpaIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueCategoryApplicationService categoryService;

  @Autowired
  private CatalogueCategoryRepository categories;

  @Test
  void rankedCreateBeforeTargetAndAdminListUseSparseRankOrder() {
    var second = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Second", "second", true)
    );
    var first = categoryService.create(
      new UpsertCatalogueCategoryCommand(
        null,
        null,
        "First",
        "first",
        true,
        SparseOrderTarget.from(second.id(), null, null)
      )
    );

    assertThat(adminRankedCategories())
      .extracting(CatalogueCategory::slug)
      .containsExactly("first", "second");
    assertThat(sortRank(first.id())).isEqualTo(1_000_000_000L);
    assertThat(sortRank(second.id())).isEqualTo(2_000_000_000L);
  }

  @Test
  void rankedReorderPreservesClosureAndMovesWithinSiblingOrder() {
    var first = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "First", "first", true)
    );
    var second = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Second", "second", true)
    );
    var third = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Third", "third", true)
    );

    categoryService.reorder(
      third.id(),
      new ReorderCatalogueCategoryCommand(null, SparseOrderTarget.from(null, first.id(), null))
    );

    assertThat(adminRankedCategories())
      .extracting(CatalogueCategory::slug)
      .containsExactly("first", "third", "second");
    assertThat(selfDepth(third.id())).isZero();
  }

  @Test
  void rankedMovePlacesCategoryWithinDestinationSiblingOrder() {
    var source = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Source", "source", true)
    );
    var target = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Target", "target", true)
    );
    var existing = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, target.id(), "Existing", "existing", true)
    );
    var moving = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, source.id(), "Moving", "moving", true)
    );

    categoryService.move(
      moving.id(),
      target.id(),
      SparseOrderTarget.from(existing.id(), null, null)
    );

    assertThat(childSlugs(target.id())).containsExactly("moving", "existing");
    assertThat(adminRankedCategories(target.id()))
      .extracting(CatalogueCategory::slug)
      .containsExactly("moving", "existing");
    assertThat(categories.findById(moving.id()).orElseThrow().parentId()).isEqualTo(target.id());
  }

  @Test
  void rankedCreateReorderAndMovePreserveTargetIndexPlacement() {
    var first = createCategory("First", "first", null);
    var second = createCategory("Second", "second", null);
    var third = createCategory("Third", "third", null);
    var inserted = categoryService.create(
      new UpsertCatalogueCategoryCommand(
        null,
        null,
        "Inserted",
        "inserted",
        true,
        SparseOrderTarget.from(null, null, 1)
      )
    );

    assertThat(adminRankedCategories())
      .extracting(CatalogueCategory::slug)
      .containsExactly("first", "inserted", "second", "third");

    categoryService.reorder(
      third.id(),
      new ReorderCatalogueCategoryCommand(null, SparseOrderTarget.from(null, null, 0))
    );

    assertThat(adminRankedCategories())
      .extracting(CatalogueCategory::slug)
      .containsExactly("third", "first", "inserted", "second");

    var destination = createCategory("Destination", "destination", null);
    createCategory("Destination First", "destination-first", destination.id());
    createCategory("Destination Last", "destination-last", destination.id());

    categoryService.move(second.id(), destination.id(), SparseOrderTarget.from(null, null, 1));

    assertThat(childSlugs(destination.id())).containsExactly(
      "destination-first",
      "second",
      "destination-last"
    );
    assertThat(categories.findById(second.id()).orElseThrow().parentId()).isEqualTo(
      destination.id()
    );
    assertThat(sortRank(first.id())).isLessThan(sortRank(inserted.id()));
  }

  @Test
  void rootChildSubtreeAndRootMovesPreserveClosureTableSemantics() {
    var root = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Components", "components", true)
    );
    var child = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, root.id(), "Storage", "storage", true)
    );
    var grandchild = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, child.id(), "NVMe SSDs", "nvme-ssds", true)
    );

    assertThat(categories.findSelfAndDescendantIds("components")).containsExactly(
      root.id(),
      child.id(),
      grandchild.id()
    );
    assertThat(categories.isDescendantOf(grandchild.id(), root.id())).isTrue();

    categoryService.move(child.id(), null);

    assertThat(categories.findSelfAndDescendantIds("components")).containsExactly(root.id());
    assertThat(categories.findSelfAndDescendantIds("storage")).containsExactly(
      child.id(),
      grandchild.id()
    );
  }

  @Test
  void adminCategoryNodeReadsIncludeDepth() {
    var root = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Components", "components", true)
    );
    var child = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, root.id(), "Storage", "storage", true)
    );
    var grandchild = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, child.id(), "NVMe SSDs", "nvme-ssds", true)
    );

    assertThat(categories.findNodeById(root.id()).orElseThrow().depth()).isZero();
    assertThat(categories.findNodeById(grandchild.id()).orElseThrow().depth()).isEqualTo(2);

    assertThat(
      categories
        .findAllNodes(
          CategoryParentScope.all(),
          new PageCriteria(0, 10),
          new SortCriteria(List.of(new SortOrder("id", SortDirection.ASC)))
        )
        .content()
    )
      .extracting(node -> node.category().slug(), CatalogueCategoryNode::depth)
      .contains(
        org.assertj.core.groups.Tuple.tuple("components", 0),
        org.assertj.core.groups.Tuple.tuple("storage", 1),
        org.assertj.core.groups.Tuple.tuple("nvme-ssds", 2)
      );
  }

  @Test
  void breadcrumbReadsNestedPathInRootToTargetOrderIncludingInactiveCategories() {
    var root = createCategory("Components", "components", null);
    var child = createCategory("Storage", "storage", root.id());
    var grandchild = createCategory("NVMe SSDs", "nvme-ssds", child.id());
    categoryService.deactivate(child.id());

    assertThat(categories.findBreadcrumbById(grandchild.id()))
      .extracting(
        item -> item.category().slug(),
        item -> item.category().active(),
        item -> item.depth()
      )
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("components", true, 0),
        org.assertj.core.groups.Tuple.tuple("storage", false, 1),
        org.assertj.core.groups.Tuple.tuple("nvme-ssds", true, 2)
      );
  }

  @Test
  void breadcrumbReadsRootCategoryAsSelfRowWithDepthZero() {
    var root = createCategory("Components", "components", null);

    assertThat(categories.findBreadcrumbById(root.id()))
      .singleElement()
      .satisfies(item -> {
        assertThat(item.category().id()).isEqualTo(root.id());
        assertThat(item.category().parentId()).isNull();
        assertThat(item.depth()).isZero();
      });
  }

  @Test
  void breadcrumbReflectsCurrentPathAfterSubtreeMove() {
    var oldRoot = createCategory("Components", "components", null);
    var newRoot = createCategory("Peripherals", "peripherals", null);
    var child = createCategory("Storage", "storage", oldRoot.id());
    var grandchild = createCategory("NVMe SSDs", "nvme-ssds", child.id());

    categoryService.move(child.id(), newRoot.id());

    assertThat(categories.findBreadcrumbById(grandchild.id()))
      .extracting(item -> item.category().slug(), item -> item.depth())
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("peripherals", 0),
        org.assertj.core.groups.Tuple.tuple("storage", 1),
        org.assertj.core.groups.Tuple.tuple("nvme-ssds", 2)
      );
  }

  @Test
  void breadcrumbReadForTwentyCategoryPathStaysWithinPerformanceTarget() {
    CatalogueCategory parent = null;
    for (int index = 1; index <= 20; index++) {
      parent = createCategory(
        "Category " + index,
        "category-" + index,
        parent == null ? null : parent.id()
      );
    }

    long started = System.nanoTime();
    var breadcrumb = categories.findBreadcrumbById(parent.id());
    long elapsedNanos = System.nanoTime() - started;

    assertThat(breadcrumb).hasSize(20);
    assertThat(breadcrumb)
      .extracting(item -> item.category().slug())
      .containsExactly(
        "category-1",
        "category-2",
        "category-3",
        "category-4",
        "category-5",
        "category-6",
        "category-7",
        "category-8",
        "category-9",
        "category-10",
        "category-11",
        "category-12",
        "category-13",
        "category-14",
        "category-15",
        "category-16",
        "category-17",
        "category-18",
        "category-19",
        "category-20"
      );
    assertThat(elapsedNanos).isLessThan(1_000_000_000L);
  }

  @Test
  void adminCategoryTreeUsesRecursiveSiblingSortOrder() {
    var rootC = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "C", "c", true)
    );
    var rootA = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "A", "a", true)
    );
    categoryService.create(new UpsertCatalogueCategoryCommand(null, rootC.id(), "Z", "z", true));
    categoryService.create(new UpsertCatalogueCategoryCommand(null, rootC.id(), "K", "k", true));
    categoryService.create(new UpsertCatalogueCategoryCommand(null, rootA.id(), "AZ", "az", true));

    assertThat(
      categories
        .findTree(
          new PageCriteria(0, 1000),
          new SortCriteria(
            List.of(
              new SortOrder("sortRank", SortDirection.ASC),
              new SortOrder("id", SortDirection.ASC)
            )
          )
        )
        .content()
    )
      .extracting(
        node -> node.category().slug(),
        CatalogueCategoryTreeNode::depth,
        CatalogueCategoryTreeNode::hasChildren
      )
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("c", 0, true),
        org.assertj.core.groups.Tuple.tuple("z", 1, false),
        org.assertj.core.groups.Tuple.tuple("k", 1, false),
        org.assertj.core.groups.Tuple.tuple("a", 0, true),
        org.assertj.core.groups.Tuple.tuple("az", 1, false)
      );

    assertThat(
      categories
        .findTree(
          new PageCriteria(0, 1000),
          new SortCriteria(
            List.of(
              new SortOrder("name", SortDirection.ASC),
              new SortOrder("id", SortDirection.ASC)
            )
          )
        )
        .content()
    )
      .extracting(node -> node.category().slug())
      .containsExactly("a", "az", "c", "k", "z");
  }

  @Test
  void defaultRankedCategoryTreeReturnsFlatDepthFirstOrder() {
    var a = createCategory("A", "a", null);
    var b = createCategory("B", "b", a.id());
    createCategory("C", "c", b.id());
    var d = createCategory("D", "d", null);
    createCategory("K", "k", d.id());

    assertThat(categories.findTree(new PageCriteria(0, 100), defaultTreeSort()).content())
      .extracting(
        node -> node.category().slug(),
        CatalogueCategoryTreeNode::depth,
        CatalogueCategoryTreeNode::hasChildren
      )
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("a", 0, true),
        org.assertj.core.groups.Tuple.tuple("b", 1, true),
        org.assertj.core.groups.Tuple.tuple("c", 2, false),
        org.assertj.core.groups.Tuple.tuple("d", 0, true),
        org.assertj.core.groups.Tuple.tuple("k", 1, false)
      );
  }

  @Test
  void defaultRankedCategoryTreeUsesIdTieBreakerForEqualSiblingRanks() {
    var first = createCategory("Zulu", "zulu", null);
    var second = createCategory("Alpha", "alpha", null);
    jdbc.update(
      "UPDATE catalogue_category SET sort_rank = ? WHERE id IN (?, ?)",
      1_000_000_000L,
      first.id(),
      second.id()
    );

    assertThat(first.id()).isLessThan(second.id());
    assertThat(categories.findTree(new PageCriteria(0, 100), defaultTreeSort()).content())
      .extracting(node -> node.category().id())
      .containsExactly(first.id(), second.id());
  }

  @Test
  void genericNameSortKeepsDescendantsUnderSortedParents() {
    var root = createCategory("Root", "root", null);
    var alpha = createCategory("Alpha", "alpha", root.id());
    createCategory("Zulu", "zulu", root.id());
    createCategory("Beta", "beta", root.id());
    createCategory("Omega", "omega", alpha.id());

    assertThat(
      categories
        .findTree(
          new PageCriteria(0, 100),
          new SortCriteria(List.of(new SortOrder("name", SortDirection.DESC)))
        )
        .content()
    )
      .extracting(node -> node.category().slug(), CatalogueCategoryTreeNode::depth)
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("root", 0),
        org.assertj.core.groups.Tuple.tuple("zulu", 1),
        org.assertj.core.groups.Tuple.tuple("beta", 1),
        org.assertj.core.groups.Tuple.tuple("alpha", 1),
        org.assertj.core.groups.Tuple.tuple("omega", 2)
      );
  }

  @Test
  void categoryTreePaginationUsesFlatTreeRowsAndTotalCount() {
    var a = createCategory("A", "a", null);
    var b = createCategory("B", "b", a.id());
    createCategory("C", "c", b.id());
    var d = createCategory("D", "d", null);
    createCategory("K", "k", d.id());

    var firstPage = categories.findTree(new PageCriteria(0, 2), defaultTreeSort());
    var secondPage = categories.findTree(new PageCriteria(1, 2), defaultTreeSort());

    assertThat(firstPage.content())
      .extracting(node -> node.category().slug())
      .containsExactly("a", "b");
    assertThat(secondPage.content())
      .extracting(node -> node.category().slug())
      .containsExactly("c", "d");
    assertThat(firstPage.totalElements()).isEqualTo(5);
    assertThat(firstPage.totalPages()).isEqualTo(3);
    assertThat(secondPage.totalElements()).isEqualTo(5);
    assertThat(secondPage.totalPages()).isEqualTo(3);
  }

  @Test
  void categoryTreeExactRequestedSizeReturnsCompleteFirstLastPageMetadata() {
    var a = createCategory("A", "a", null);
    createCategory("B", "b", a.id());
    createCategory("C", "c", null);

    var page = categories.findTree(new PageCriteria(0, 3), defaultTreeSort());

    assertThat(categories.countTreeRows()).isEqualTo(3);
    assertThat(page.content()).hasSize(3);
    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.first()).isTrue();
    assertThat(page.last()).isTrue();
  }

  @Test
  void categoryTreeForOneThousandRowsStaysWithinPerformanceTarget() {
    for (int index = 1; index <= 1_000; index++) {
      insertCategory("Category " + index, "category-" + index, null);
    }

    long started = System.nanoTime();
    var page = categories.findTree(new PageCriteria(0, 1_000), defaultTreeSort());
    long elapsedNanos = System.nanoTime() - started;

    assertThat(page.content()).hasSize(1_000);
    assertThat(page.totalElements()).isEqualTo(1_000);
    assertThat(page.first()).isTrue();
    assertThat(page.last()).isTrue();
    assertThat(elapsedNanos).isLessThan(2_000_000_000L);
  }

  @Test
  void applicationTreeReadRejectsRowsAboveRequestedSizeBeforePartialTreeSuccess() {
    for (int index = 1; index <= 1_001; index++) {
      insertCategory("Category " + index, "category-" + index, null);
    }

    assertThatThrownBy(() ->
      categoryService.findTree(
        new AdminCatalogueCategoryTreeQuery(new PageCriteria(0, 1_000), defaultTreeSort())
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, ex -> {
      assertThat(ex.code()).isEqualTo("CATEGORY_TREE_TOO_LARGE");
      assertThat(ex.messageKey()).isEqualTo("category.application.tree.too-large");
    });
  }

  @Test
  void applicationTreeReadRejectsRequestedSizeBelowTotalBeforePartialTreeSuccess() {
    var a = createCategory("A", "a", null);
    createCategory("B", "b", a.id());
    createCategory("C", "c", null);

    assertThatThrownBy(() ->
      categoryService.findTree(
        new AdminCatalogueCategoryTreeQuery(new PageCriteria(0, 2), defaultTreeSort())
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, ex -> {
      assertThat(ex.code()).isEqualTo("CATEGORY_TREE_TOO_LARGE");
      assertThat(ex.messageKey()).isEqualTo("category.application.tree.too-large");
    });
  }

  @Test
  void moveRejectsPlacementTargetOutsideDestinationSiblingGroupAndRollsBackParentChange() {
    var source = createCategory("Source", "source", null);
    var destination = createCategory("Destination", "destination", null);
    var outsideTarget = createCategory("Outside", "outside", source.id());
    var moving = createCategory("Moving", "moving", source.id());
    createCategory("Destination Child", "destination-child", destination.id());

    assertThatThrownBy(() ->
      categoryService.move(
        moving.id(),
        destination.id(),
        SparseOrderTarget.from(outsideTarget.id(), null, null)
      )
    ).isInstanceOf(CatalogueCategoryPlacementTargetException.class);

    assertThat(categories.findById(moving.id()).orElseThrow().parentId()).isEqualTo(source.id());
    assertThat(categories.isDescendantOf(moving.id(), source.id())).isTrue();
    assertThat(categories.isDescendantOf(moving.id(), destination.id())).isFalse();
  }

  @Test
  void concurrentSiblingReordersPreserveRankAndClosureIntegrity() {
    var parent = createCategory("Components", "components", null);
    var first = createCategory("First", "first", parent.id());
    var second = createCategory("Second", "second", parent.id());
    var third = createCategory("Third", "third", parent.id());
    var fourth = createCategory("Fourth", "fourth", parent.id());
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> moveFourthBeforeFirst = executor.submit(() -> {
        await(start);
        categoryService.reorder(
          fourth.id(),
          new ReorderCatalogueCategoryCommand(
            parent.id(),
            SparseOrderTarget.from(first.id(), null, null)
          )
        );
      });
      Future<?> moveSecondAfterThird = executor.submit(() -> {
        await(start);
        categoryService.reorder(
          second.id(),
          new ReorderCatalogueCategoryCommand(
            parent.id(),
            SparseOrderTarget.from(null, third.id(), null)
          )
        );
      });

      start.countDown();
      complete(moveFourthBeforeFirst);
      complete(moveSecondAfterThird);
    } finally {
      executor.shutdownNow();
    }

    assertThat(childSlugs(parent.id())).containsExactlyInAnyOrder(
      "first",
      "second",
      "third",
      "fourth"
    );
    assertThat(childSortRanks(parent.id())).hasSize(4).doesNotContainNull().doesNotHaveDuplicates();
    assertThat(selfDepth(first.id())).isZero();
    assertThat(selfDepth(second.id())).isZero();
    assertThat(categories.isDescendantOf(second.id(), parent.id())).isTrue();
    assertThat(categories.isDescendantOf(fourth.id(), parent.id())).isTrue();
  }

  @Test
  void emptyCategoryTreeReturnsEmptyFirstLastPage() {
    var page = categories.findTree(new PageCriteria(0, 100), defaultTreeSort());

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
    assertThat(page.first()).isTrue();
    assertThat(page.last()).isTrue();
  }

  @Test
  void hierarchyRejectsSelfAndDescendantMoves() {
    var root = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Components", "components", true)
    );
    var child = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, root.id(), "Storage", "storage", true)
    );

    assertThatThrownBy(() -> categoryService.move(root.id(), root.id())).isInstanceOf(
      CatalogueConflictException.class
    );
    assertThatThrownBy(() -> categoryService.move(root.id(), child.id())).isInstanceOf(
      CatalogueConflictException.class
    );
  }

  private CatalogueCategory createCategory(String name, String slug, Long parentId) {
    return categoryService.create(
      new UpsertCatalogueCategoryCommand(null, parentId, name, slug, true)
    );
  }

  private static SortCriteria defaultTreeSort() {
    return new SortCriteria(
      List.of(new SortOrder("sortRank", SortDirection.ASC), new SortOrder("id", SortDirection.ASC))
    );
  }

  private List<CatalogueCategory> adminRankedCategories() {
    return adminRankedCategories(null);
  }

  private List<CatalogueCategory> adminRankedCategories(Long parentId) {
    return categories
      .findAll(
        parentId == null ? CategoryParentScope.all() : CategoryParentScope.children(parentId),
        new PageCriteria(0, 10),
        new SortCriteria(
          List.of(
            new SortOrder("sortRank", SortDirection.ASC),
            new SortOrder("id", SortDirection.ASC)
          )
        )
      )
      .content();
  }

  private Long sortRank(Long id) {
    return jdbc.queryForObject(
      "SELECT sort_rank FROM catalogue_category WHERE id = ?",
      Long.class,
      id
    );
  }

  private Integer selfDepth(Long id) {
    return jdbc.queryForObject(
      """
      SELECT depth
      FROM catalogue_category_closure
      WHERE ancestor_id = ?
        AND descendant_id = ?
      """,
      Integer.class,
      id,
      id
    );
  }

  private List<String> childSlugs(Long parentId) {
    return jdbc.queryForList(
      """
      SELECT slug
      FROM catalogue_category
      WHERE parent_id = ?
      ORDER BY sort_rank ASC, id ASC
      """,
      String.class,
      parentId
    );
  }

  private List<Long> childSortRanks(Long parentId) {
    return jdbc.queryForList(
      """
      SELECT sort_rank
      FROM catalogue_category
      WHERE parent_id = ?
      ORDER BY sort_rank ASC, id ASC
      """,
      Long.class,
      parentId
    );
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("timed out waiting for concurrent reorder start");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted waiting for concurrent reorder start", exception);
    }
  }

  private static void complete(Future<?> future) {
    try {
      future.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted waiting for concurrent reorder", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new AssertionError("concurrent reorder failed", exception);
    }
  }
}
