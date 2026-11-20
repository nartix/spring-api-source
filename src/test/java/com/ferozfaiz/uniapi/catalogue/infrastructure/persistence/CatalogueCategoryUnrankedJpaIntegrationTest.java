package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.ReorderCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryFilters;
import com.ferozfaiz.uniapi.catalogue.application.query.CategoryParentScope;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "uniapi.category-systems.catalogue.ranking.enabled=false")
class CatalogueCategoryUnrankedJpaIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueCategoryApplicationService categoryService;

  @Autowired
  private CatalogueCategoryRepository categories;

  @Test
  void unrankedCreateMoveAndPersistenceReadsUseFallbackOrderWithoutSortRank() {
    var beta = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Beta", "beta", true)
    );
    var alpha = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Alpha", "alpha", true)
    );

    assertThat(sortRank(beta.id())).isNull();
    assertThat(sortRank(alpha.id())).isNull();
    assertThat(fallbackOrderedRootSlugs()).containsExactly("alpha", "beta");
    assertThat(fallbackOrderedTreeSortRanks()).containsOnlyNulls();

    categoryService.move(alpha.id(), beta.id(), SparseOrderTarget.append());

    CatalogueCategory moved = categories.findById(alpha.id()).orElseThrow();
    assertThat(moved.parentId()).isEqualTo(beta.id());
    assertThat(sortRank(alpha.id())).isNull();
  }

  @Test
  void unrankedWritesRejectExplicitPlacement() {
    var existing = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Existing", "existing", true)
    );

    assertThatThrownBy(() ->
      categoryService.create(
        new UpsertCatalogueCategoryCommand(
          null,
          null,
          "Before",
          "before",
          true,
          SparseOrderTarget.from(existing.id(), null, null)
        )
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("catalogue.api.category.ranking.disabled.before-id");

    assertThatThrownBy(() ->
      categoryService.move(existing.id(), null, SparseOrderTarget.from(null, existing.id(), null))
    )
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("catalogue.api.category.ranking.disabled.after-id");

    assertThatThrownBy(() ->
      categoryService.reorder(
        existing.id(),
        new ReorderCatalogueCategoryCommand(null, SparseOrderTarget.from(null, null, 0))
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("catalogue.api.category.ranking.disabled.target-index");
  }

  private List<String> fallbackOrderedRootSlugs() {
    return categories
      .findAll(
        CategoryParentScope.roots(),
        AdminCatalogueCategoryFilters.none(),
        new PageCriteria(0, 10),
        new SortCriteria(
          List.of(new SortOrder("name", SortDirection.ASC), new SortOrder("id", SortDirection.ASC))
        )
      )
      .content()
      .stream()
      .map(CatalogueCategory::slug)
      .toList();
  }

  private List<Long> fallbackOrderedTreeSortRanks() {
    return categories
      .findTree(
        new PageCriteria(0, 10),
        new SortCriteria(
          List.of(new SortOrder("name", SortDirection.ASC), new SortOrder("id", SortDirection.ASC))
        )
      )
      .content()
      .stream()
      .map(node -> node.category().sortRank())
      .toList();
  }

  private Long sortRank(Long id) {
    return jdbc.queryForObject(
      "SELECT sort_rank FROM catalogue_category WHERE id = ?",
      Long.class,
      id
    );
  }
}
