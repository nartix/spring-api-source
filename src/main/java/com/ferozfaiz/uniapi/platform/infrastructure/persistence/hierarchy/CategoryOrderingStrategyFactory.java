package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CategoryOrderingStrategyFactory {

  private final Map<String, CategoryOrderingStrategy> strategies;

  public CategoryOrderingStrategyFactory(List<CategoryOrderingStrategy> strategies) {
    Map<String, CategoryOrderingStrategy> mapped = new LinkedHashMap<>();
    for (CategoryOrderingStrategy strategy : strategies) {
      CategoryOrderingStrategy previous = mapped.putIfAbsent(strategy.systemKey(), strategy);
      if (previous != null) {
        throw new IllegalArgumentException(
          "Duplicate category ordering strategy for system: " + strategy.systemKey()
        );
      }
    }
    this.strategies = Map.copyOf(mapped);
  }

  public CategoryOrderingStrategy requireStrategy(String systemKey) {
    String key = SqlIdentifiers.requireText(systemKey, "systemKey");
    CategoryOrderingStrategy strategy = strategies.get(key);
    if (strategy == null) {
      throw new IllegalArgumentException("Unknown category ordering strategy: " + key);
    }
    return strategy;
  }
}
