package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryProvider;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogueCategoryGenericListJpaIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueAdminCategoryProvider provider;

  @Autowired
  private CatalogueCategoryApplicationService categoryService;

  @Test
  void genericProviderListDelegatesParentScopeFiltersAndSortToCataloguePersistence() {
    var root = createCategory("Components", "components", null);
    createCategory("GPU Deals", "gpu-deals", root.id());
    createCategory("GPU Hidden", "gpu-hidden", root.id(), false);
    createCategory("CPU Deals", "cpu-deals", root.id());
    createCategory("Other Root", "other-root", null);

    var result = provider.list(
      query(
        AdminCategoryParentScope.children(root.id()),
        new AdminCategoryFilters(true, "gpu", null),
        idSort()
      )
    );

    assertThat(result.content())
      .extracting(
        node -> node.category().slug(),
        node -> node.category().parentId(),
        node -> node.depth()
      )
      .containsExactly(org.assertj.core.groups.Tuple.tuple("gpu-deals", root.id(), 1));
    assertThat(result.content().getFirst().hasChildren()).isFalse();
    assertThat(result.totalElements()).isEqualTo(1);
  }

  @Test
  void genericProviderListMapsMissingPositiveParentToGenericParentError() {
    assertThatThrownBy(() ->
      provider.list(
        query(
          AdminCategoryParentScope.children(404L),
          new AdminCategoryFilters(null, null, null),
          idSort()
        )
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, ex -> {
      assertThat(ex.code()).isEqualTo("CATEGORY_PARENT_NOT_FOUND");
      assertThat(ex.messageArgs()).containsExactly(404L);
    });
  }

  @Test
  void genericProviderListPreservesAllRootsAndChildrenScopes() {
    var firstRoot = createCategory("First Root", "first-root", null);
    var secondRoot = createCategory("Second Root", "second-root", null);
    var child = createCategory("Nested Child", "nested-child", firstRoot.id());

    var roots = provider.list(
      query(AdminCategoryParentScope.roots(), new AdminCategoryFilters(null, null, null), idSort())
    );
    var all = provider.list(
      query(AdminCategoryParentScope.all(), new AdminCategoryFilters(null, null, null), idSort())
    );
    var children = provider.list(
      query(
        AdminCategoryParentScope.children(firstRoot.id()),
        new AdminCategoryFilters(null, null, null),
        idSort()
      )
    );

    assertThat(roots.content())
      .extracting(node -> node.category().id())
      .containsExactly(firstRoot.id(), secondRoot.id());
    assertThat(all.content())
      .extracting(node -> node.category().id())
      .containsExactly(firstRoot.id(), secondRoot.id(), child.id());
    assertThat(children.content())
      .extracting(node -> node.category().id())
      .containsExactly(child.id());
  }

  @Test
  void genericProviderListPreservesExactSlugAndInactiveFilters() {
    createCategory("Visible Root", "visible-root", null);
    var hidden = createCategory("Hidden Root", "hidden-root", null, false);

    var result = provider.list(
      query(
        AdminCategoryParentScope.roots(),
        new AdminCategoryFilters(false, null, "hidden-root"),
        idSort()
      )
    );

    assertThat(result.content())
      .singleElement()
      .satisfies(node -> {
        assertThat(node.category().id()).isEqualTo(hidden.id());
        assertThat(node.category().slug()).isEqualTo("hidden-root");
        assertThat(node.category().active()).isFalse();
      });
  }

  @Test
  void genericProviderListPreservesRankedSiblingAndExplicitNameOrdering() {
    var zulu = createCategory("Zulu", "zulu", null);
    var alpha = createCategory("Alpha", "alpha", null);
    var middle = createCategory("Middle", "middle", null);

    var ranked = provider.list(
      query(
        AdminCategoryParentScope.roots(),
        new AdminCategoryFilters(null, null, null),
        new SortCriteria(
          List.of(
            new SortOrder("sortRank", SortDirection.ASC),
            new SortOrder("id", SortDirection.ASC)
          )
        )
      )
    );
    var named = provider.list(
      query(
        AdminCategoryParentScope.roots(),
        new AdminCategoryFilters(null, null, null),
        new SortCriteria(
          List.of(new SortOrder("name", SortDirection.ASC), new SortOrder("id", SortDirection.ASC))
        )
      )
    );

    assertThat(ranked.content())
      .extracting(node -> node.category().id())
      .containsExactly(zulu.id(), alpha.id(), middle.id());
    assertThat(ranked.content())
      .extracting(node -> node.category().sortRank())
      .isSorted();
    assertThat(named.content())
      .extracting(node -> node.category().name())
      .containsExactly("Alpha", "Middle", "Zulu");
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

  private static AdminCategoryListQuery query(
    AdminCategoryParentScope parentScope,
    AdminCategoryFilters filters,
    SortCriteria sort
  ) {
    return new AdminCategoryListQuery(parentScope, filters, new PageCriteria(0, 20), sort);
  }

  private static SortCriteria idSort() {
    return new SortCriteria(List.of(new SortOrder("id", SortDirection.ASC)));
  }
}
