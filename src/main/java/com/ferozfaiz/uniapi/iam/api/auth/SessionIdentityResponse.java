package com.ferozfaiz.uniapi.iam.api.auth;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record SessionIdentityResponse(
  Long id,
  String username,
  Set<String> roles,
  Set<String> permissions,
  SessionScope sessionScope
) {
  public SessionIdentityResponse {
    roles = unmodifiableCopy(roles, "roles");
    permissions = unmodifiableCopy(permissions, "permissions");
    Objects.requireNonNull(sessionScope, "sessionScope must not be null");
  }

  private static Set<String> unmodifiableCopy(Set<String> values, String name) {
    Objects.requireNonNull(values, name + " must not be null");
    return Collections.unmodifiableSet(new LinkedHashSet<>(values));
  }
}
