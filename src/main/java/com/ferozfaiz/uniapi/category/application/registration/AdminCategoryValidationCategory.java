package com.ferozfaiz.uniapi.category.application.registration;

public enum AdminCategoryValidationCategory {
  INVALID_SYSTEM_KEY("invalid-system-key"),
  INVALID_OWNING_MODULE("invalid-owning-module"),
  INVALID_IDENTIFIER("invalid-identifier"),
  INVALID_PROFILE("invalid-profile"),
  INVALID_OPERATION_DECLARATION("invalid-operation-declaration"),
  INVALID_HIERARCHY("invalid-hierarchy"),
  INVALID_ORDERING_RANKING("invalid-ordering-ranking"),
  INVALID_LIFECYCLE("invalid-lifecycle"),
  INVALID_LOGICAL_REFERENCE("invalid-logical-reference"),
  INVALID_LIMIT("invalid-limit"),
  INVALID_PERMISSION_MAPPING("invalid-permission-mapping"),
  INVALID_POLICY_REFERENCE("invalid-policy-reference"),
  INVALID_COLLECTION("invalid-collection"),
  INVALID_COMPONENT_SLOT("invalid-component-slot"),
  DUPLICATE_SYSTEM_KEY("duplicate-system-key"),
  DUPLICATE_RUNTIME_PORT_OWNERSHIP("duplicate-runtime-port-ownership"),
  MISSING_COMPONENT("missing-component"),
  PROFILE_CODEC_INCOMPATIBILITY("profile-codec-incompatibility"),
  MISSING_COLLABORATOR("missing-collaborator"),
  UNEXPECTED_COLLABORATOR("unexpected-collaborator");

  private final String code;

  AdminCategoryValidationCategory(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
