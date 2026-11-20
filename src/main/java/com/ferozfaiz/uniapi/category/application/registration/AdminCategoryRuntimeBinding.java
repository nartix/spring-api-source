package com.ferozfaiz.uniapi.category.application.registration;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public final class AdminCategoryRuntimeBinding {

  private final AdminCategoryIdentifierCodec<?> identifierCodec;
  private final AdminCategoryPermissionMapping permissionMapping;
  private final AdminCategoryRuntimePolicyBindings policyBindings;
  private final AdminCategoryRuntimePorts ports;

  public AdminCategoryRuntimeBinding(
    AdminCategoryIdentifierCodec<?> identifierCodec,
    AdminCategoryPermissionMapping permissionMapping,
    AdminCategoryRuntimePolicyBindings policyBindings,
    AdminCategoryRuntimePorts ports
  ) {
    this.identifierCodec = Objects.requireNonNull(
      identifierCodec,
      "identifierCodec must not be null"
    );
    this.permissionMapping = Objects.requireNonNull(
      permissionMapping,
      "permissionMapping must not be null"
    );
    this.policyBindings = Objects.requireNonNull(policyBindings, "policyBindings must not be null");
    this.ports = Objects.requireNonNull(ports, "ports must not be null");
  }

  public AdminCategoryIdentifierCodec<?> identifierCodec() {
    return identifierCodec;
  }

  public AdminCategoryPermissionMapping permissionMapping() {
    return permissionMapping;
  }

  public AdminCategoryRuntimePolicyBindings policyBindings() {
    return policyBindings;
  }

  public AdminCategoryRuntimePorts ports() {
    return ports;
  }
}
