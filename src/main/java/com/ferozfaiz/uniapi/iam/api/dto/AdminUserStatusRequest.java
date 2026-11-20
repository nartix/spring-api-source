package com.ferozfaiz.uniapi.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

@Schema(description = "Account status fields that can be changed by limited admin users")
public record AdminUserStatusRequest(
  @Schema(description = "Whether the user account is enabled", example = "true") Boolean active,
  @Schema(description = "Whether the account has not expired", example = "true")
  Boolean accountNonExpired,
  @Schema(description = "Whether the account is not locked", example = "true")
  Boolean accountNonLocked,
  @Schema(description = "Whether the credentials have not expired", example = "true")
  Boolean credentialsNonExpired
) {
  @AssertTrue(message = "{iam.api.admin-user.status.required}")
  @Schema(hidden = true)
  public boolean isStatusChangePresent() {
    return (
      active != null ||
      accountNonExpired != null ||
      accountNonLocked != null ||
      credentialsNonExpired != null
    );
  }
}
