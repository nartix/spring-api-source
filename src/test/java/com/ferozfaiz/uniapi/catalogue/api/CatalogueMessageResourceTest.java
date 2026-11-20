package com.ferozfaiz.uniapi.catalogue.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class CatalogueMessageResourceTest {

  private static final List<String> FEATURE_KEYS = List.of(
    "catalogue.api.category.placement.unsupported",
    "catalogue.api.category.placement.target.invalid"
  );

  @Test
  void featureMessageKeysResolveInDefaultAndFrenchBundles() {
    ResourceBundle defaults = ResourceBundle.getBundle("messages.catalogue", Locale.ENGLISH);
    ResourceBundle french = ResourceBundle.getBundle("messages.catalogue", Locale.FRENCH);

    assertThat(FEATURE_KEYS).allSatisfy(key -> {
      assertThat(defaults.containsKey(key))
        .as(key + " default")
        .isTrue();
      assertThat(french.containsKey(key))
        .as(key + " fr")
        .isTrue();
      assertThat(defaults.getString(key)).isNotBlank();
      assertThat(french.getString(key)).isNotBlank();
    });
    assertThat(defaults.getString("catalogue.api.category.placement.unsupported")).isEqualTo(
      "placement mode {0} is not supported for catalogue categories"
    );
  }
}
