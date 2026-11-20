package com.ferozfaiz.uniapi.iam.application.command;

public record AdminUserStatusCommand(
  Boolean active,
  Boolean accountNonExpired,
  Boolean accountNonLocked,
  Boolean credentialsNonExpired
) {}
