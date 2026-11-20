package com.ferozfaiz.uniapi.iam.api.auth;

import java.util.Set;

public record AuthenticatedAuthorities(Set<String> roles, Set<String> permissions) {
  public static AuthenticatedAuthorities empty() {
    return new AuthenticatedAuthorities(Set.of(), Set.of());
  }
}
