package com.ferozfaiz.uniapi.iam.application.command;

public record RegisterUserCommand(
  String username,
  String email,
  String rawPassword,
  String firstName,
  String lastName
) {}
