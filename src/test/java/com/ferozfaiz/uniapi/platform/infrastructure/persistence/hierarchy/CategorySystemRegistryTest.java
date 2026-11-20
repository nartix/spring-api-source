package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CategorySystemRegistryTest {

  @Test
  void exposesConfiguredDescriptorsBySystemKey() {
    CategorySystemRegistry registry = new CategorySystemRegistry(
      properties(
        Map.of(
          "data",
          system("data", "data_category", "data_category_closure", true),
          "catalogue",
          system("catalogue", "catalogue_category", "catalogue_category_closure", true),
          "unranked-test-fixture",
          system("test", "test_unranked_category", "test_unranked_category_closure", false)
        )
      )
    );

    assertThat(registry.descriptors()).hasSize(3);
    assertThat(registry.requireDescriptor("data").ranking().rankingEnabled()).isTrue();
    assertThat(registry.requireDescriptor("catalogue").rankTableDescriptor()).isPresent();
    assertThat(registry.requireDescriptor("unranked-test-fixture").rankTableDescriptor()).isEmpty();
  }

  @Test
  void supportsFutureRankedAndUnrankedCategorySystems() {
    CategorySystemRegistry registry = new CategorySystemRegistry(
      properties(
        Map.of(
          "future-ranked",
          system("future", "future_category", "future_category_closure", true),
          "future-unranked",
          system("future", "future_unranked_category", "future_unranked_category_closure", false)
        )
      )
    );

    CategorySystemDescriptor ranked = registry.requireDescriptor("future-ranked");
    CategorySystemDescriptor unranked = registry.requireDescriptor("future-unranked");

    assertThat(ranked.owningModule()).isEqualTo("future");
    assertThat(ranked.ranking().rankingEnabled()).isTrue();
    assertThat(ranked.rankTableDescriptor()).isPresent();
    assertThat(unranked.owningModule()).isEqualTo("future");
    assertThat(unranked.ranking().rankingEnabled()).isFalse();
    assertThat(unranked.rankTableDescriptor()).isEmpty();
  }

  @Test
  void failsFastWhenDescriptorStorageDoesNotMatchRankingCapability() {
    RankingCapabilityDescriptor ranking = RankingCapabilityDescriptor.ranked(
      "sort_rank",
      1_000_000_000L,
      1_000_000_000L,
      1_000,
      DisabledRankingRequestPolicy.REJECT,
      List.of(PlacementMode.APPEND),
      true
    );

    assertThatThrownBy(() ->
      new CategorySystemDescriptor(
        "data",
        "data",
        ClosureTableDescriptor.of("data_category", "data_category_closure"),
        ranking,
        fallbackOrder(),
        java.util.Optional.empty()
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("rankTableDescriptor");
  }

  @Test
  void rejectsDuplicateOrBlankSystemKeys() {
    CategorySystemRegistry registry = new CategorySystemRegistry(
      properties(Map.of("data", system("data", "data_category", "data_category_closure", true)))
    );

    assertThatThrownBy(() -> registry.requireDescriptor("missing"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unknown category system");
    assertThatThrownBy(() -> registry.requireDescriptor(" "))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("systemKey");
  }

  private static CategorySystemProperties properties(
    Map<String, CategorySystemProperties.SystemProperties> systems
  ) {
    CategorySystemProperties properties = new CategorySystemProperties();
    properties.setCategorySystems(systems);
    return properties;
  }

  private static CategorySystemProperties.SystemProperties system(
    String owningModule,
    String nodeTable,
    String closureTable,
    boolean ranked
  ) {
    CategorySystemProperties.SystemProperties system =
      new CategorySystemProperties.SystemProperties();
    system.setOwningModule(owningModule);
    system.setNodeTable(nodeTable);
    system.setClosureTable(closureTable);
    system.getRanking().setEnabled(ranked);
    system.setFallbackOrder(
      List.of(
        fallback("name", "name", FallbackOrderDescriptor.Direction.ASC),
        fallback("id", "id", FallbackOrderDescriptor.Direction.ASC)
      )
    );
    return system;
  }

  private static FallbackOrderDescriptor fallbackOrder() {
    return new FallbackOrderDescriptor(
      List.of(
        new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
        new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
      )
    );
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
