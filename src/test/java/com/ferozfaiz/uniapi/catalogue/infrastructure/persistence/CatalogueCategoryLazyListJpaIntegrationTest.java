package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryFilters;
import com.ferozfaiz.uniapi.catalogue.application.query.CategoryParentScope;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogueCategoryLazyListJpaIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueCategoryApplicationService categoryService;

  @Autowired
  private CatalogueCategoryRepository categories;

  @Test
  void listsAllRootsAndDirectChildrenWithoutRecursiveTreeTraversal() {
    var rootA = createCategory("Components", "components", null);
    var rootB = createCategory("Storage", "storage", null);
    var childA = createCategory("NVMe SSDs", "nvme-ssds", rootA.id());
    var childB = createCategory("SATA SSDs", "sata-ssds", rootA.id());
    createCategory("PCIe 5.0 Drives", "pcie-5-drives", childA.id());

    assertThat(list(CategoryParentScope.all(), AdminCatalogueCategoryFilters.none(), idSort()))
      .extracting(node -> node.category().slug())
      .containsExactly("components", "storage", "nvme-ssds", "sata-ssds", "pcie-5-drives");

    assertThat(list(CategoryParentScope.roots(), AdminCatalogueCategoryFilters.none(), rankSort()))
      .extracting(node -> node.category().slug(), CatalogueCategoryNode::depth)
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("components", 0),
        org.assertj.core.groups.Tuple.tuple("storage", 0)
      );

    assertThat(
      list(
        CategoryParentScope.children(rootA.id()),
        AdminCatalogueCategoryFilters.none(),
        rankSort()
      )
    )
      .extracting(
        node -> node.category().slug(),
        CatalogueCategoryNode::depth,
        CatalogueCategoryNode::hasChildren
      )
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("nvme-ssds", 1, true),
        org.assertj.core.groups.Tuple.tuple("sata-ssds", 1, false)
      );

    assertThat(
      list(
        CategoryParentScope.children(rootB.id()),
        AdminCatalogueCategoryFilters.none(),
        rankSort()
      )
    ).isEmpty();
  }

  @Test
  void filtersByActiveSlugAndLiteralContainsWithinParentScope() {
    var root = createCategory("Storage Root", "storage-root", null);
    createCategory("GPU 100%_\\ Deals", "gpu-deals", root.id());
    createCategory("GPU Hidden", "gpu-hidden", root.id(), false);
    createCategory("CPU Deals", "cpu-deals", root.id());
    createCategory("Other Root", "other-root", null);

    assertThat(
      list(
        CategoryParentScope.children(root.id()),
        new AdminCatalogueCategoryFilters(true, "100%_\\", null),
        idSort()
      )
    )
      .extracting(node -> node.category().slug())
      .containsExactly("gpu-deals");

    assertThat(
      list(
        CategoryParentScope.children(root.id()),
        new AdminCatalogueCategoryFilters(false, "gpu", null),
        idSort()
      )
    )
      .extracting(node -> node.category().slug())
      .containsExactly("gpu-hidden");

    assertThat(
      list(
        CategoryParentScope.all(),
        new AdminCatalogueCategoryFilters(null, null, "other-root"),
        idSort()
      )
    )
      .extracting(node -> node.category().slug())
      .containsExactly("other-root");
  }

  @Test
  void hasChildrenIgnoresCurrentFiltersAndReturnedPageWindow() {
    var root = createCategory("Storage", "storage", null);
    createCategory("Inactive Child", "inactive-child", root.id(), false);
    createCategory("Nonmatching Child", "nonmatching-child", root.id());
    createCategory("GPU Leaf", "gpu-leaf", null);

    assertThat(
      list(
        CategoryParentScope.roots(),
        new AdminCatalogueCategoryFilters(true, "storage", null),
        idSort()
      )
    )
      .extracting(node -> node.category().slug(), CatalogueCategoryNode::hasChildren)
      .containsExactly(org.assertj.core.groups.Tuple.tuple("storage", true));

    assertThat(
      categories
        .findAllNodes(
          CategoryParentScope.roots(),
          AdminCatalogueCategoryFilters.none(),
          new PageCriteria(0, 1),
          idSort()
        )
        .content()
    )
      .extracting(node -> node.category().slug(), CatalogueCategoryNode::hasChildren)
      .containsExactly(org.assertj.core.groups.Tuple.tuple("storage", true));
  }

  @Test
  void stableSortTieBreakersKeepLargeSiblingPagesBounded() {
    var parent = createCategory("Components", "components", null);
    for (int index = 0; index < 75; index++) {
      createCategory("Child " + index, "child-" + index, parent.id());
    }

    var firstPage = categories.findAllNodes(
      CategoryParentScope.children(parent.id()),
      AdminCatalogueCategoryFilters.none(),
      new PageCriteria(0, 20),
      rankSort()
    );
    var secondPage = categories.findAllNodes(
      CategoryParentScope.children(parent.id()),
      AdminCatalogueCategoryFilters.none(),
      new PageCriteria(1, 20),
      rankSort()
    );

    assertThat(firstPage.content()).hasSize(20);
    assertThat(secondPage.content()).hasSize(20);
    assertThat(firstPage.content().getFirst().category().slug()).isEqualTo("child-0");
    assertThat(secondPage.content().getFirst().category().slug()).isEqualTo("child-20");
    assertThat(firstPage.totalElements()).isEqualTo(75);
  }

  private CatalogueCategory createCategory(String name, String slug, Long parentId) {
    return createCategory(name, slug, parentId, true);
  }

  private CatalogueCategory createCategory(
    String name,
    String slug,
    Long parentId,
    boolean active
  ) {
    return categoryService.create(
      new UpsertCatalogueCategoryCommand(null, parentId, name, slug, active)
    );
  }

  private List<CatalogueCategoryNode> list(
    CategoryParentScope parentScope,
    AdminCatalogueCategoryFilters filters,
    SortCriteria sort
  ) {
    return categories.findAllNodes(parentScope, filters, new PageCriteria(0, 100), sort).content();
  }

  private static SortCriteria idSort() {
    return new SortCriteria(List.of(new SortOrder("id", SortDirection.ASC)));
  }

  private static SortCriteria rankSort() {
    return new SortCriteria(
      List.of(new SortOrder("sortRank", SortDirection.ASC), new SortOrder("id", SortDirection.ASC))
    );
  }
}
