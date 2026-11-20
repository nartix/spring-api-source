package com.ferozfaiz.uniapi.category.application.registration;

public enum AdminCategoryValidationComponent {
  DEFINITION("definition"),
  IDENTIFIER_CODEC("identifier-codec"),
  PERMISSION_MAPPING("permission-mapping"),
  POLICY_REFERENCES("policy-references"),
  RUNTIME_BINDING("runtime-binding"),
  RUNTIME_PORTS("runtime-ports"),
  RUNTIME_POLICY_BINDINGS("runtime-policy-bindings"),
  REGISTRATION_SOURCE("registration-source"),
  REGISTRATION("registration"),
  REGISTRY("registry");

  private final String code;

  AdminCategoryValidationComponent(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
