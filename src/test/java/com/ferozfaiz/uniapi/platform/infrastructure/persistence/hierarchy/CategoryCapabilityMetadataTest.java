package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CategoryCapabilityMetadataTest {

  @Test
  void projectsRankedDescriptorWithoutFallbackOrder() {
    CategoryCapabilityMetadata metadata = CategoryCapabilityMetadata.from(rankedDescriptor(true));

    assertThat(metadata.rankingEnabled()).isTrue();
    assertThat(metadata.placementModes()).containsExactly(
      PlacementMode.APPEND,
      PlacementMode.BEFORE_ID,
      PlacementMode.AFTER_ID,
      PlacementMode.TARGET_INDEX
    );
    assertThat(metadata.sortRankExposed()).isTrue();
    assertThat(metadata.fallbackOrder()).isEmpty();
  }

  @Test
  void projectsRankedHiddenDescriptorWithFallbackOrder() {
    CategoryCapabilityMetadata metadata = CategoryCapabilityMetadata.from(rankedDescriptor(false));

    assertThat(metadata.rankingEnabled()).isTrue();
    assertThat(metadata.placementModes()).containsExactly(
      PlacementMode.APPEND,
      PlacementMode.BEFORE_ID,
      PlacementMode.AFTER_ID,
      PlacementMode.TARGET_INDEX
    );
    assertThat(metadata.sortRankExposed()).isFalse();
    assertThat(metadata.fallbackOrder())
      .extracting(CategoryCapabilityMetadata.FallbackOrder::property)
      .containsExactly("name", "id");
  }

  @Test
  void projectsUnrankedDescriptorWithFallbackOrderAndNoSortRank() {
    CategoryCapabilityMetadata metadata = CategoryCapabilityMetadata.from(
      UnrankedCategoryFixture.DESCRIPTOR
    );

    assertThat(metadata.rankingEnabled()).isFalse();
    assertThat(metadata.placementModes()).containsExactly(PlacementMode.APPEND);
    assertThat(metadata.sortRankExposed()).isFalse();
    assertThat(metadata.fallbackOrder())
      .extracting(CategoryCapabilityMetadata.FallbackOrder::property)
      .containsExactly("name", "id");
  }

  private static CategorySystemDescriptor rankedDescriptor(boolean sortRankExposed) {
    return new CategorySystemDescriptor(
      "ranked-test",
      "test",
      ClosureTableDescriptor.of("ranked_category", "ranked_category_closure"),
      RankingCapabilityDescriptor.ranked(
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
        sortRankExposed
      ),
      new FallbackOrderDescriptor(
        List.of(
          new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
          new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
        )
      ),
      Optional.of(new SparseRankTableDescriptor("ranked_category", "id", "parent_id", "sort_rank"))
    );
  }
}
