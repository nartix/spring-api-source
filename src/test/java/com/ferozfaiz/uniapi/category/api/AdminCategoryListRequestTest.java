package com.ferozfaiz.uniapi.category.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryListRequest;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.DisabledRankingRequestPolicy;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.PlacementMode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankingCapabilityDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankTableDescriptor;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminCategoryListRequestTest {

  @Test
  void defaultsAllCategoryListsToUpdatedAtDescendingWithStableIdTieBreaker() {
    var query = new AdminCategoryListRequest(
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null
    ).toQuery(rankedDescriptor(true));

    assertThat(query.parentScope()).isEqualTo(AdminCategoryParentScope.all());
    assertThat(query.page().page()).isEqualTo(0);
    assertThat(query.page().size()).isEqualTo(20);
    assertThat(query.sort().orders())
      .extracting(order -> order.property() + ":" + order.direction())
      .containsExactly("updatedAt:DESC", "id:ASC");
  }

  @Test
  void acceptsAscendingSortRankOnlyForRankedExposedSiblingScopes() {
    var query = new AdminCategoryListRequest(
      null,
      null,
      "0",
      null,
      null,
      null,
      List.of("sortRank,asc"),
      null
    ).toQuery(rankedDescriptor(true));

    assertThat(query.parentScope()).isEqualTo(AdminCategoryParentScope.roots());
    assertThat(query.sort().orders().getFirst().property()).isEqualTo("sortRank");
    assertThat(query.sort().orders().getFirst().direction()).isEqualTo(SortDirection.ASC);

    assertThatThrownBy(() ->
      new AdminCategoryListRequest(
        null,
        null,
        null,
        null,
        null,
        null,
        List.of("sortRank,asc"),
        null
      ).toQuery(rankedDescriptor(true))
    )
      .isInstanceOf(InvalidRequestException.class)
      .hasMessageContaining("sortRank sorting is only supported");
  }

  @Test
  void hiddenRankSiblingDefaultUsesDescriptorFallbackOrder() {
    var query = new AdminCategoryListRequest(null, null, "0", null, null, null, null, null).toQuery(
      rankedDescriptor(false)
    );

    assertThat(query.sort().orders())
      .extracting(order -> order.property() + ":" + order.direction())
      .containsExactly("name:ASC", "id:ASC");
  }

  @Test
  void acceptsExactPageSizeAndSearchMaximaAndRejectsTheNextValue() {
    String maximumSearch = "q".repeat(120);
    var query = new AdminCategoryListRequest(
      0,
      1_000,
      null,
      null,
      maximumSearch,
      null,
      null,
      null
    ).toQuery(rankedDescriptor(true));

    assertThat(query.page().page()).isZero();
    assertThat(query.page().size()).isEqualTo(1_000);
    assertThat(query.filters().q()).isEqualTo(maximumSearch);

    assertThatThrownBy(() ->
      new AdminCategoryListRequest(0, 1_001, null, null, null, null, null, null).toQuery(
        rankedDescriptor(true)
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.list.size.max");
    assertThatThrownBy(() ->
      new AdminCategoryListRequest(0, 20, null, null, "q".repeat(121), null, null, null).toQuery(
        rankedDescriptor(true)
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.list.search.size");
  }

  @Test
  void preservesFourSimultaneousListConstraints() {
    var query = new AdminCategoryListRequest(
      null,
      null,
      "42",
      false,
      "  Components  ",
      "graphics-cards",
      null,
      null
    ).toQuery(rankedDescriptor(true));

    assertThat(query.parentScope()).isEqualTo(AdminCategoryParentScope.children(42L));
    assertThat(query.filters().active()).isFalse();
    assertThat(query.filters().q()).isEqualTo("Components");
    assertThat(query.filters().slug()).isEqualTo("graphics-cards");
  }

  @Test
  void acceptsFivePrimarySortsWithStableIdTieBreakerAndRejectsSix() {
    List<String> fiveSorts = List.of(
      "name,asc",
      "slug,desc",
      "active,asc",
      "createdAt,desc",
      "updatedAt,asc"
    );
    var query = new AdminCategoryListRequest(
      null,
      null,
      null,
      null,
      null,
      null,
      fiveSorts,
      null
    ).toQuery(rankedDescriptor(true));

    assertThat(query.sort().orders())
      .extracting(order -> order.property() + ":" + order.direction())
      .containsExactly(
        "name:ASC",
        "slug:DESC",
        "active:ASC",
        "createdAt:DESC",
        "updatedAt:ASC",
        "id:ASC"
      );

    assertThatThrownBy(() ->
      new AdminCategoryListRequest(
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(
          "name,asc",
          "slug,desc",
          "active,asc",
          "createdAt,desc",
          "updatedAt,asc",
          "parentId,asc"
        ),
        null
      ).toQuery(rankedDescriptor(true))
    )
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.list.sort.max");
  }

  private static CategorySystemDescriptor rankedDescriptor(boolean sortRankExposed) {
    var ranking = RankingCapabilityDescriptor.ranked(
      "sort_rank",
      1_000_000_000L,
      1_000_000_000L,
      1_000,
      DisabledRankingRequestPolicy.REJECT,
      List.of(
        PlacementMode.APPEND,
        PlacementMode.BEFORE_ID,
        PlacementMode.AFTER_ID,
        PlacementMode.TARGET_INDEX
      ),
      sortRankExposed
    );
    return new CategorySystemDescriptor(
      "catalogue",
      "catalogue",
      ClosureTableDescriptor.of("catalogue_category", "catalogue_category_closure"),
      ranking,
      new FallbackOrderDescriptor(
        List.of(
          new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
          new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
        )
      ),
      Optional.of(
        new SparseRankTableDescriptor("catalogue_category", "id", "parent_id", "sort_rank")
      )
    );
  }
}
