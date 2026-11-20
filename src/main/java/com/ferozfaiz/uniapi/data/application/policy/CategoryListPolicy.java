package com.ferozfaiz.uniapi.data.application.policy;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import org.springframework.stereotype.Component;

@Component
public class CategoryListPolicy {

  private final CategoryListPolicyProperties properties;

  public CategoryListPolicy(CategoryListPolicyProperties properties) {
    this.properties = properties;
  }

  public void enforcePage(PageCriteria page) {
    if (!properties.isEnforceMaxPageSize()) {
      return;
    }
    if (page.size() > properties.getMaxPageSize()) {
      throw new InvalidRequestException(
        "data.application.category.list.size.max",
        "size must be at most " + properties.getMaxPageSize(),
        properties.getMaxPageSize()
      );
    }
  }

  public void enforceSort(SortCriteria sort) {
    boolean treeScoped =
      !sort.orders().isEmpty() && "tree".equals(sort.orders().getFirst().property());
    boolean invalidSortRank = sort
      .orders()
      .stream()
      .anyMatch(
        order ->
          "sortRank".equals(order.property()) &&
          (!treeScoped || order.direction() != SortDirection.ASC)
      );
    if (invalidSortRank) {
      throw new InvalidRequestException(
        "data.api.category.list.sort.sort-rank.unsupported",
        "sortRank sorting is only supported for ascending ranked tree category views"
      );
    }
  }
}
