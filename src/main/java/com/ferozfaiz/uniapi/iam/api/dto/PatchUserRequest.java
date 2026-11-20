package com.ferozfaiz.uniapi.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatchUserRequest(
  @Pattern(regexp = ".*\\S.*", message = "{iam.api.user.username.required}")
  @Size(max = 100, message = "{iam.api.user.username.size}")
  String username,
  @Pattern(regexp = ".*\\S.*", message = "{iam.api.user.email.required}")
  @Email(message = "{iam.api.user.email.valid}")
  @Size(max = 320, message = "{iam.api.user.email.size}")
  String email,
  @Size(max = 100, message = "{iam.api.user.first-name.size}") String firstName,
  @Size(max = 100, message = "{iam.api.user.last-name.size}") String lastName,
  Boolean active
) {}
