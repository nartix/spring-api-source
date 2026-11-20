package com.ferozfaiz.uniapi.iam.application.command;

import java.util.List;

public record AdminCreateUserCommand(
  String username,
  String email,
  String rawPassword,
  String firstName,
  String lastName,
  boolean active,
  List<String> roles,
  List<String> permissions
) {}
