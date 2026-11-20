package com.ferozfaiz.uniapi.iam.api;

import com.ferozfaiz.uniapi.iam.api.authorization.RequireAdminUsersWrite;
import com.ferozfaiz.uniapi.iam.api.dto.AdminListPermissionsRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminListRolesRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminPermissionOptionResponse;
import com.ferozfaiz.uniapi.iam.api.dto.AdminRoleOptionResponse;
import com.ferozfaiz.uniapi.iam.api.mapper.AdminAccessOptionApiMapper;
import com.ferozfaiz.uniapi.iam.application.AdminAccessOptionApplicationService;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageLinksAssembler;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${spring.data.rest.base-path}/admin")
public class AdminAccessOptionController {

  private final AdminAccessOptionApplicationService accessOptions;

  public AdminAccessOptionController(AdminAccessOptionApplicationService accessOptions) {
    this.accessOptions = accessOptions;
  }

  @GetMapping("/roles")
  @RequireAdminUsersWrite
  @Operation(
    summary = "List assignable admin user roles",
    description = "Returns a paginated, sorted list of roles available to admin user forms."
  )
  @ApiResponses({
    @ApiResponse(
      responseCode = "200",
      description = "Roles returned",
      content = @Content(schema = @Schema(implementation = PageResponse.class))
    ),
    @ApiResponse(
      responseCode = "400",
      description = "Invalid pagination, filtering, or sorting parameters",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
    ),
    @ApiResponse(
      responseCode = "401",
      description = "Authentication is required",
      content = @Content
    ),
    @ApiResponse(
      responseCode = "403",
      description = "User is not allowed to list assignable roles",
      content = @Content
    ),
  })
  public PageResponse<AdminRoleOptionResponse> listRoles(
    @ParameterObject @Valid @ModelAttribute AdminListRolesRequest request
  ) {
    PageResult<Role> page = accessOptions.findRoles(AdminAccessOptionApiMapper.toQuery(request));
    return AdminAccessOptionApiMapper.toRolePageResponse(
      page,
      PageLinksAssembler.fromCurrentRequest(page)
    );
  }

  @GetMapping("/permissions")
  @RequireAdminUsersWrite
  @Operation(
    summary = "List assignable admin user permissions",
    description = "Returns a paginated, filtered, and sorted list of permissions available to admin user forms."
  )
  @ApiResponses({
    @ApiResponse(
      responseCode = "200",
      description = "Permissions returned",
      content = @Content(schema = @Schema(implementation = PageResponse.class))
    ),
    @ApiResponse(
      responseCode = "400",
      description = "Invalid pagination, filtering, or sorting parameters",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
    ),
    @ApiResponse(
      responseCode = "401",
      description = "Authentication is required",
      content = @Content
    ),
    @ApiResponse(
      responseCode = "403",
      description = "User is not allowed to list assignable permissions",
      content = @Content
    ),
  })
  public PageResponse<AdminPermissionOptionResponse> listPermissions(
    @ParameterObject @Valid @ModelAttribute AdminListPermissionsRequest request
  ) {
    PageResult<Permission> page = accessOptions.findPermissions(
      AdminAccessOptionApiMapper.toQuery(request)
    );
    return AdminAccessOptionApiMapper.toPermissionPageResponse(
      page,
      PageLinksAssembler.fromCurrentRequest(page)
    );
  }
}
