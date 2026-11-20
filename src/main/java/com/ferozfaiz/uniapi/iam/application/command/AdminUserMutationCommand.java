package com.ferozfaiz.uniapi.iam.application.command;

import java.util.List;

public record AdminUserMutationCommand(
  String username,
  String email,
  String firstName,
  String lastName,
  boolean active,
  List<String> roles,
  List<String> permissions
) {}
