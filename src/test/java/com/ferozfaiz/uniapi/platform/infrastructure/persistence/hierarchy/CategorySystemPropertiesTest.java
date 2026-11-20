package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CategorySystemPropertiesTest {

  @Test
  void buildsRankedDescriptorFromConfiguredDefaults() {
    CategorySystemRegistry registry = new CategorySystemRegistry(
      propertiesWith("data", rankedSystem("data_category", "data_category_closure"))
    );

    CategorySystemDescriptor descriptor = registry.requireDescriptor("data");

    assertThat(descriptor.systemKey()).isEqualTo("data");
    assertThat(descriptor.owningModule()).isEqualTo("data");
    assertThat(descriptor.closureTableDescriptor().nodeTable()).isEqualTo("data_category");
    assertThat(descriptor.ranking().rankingEnabled()).isTrue();
    assertThat(descriptor.ranking().columnName()).isEqualTo("sort_rank");
    assertThat(descriptor.ranking().startingRank()).isEqualTo(1_000_000_000L);
    assertThat(descriptor.ranking().rankStep()).isEqualTo(1_000_000_000L);
    assertThat(descriptor.ranking().maxSyncRebalanceRows()).isEqualTo(1_000L);
    assertThat(descriptor.rankTableDescriptor()).isPresent();
    assertThat(descriptor.rankTableDescriptor().orElseThrow().rankColumn()).isEqualTo("sort_rank");
  }

  @Test
  void buildsUnrankedDescriptorWithoutRankStorage() {
    CategorySystemProperties.SystemProperties system = unrankedSystem(
      "test_unranked_category",
      "test_unranked_category_closure"
    );
    CategorySystemRegistry registry = new CategorySystemRegistry(
      propertiesWith("unranked-test-fixture", system)
    );

    CategorySystemDescriptor descriptor = registry.requireDescriptor("unranked-test-fixture");

    assertThat(descriptor.ranking().rankingEnabled()).isFalse();
    assertThat(descriptor.ranking().placementModes()).containsExactly(PlacementMode.APPEND);
    assertThat(descriptor.ranking().sortRankExposed()).isFalse();
    assertThat(descriptor.ranking().disabledRequestPolicy()).isEqualTo(
      DisabledRankingRequestPolicy.REJECT
    );
    assertThat(descriptor.rankTableDescriptor()).isEmpty();
    assertThat(descriptor.fallbackOrder().orders())
      .extracting(FallbackOrderDescriptor.Order::property)
      .containsExactly("name", "id");
  }

  @Test
  void buildsRankedHiddenDescriptorWithConfiguredFallbackOrderAndPlacementModes() {
    CategorySystemProperties.SystemProperties system = rankedSystem(
      "data_category",
      "data_category_closure"
    );
    system.getRanking().setSortRankExposed(false);
    system.getRanking().setPlacementModes(List.of(PlacementMode.APPEND, PlacementMode.AFTER_ID));
    CategorySystemRegistry registry = new CategorySystemRegistry(propertiesWith("data", system));

    CategorySystemDescriptor descriptor = registry.requireDescriptor("data");

    assertThat(descriptor.ranking().rankingEnabled()).isTrue();
    assertThat(descriptor.ranking().sortRankExposed()).isFalse();
    assertThat(descriptor.ranking().placementModes()).containsExactly(
      PlacementMode.APPEND,
      PlacementMode.AFTER_ID
    );
    assertThat(descriptor.fallbackOrder().orders())
      .extracting(FallbackOrderDescriptor.Order::property)
      .containsExactly("name", "id");
    assertThat(descriptor.rankTableDescriptor()).isPresent();
  }

  @Test
  void rejectsRankedSystemWithoutRankColumn() {
    CategorySystemProperties.SystemProperties system = rankedSystem(
      "data_category",
      "data_category_closure"
    );
    system.getRanking().setColumnName(" ");

    assertThatThrownBy(() -> new CategorySystemRegistry(propertiesWith("data", system)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("columnName");
  }

  @Test
  void rejectsInvalidSparseRankSettings() {
    CategorySystemProperties.SystemProperties system = rankedSystem(
      "data_category",
      "data_category_closure"
    );
    system.getRanking().setRankStep(0);

    assertThatThrownBy(() -> new CategorySystemRegistry(propertiesWith("data", system)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("rankStep");
  }

  @Test
  void normalizesDisabledRankingToAppendOnlyPlacement() {
    CategorySystemProperties.SystemProperties system = unrankedSystem(
      "test_unranked_category",
      "test_unranked_category_closure"
    );
    system.getRanking().setPlacementModes(List.of(PlacementMode.APPEND, PlacementMode.BEFORE_ID));

    CategorySystemRegistry registry = new CategorySystemRegistry(
      propertiesWith("unranked-test-fixture", system)
    );

    assertThat(
      registry.requireDescriptor("unranked-test-fixture").ranking().placementModes()
    ).containsExactly(PlacementMode.APPEND);
  }

  @Test
  void documentsRejectAsOnlySupportedDisabledRequestPolicy() {
    assertThat(DisabledRankingRequestPolicy.values()).containsExactly(
      DisabledRankingRequestPolicy.REJECT
    );
  }

  private static CategorySystemProperties propertiesWith(
    String key,
    CategorySystemProperties.SystemProperties system
  ) {
    CategorySystemProperties properties = new CategorySystemProperties();
    properties.setCategorySystems(Map.of(key, system));
    return properties;
  }

  private static CategorySystemProperties.SystemProperties rankedSystem(
    String nodeTable,
    String closureTable
  ) {
    CategorySystemProperties.SystemProperties system = baseSystem(nodeTable, closureTable);
    system.getRanking().setEnabled(true);
    return system;
  }

  private static CategorySystemProperties.SystemProperties unrankedSystem(
    String nodeTable,
    String closureTable
  ) {
    CategorySystemProperties.SystemProperties system = baseSystem(nodeTable, closureTable);
    system.getRanking().setEnabled(false);
    return system;
  }

  private static CategorySystemProperties.SystemProperties baseSystem(
    String nodeTable,
    String closureTable
  ) {
    CategorySystemProperties.SystemProperties system =
      new CategorySystemProperties.SystemProperties();
    system.setOwningModule("data");
    system.setNodeTable(nodeTable);
    system.setClosureTable(closureTable);
    system.setFallbackOrder(
      List.of(
        fallback("name", "name", FallbackOrderDescriptor.Direction.ASC),
        fallback("id", "id", FallbackOrderDescriptor.Direction.ASC)
      )
    );
    return system;
  }

  private static CategorySystemProperties.FallbackOrderProperties fallback(
    String property,
    String columnName,
    FallbackOrderDescriptor.Direction direction
  ) {
    CategorySystemProperties.FallbackOrderProperties fallback =
      new CategorySystemProperties.FallbackOrderProperties();
    fallback.setProperty(property);
    fallback.setColumnName(columnName);
    fallback.setDirection(direction);
    return fallback;
  }
}
