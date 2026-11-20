package com.ferozfaiz.uniapi.category.application.registration;

public enum AdminCategoryPolicyObligation {
  GENERAL_VALIDATION("general-validation"),
  QUERY("query"),
  HIERARCHY("hierarchy"),
  MUTATION_VALIDATION("mutation-validation"),
  ORDERING("ordering"),
  DELETION("deletion"),
  LIFECYCLE("lifecycle");

  private final String code;

  AdminCategoryPolicyObligation(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
