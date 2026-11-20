package com.ferozfaiz.uniapi.iam.application.command;

public record PatchUserCommand(
  String username,
  String email,
  String firstName,
  String lastName,
  Boolean active
) {}
