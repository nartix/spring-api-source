package com.ferozfaiz.uniapi.iam.application.command;

public record UpdateUserCommand(
  String username,
  String email,
  String firstName,
  String lastName,
  boolean active
) {}
