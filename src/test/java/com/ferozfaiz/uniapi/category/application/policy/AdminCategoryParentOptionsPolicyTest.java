package com.ferozfaiz.uniapi.category.application.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AdminCategoryParentOptionsPolicyTest {

  @Test
  void defaultsLimitToTwentyFiveAndAcceptsBoundaries() {
    assertThat(AdminCategoryParentOptionsPolicy.resolveLimit(null)).isEqualTo(25);
    assertThat(AdminCategoryParentOptionsPolicy.resolveLimit(1)).isEqualTo(1);
    assertThat(AdminCategoryParentOptionsPolicy.resolveLimit(50)).isEqualTo(50);
  }

  @Test
  void rejectsOutOfRangeLimits() {
    assertThatThrownBy(() -> AdminCategoryParentOptionsPolicy.resolveLimit(0))
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.parent-options.limit.min");

    assertThatThrownBy(() -> AdminCategoryParentOptionsPolicy.resolveLimit(51))
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.parent-options.limit.max");
  }

  @Test
  void normalizesSearchIncludeIdsExcludeSubtreeAndInactiveFlag() {
    assertThat(AdminCategoryParentOptionsPolicy.normalizeSearch("  Components  ")).isEqualTo(
      "Components"
    );
    assertThat(
      AdminCategoryParentOptionsPolicy.normalizeIncludeIds(List.of("10", "10", "12"))
    ).containsExactly(10L, 12L);
    assertThat(AdminCategoryParentOptionsPolicy.normalizeExcludeSubtreeId("42")).isEqualTo(42L);
    assertThat(AdminCategoryParentOptionsPolicy.resolveIncludeInactive(null)).isTrue();
    assertThat(AdminCategoryParentOptionsPolicy.resolveIncludeInactive("false")).isFalse();
  }

  @Test
  void rejectsInvalidParentOptionShapes() {
    assertThatThrownBy(() -> AdminCategoryParentOptionsPolicy.normalizeIncludeIds(List.of("-1")))
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.parent-options.include-ids.invalid");

    assertThatThrownBy(() -> AdminCategoryParentOptionsPolicy.normalizeExcludeSubtreeId("abc"))
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.parent-options.exclude-subtree-id.invalid");

    assertThatThrownBy(() -> AdminCategoryParentOptionsPolicy.resolveIncludeInactive("sometimes"))
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.parent-options.include-inactive.invalid");
  }

  @Test
  void acceptsExactlyOneHundredTwentyRawSearchCharactersAndRejectsOneMore() {
    String maximumSearch = "q".repeat(120);

    assertThat(AdminCategoryParentOptionsPolicy.normalizeSearch(maximumSearch)).isEqualTo(
      maximumSearch
    );
    assertThatThrownBy(() -> AdminCategoryParentOptionsPolicy.normalizeSearch("q".repeat(121)))
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.parent-options.search.size");
  }

  @Test
  void appliesTheTwentyFiveIncludeIdMaximumBeforeDeduplication() {
    List<String> twentyFiveUniqueIds = IntStream.rangeClosed(1, 25)
      .mapToObj(Integer::toString)
      .toList();

    assertThat(
      AdminCategoryParentOptionsPolicy.normalizeIncludeIds(twentyFiveUniqueIds)
    ).containsExactlyElementsOf(IntStream.rangeClosed(1, 25).mapToObj(Long::valueOf).toList());
    assertThat(
      AdminCategoryParentOptionsPolicy.normalizeIncludeIds(Collections.nCopies(25, "1"))
    ).containsExactly(1L);
    assertThatThrownBy(() ->
      AdminCategoryParentOptionsPolicy.normalizeIncludeIds(Collections.nCopies(26, "1"))
    )
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("category.api.parent-options.include-ids.max");
  }
}
