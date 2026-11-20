package com.ferozfaiz.uniapi.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CatalogueCategoryBreadcrumbItemTest {

  @Test
  void acceptsCategoryAndNonNegativeDepth() {
    CatalogueCategory category = category();

    CatalogueCategoryBreadcrumbItem item = new CatalogueCategoryBreadcrumbItem(category, 0);

    assertThat(item.category()).isEqualTo(category);
    assertThat(item.depth()).isZero();
  }

  @Test
  void rejectsNullCategory() {
    assertThatThrownBy(() -> new CatalogueCategoryBreadcrumbItem(null, 0))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("category must not be null");
  }

  @Test
  void rejectsNegativeDepth() {
    assertThatThrownBy(() -> new CatalogueCategoryBreadcrumbItem(category(), -1))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("depth must be zero or greater");
  }

  private static CatalogueCategory category() {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueCategory(
      1L,
      null,
      1_000_000_000L,
      "Components",
      "components",
      true,
      now,
      now
    );
  }
}
