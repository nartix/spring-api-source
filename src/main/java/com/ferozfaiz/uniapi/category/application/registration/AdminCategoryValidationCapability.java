package com.ferozfaiz.uniapi.category.application.registration;

public enum AdminCategoryValidationCapability {
  CAPABILITIES("capabilities"),
  LIST("list"),
  TREE("tree"),
  PARENT_OPTIONS("parent-options"),
  GET("get"),
  BREADCRUMB("breadcrumb"),
  CREATE("create"),
  REPLACE("replace"),
  PATCH("patch"),
  DELETE("delete"),
  MOVE("move"),
  REORDER("reorder"),
  ACTIVATE("activate"),
  DEACTIVATE("deactivate"),
  SEARCH("search"),
  FILTER("filter"),
  SORT("sort"),
  HIERARCHY("hierarchy"),
  ORDERING("ordering"),
  RANKING("ranking"),
  LIFECYCLE("lifecycle");

  private final String code;

  AdminCategoryValidationCapability(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
