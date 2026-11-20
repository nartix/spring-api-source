package com.ferozfaiz.uniapi.platform.security.authentication;

import com.ferozfaiz.uniapi.iam.api.auth.SessionPrincipal;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record AuthenticatedPrincipal(
  Long userId,
  String username,
  Set<String> roles,
  Set<String> permissions
) implements SessionPrincipal, Serializable {
  private static final long serialVersionUID = 1L;

  public AuthenticatedPrincipal {
    roles = unmodifiableCopy(roles, "roles");
    permissions = unmodifiableCopy(permissions, "permissions");
  }

  @Override
  public String getName() {
    return username;
  }

  private static Set<String> unmodifiableCopy(Set<String> values, String name) {
    Objects.requireNonNull(values, name + " must not be null");
    return Collections.unmodifiableSet(new LinkedHashSet<>(values));
  }
}
