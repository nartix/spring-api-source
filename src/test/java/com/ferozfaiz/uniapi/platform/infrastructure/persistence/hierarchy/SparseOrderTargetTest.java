package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

class SparseOrderTargetTest {

  @Test
  void parsesAppendWhenNoPlacementFieldsArePresent() {
    SparseOrderTarget target = SparseOrderTarget.from(null, null, null);

    assertThat(target.mode()).isEqualTo(PlacementMode.APPEND);
    assertThat(target.hasExplicitPlacement()).isFalse();
  }

  @Test
  void parsesBeforeAfterAndTargetIndexModes() {
    assertThat(SparseOrderTarget.from(10L, null, null).mode()).isEqualTo(PlacementMode.BEFORE_ID);
    assertThat(SparseOrderTarget.from(null, 11L, null).mode()).isEqualTo(PlacementMode.AFTER_ID);
    assertThat(SparseOrderTarget.from(null, null, 2).mode()).isEqualTo(PlacementMode.TARGET_INDEX);
  }

  @Test
  void rejectsConflictingExplicitPlacementFields() {
    assertThatThrownBy(() -> SparseOrderTarget.from(10L, 11L, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("exactly one");
  }

  @Test
  void rejectsNegativeTargetIndex() {
    assertThatThrownBy(() -> SparseOrderTarget.from(null, null, -1))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("targetIndex");
  }

  @Test
  void disabledSystemRejectsBeforeAfterAndTargetIndexPlacementWithStableMessageKey() {
    UnrankedFallbackOrderingStrategy strategy = new UnrankedFallbackOrderingStrategy(
      UnrankedCategoryFixture.DESCRIPTOR
    );

    assertDisabledRanking(strategy, SparseOrderTarget.from(10L, null, null));
    assertDisabledRanking(strategy, SparseOrderTarget.from(null, 11L, null));
    assertDisabledRanking(strategy, SparseOrderTarget.from(null, null, 0));
  }

  private static void assertDisabledRanking(
    UnrankedFallbackOrderingStrategy strategy,
    SparseOrderTarget target
  ) {
    assertThatThrownBy(() -> strategy.validatePlacement(target))
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("platform.infrastructure.category.ranking.disabled");
  }
}
