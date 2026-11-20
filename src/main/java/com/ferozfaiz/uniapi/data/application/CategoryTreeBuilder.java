package com.ferozfaiz.uniapi.data.application;

import com.ferozfaiz.uniapi.data.application.query.CategoryTree;
import com.ferozfaiz.uniapi.data.domain.model.Category;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class CategoryTreeBuilder {

  private static final Comparator<Category> CATEGORY_ORDER = Comparator.comparing(
    Category::sortRank,
    Comparator.nullsLast(Long::compareTo)
  ).thenComparing(category -> Objects.requireNonNull(category.id()));

  private CategoryTreeBuilder() {}

  static List<CategoryTree> forest(List<Category> categories) {
    Map<Long, List<Category>> childrenByParentId = groupByParentId(categories);
    return categories
      .stream()
      .filter(category -> category.parentId() == null)
      .sorted(CATEGORY_ORDER)
      .map(category -> toTree(category, childrenByParentId))
      .toList();
  }

  static CategoryTree tree(Category root, List<Category> descendants) {
    List<Category> categories = new java.util.ArrayList<>(descendants.size() + 1);
    categories.add(root);
    categories.addAll(descendants);
    return toTree(root, groupByParentId(categories));
  }

  private static CategoryTree toTree(
    Category category,
    Map<Long, List<Category>> childrenByParentId
  ) {
    List<CategoryTree> children = childrenByParentId
      .getOrDefault(category.id(), List.of())
      .stream()
      .sorted(CATEGORY_ORDER)
      .map(child -> toTree(child, childrenByParentId))
      .toList();

    return new CategoryTree(category, children);
  }

  private static Map<Long, List<Category>> groupByParentId(List<Category> categories) {
    return categories
      .stream()
      .filter(category -> category.parentId() != null)
      .collect(Collectors.groupingBy(Category::parentId));
  }
}
