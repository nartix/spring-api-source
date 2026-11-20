package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class RankingMessageBundleTest {

  @Test
  void dataAndCatalogueRankingMessageKeysResolveInDefaultAndFrenchBundles() {
    assertKeysResolve(
      "messages/data",
      List.of(
        "data.api.category.placement.conflict",
        "data.api.category.placement.target.required",
        "data.api.category.placement.target.invalid",
        "data.api.category.placement.target.self",
        "data.api.category.placement.target-index.bounds",
        "data.api.category.ranking.disabled",
        "data.api.category.ranking.disabled.before-id",
        "data.api.category.ranking.disabled.after-id",
        "data.api.category.ranking.disabled.target-index",
        "data.api.category.list.sort.sort-rank.unsupported",
        "data.domain.category.ranking.gap-exhausted",
        "data.domain.category.ranking.rebalance-limit"
      )
    );
    assertKeysResolve(
      "messages/catalogue",
      List.of(
        "catalogue.api.category.placement.conflict",
        "catalogue.api.category.placement.target.required",
        "catalogue.api.category.placement.target.invalid",
        "catalogue.api.category.placement.target.self",
        "catalogue.api.category.placement.target-index.bounds",
        "catalogue.api.category.ranking.disabled",
        "catalogue.api.category.ranking.disabled.before-id",
        "catalogue.api.category.ranking.disabled.after-id",
        "catalogue.api.category.ranking.disabled.target-index",
        "catalogue.api.list.sort.sort-rank.unsupported",
        "catalogue.domain.category.ranking.gap-exhausted",
        "catalogue.domain.category.ranking.rebalance-limit"
      )
    );
  }

  private static void assertKeysResolve(String bundleName, List<String> keys) {
    ResourceBundle defaultBundle = ResourceBundle.getBundle(bundleName, Locale.ROOT);
    ResourceBundle frenchBundle = ResourceBundle.getBundle(bundleName, Locale.FRENCH);
    for (String key : keys) {
      assertThat(defaultBundle.getString(key)).as("%s default", key).isNotBlank();
      assertThat(frenchBundle.getString(key)).as("%s french", key).isNotBlank();
    }
  }
}
