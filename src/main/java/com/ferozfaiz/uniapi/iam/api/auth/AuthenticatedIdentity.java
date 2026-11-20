package com.ferozfaiz.uniapi.iam.api.auth;

public record AuthenticatedIdentity(
  Long userId,
  String username,
  boolean active,
  boolean accountNonExpired,
  boolean accountNonLocked,
  boolean credentialsNonExpired
) {}
