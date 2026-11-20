package com.ferozfaiz.uniapi.data.application.query;

import com.ferozfaiz.uniapi.data.domain.model.Category;
import java.util.List;

public record CategoryTree(Category category, List<CategoryTree> children) {
  public CategoryTree {
    children = List.copyOf(children);
  }
}
