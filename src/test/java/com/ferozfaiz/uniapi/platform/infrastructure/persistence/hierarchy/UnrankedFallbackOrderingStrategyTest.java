package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class UnrankedFallbackOrderingStrategyTest {

  private final UnrankedFallbackOrderingStrategy strategy = new UnrankedFallbackOrderingStrategy(
    UnrankedCategoryFixture.DESCRIPTOR
  );

  @Test
  void exposesAppendOnlyCapabilityAndFallbackOrder() {
    CategoryCapabilityMetadata metadata = strategy.capabilityMetadata();

    assertThat(metadata.rankingEnabled()).isFalse();
    assertThat(metadata.placementModes()).containsExactly(PlacementMode.APPEND);
    assertThat(metadata.sortRankExposed()).isFalse();
    assertThat(metadata.fallbackOrder())
      .extracting(CategoryCapabilityMetadata.FallbackOrder::property)
      .containsExactly("name", "id");
  }

  @Test
  void acceptsAppendByOmission() {
    assertThatCode(() ->
      strategy.validatePlacement(SparseOrderTarget.append())
    ).doesNotThrowAnyException();
  }

  @Test
  void rejectsExplicitRankPlacementFieldsByDefault() {
    assertThatThrownBy(() -> strategy.validatePlacement(SparseOrderTarget.from(10L, null, null)))
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("platform.infrastructure.category.ranking.disabled");
  }

  @Test
  void resolvesDisabledRankingMessageKeyForDefaultAndFrenchBundles() {
    String messageKey = "platform.infrastructure.category.ranking.disabled";

    assertThat(
      ResourceBundle.getBundle("messages/platform", Locale.ROOT).getString(messageKey)
    ).contains("Ranking is disabled");
    assertThat(
      ResourceBundle.getBundle("messages/platform", Locale.FRENCH).getString(messageKey)
    ).contains("classement");
  }
}
