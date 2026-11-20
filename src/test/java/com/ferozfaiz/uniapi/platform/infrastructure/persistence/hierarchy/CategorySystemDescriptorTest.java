package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CategorySystemDescriptorTest {

  @Test
  void rankedDescriptorsRequireRankStorage() {
    assertThatThrownBy(() ->
      new CategorySystemDescriptor(
        "ranked",
        "test",
        ClosureTableDescriptor.of("ranked_category", "ranked_category_closure"),
        rankedCapability(),
        fallbackOrder(),
        Optional.empty()
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("rankTableDescriptor");
  }

  @Test
  void unrankedDescriptorsRejectRankStorage() {
    assertThatThrownBy(() ->
      new CategorySystemDescriptor(
        "unranked",
        "test",
        ClosureTableDescriptor.of("unranked_category", "unranked_category_closure"),
        RankingCapabilityDescriptor.unranked(DisabledRankingRequestPolicy.REJECT),
        fallbackOrder(),
        Optional.of(
          new SparseRankTableDescriptor("unranked_category", "id", "parent_id", "sort_rank")
        )
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("rankTableDescriptor");
  }

  private static RankingCapabilityDescriptor rankedCapability() {
    return RankingCapabilityDescriptor.ranked(
      "sort_rank",
      1_000_000_000L,
      1_000_000_000L,
      1_000L,
      DisabledRankingRequestPolicy.REJECT,
      List.of(
        PlacementMode.APPEND,
        PlacementMode.BEFORE_ID,
        PlacementMode.AFTER_ID,
        PlacementMode.TARGET_INDEX
      ),
      true
    );
  }

  private static FallbackOrderDescriptor fallbackOrder() {
    return new FallbackOrderDescriptor(
      List.of(
        new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
        new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
      )
    );
  }
}
