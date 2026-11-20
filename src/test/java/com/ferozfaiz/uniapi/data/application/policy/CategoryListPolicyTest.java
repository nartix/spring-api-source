package com.ferozfaiz.uniapi.data.application.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryListPolicyTest {

  private final CategoryListPolicy policy = new CategoryListPolicy(
    new CategoryListPolicyProperties()
  );

  @Test
  void allowsAscendingSortRankOnlyWhenTreeScoped() {
    assertThatCode(() ->
      policy.enforceSort(
        new SortCriteria(
          List.of(
            new SortOrder("tree", SortDirection.ASC),
            new SortOrder("sortRank", SortDirection.ASC)
          )
        )
      )
    ).doesNotThrowAnyException();
  }

  @Test
  void rejectsDescendingOrMixedParentSortRank() {
    assertThatThrownBy(() -> policy.enforceSort(SortCriteria.of("sortRank", SortDirection.ASC)))
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("data.api.category.list.sort.sort-rank.unsupported");

    assertThatThrownBy(() ->
      policy.enforceSort(
        new SortCriteria(
          List.of(
            new SortOrder("tree", SortDirection.ASC),
            new SortOrder("sortRank", SortDirection.DESC)
          )
        )
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("data.api.category.list.sort.sort-rank.unsupported");
  }
}
