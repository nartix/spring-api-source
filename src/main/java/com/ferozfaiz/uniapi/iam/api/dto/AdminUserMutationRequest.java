package com.ferozfaiz.uniapi.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminUserMutationRequest(
  @NotBlank(message = "{iam.api.user.username.required}")
  @Size(max = 100, message = "{iam.api.user.username.size}")
  String username,
  @NotBlank(message = "{iam.api.user.email.required}")
  @Email(message = "{iam.api.user.email.valid}")
  @Size(max = 320, message = "{iam.api.user.email.size}")
  String email,
  @Size(max = 100, message = "{iam.api.user.first-name.size}") String firstName,
  @Size(max = 100, message = "{iam.api.user.last-name.size}") String lastName,
  @NotNull(message = "{iam.api.user.active.required}") Boolean active,
  @Size(max = 50, message = "{iam.api.admin-user.roles.size}") List<String> roles,
  @Size(max = 200, message = "{iam.api.admin-user.permissions.size}") List<String> permissions
) {}
