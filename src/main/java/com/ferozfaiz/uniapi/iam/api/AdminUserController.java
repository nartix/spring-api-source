package com.ferozfaiz.uniapi.iam.api;

import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthorityLookup;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireAdminUsersRead;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireAdminUsersStatus;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireAdminUsersWrite;
import com.ferozfaiz.uniapi.iam.api.dto.AdminCreateUserRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminListUsersRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminUserMutationRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminUserResponse;
import com.ferozfaiz.uniapi.iam.api.dto.AdminUserStatusRequest;
import com.ferozfaiz.uniapi.iam.api.mapper.AdminUserApiMapper;
import com.ferozfaiz.uniapi.iam.application.AdminUserApplicationService;
import com.ferozfaiz.uniapi.iam.application.query.AdminUserListItem;
import com.ferozfaiz.uniapi.iam.domain.model.User;
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
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("${spring.data.rest.base-path}/admin/users")
public class AdminUserController {

  private final AdminUserApplicationService users;
  private final IdentityAuthorityLookup authorityLookup;

  public AdminUserController(
    AdminUserApplicationService users,
    IdentityAuthorityLookup authorityLookup
  ) {
    this.users = users;
    this.authorityLookup = authorityLookup;
  }

  @PostMapping
  @RequireAdminUsersWrite
  public ResponseEntity<AdminUserResponse> createUser(
    @Valid @RequestBody AdminCreateUserRequest request
  ) {
    User registered = users.create(AdminUserApiMapper.toCommand(request));
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(registered.id())
      .toUri();

    return ResponseEntity.created(location).body(toResponse(registered));
  }

  @GetMapping
  @RequireAdminUsersRead
  @Operation(
    summary = "List admin users",
    description = "Returns a paginated, sorted list of users for admin management."
  )
  @ApiResponses({
    @ApiResponse(
      responseCode = "200",
      description = "Users returned",
      content = @Content(schema = @Schema(implementation = PageResponse.class))
    ),
    @ApiResponse(
      responseCode = "400",
      description = "Invalid pagination or sorting parameters",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
    ),
    @ApiResponse(
      responseCode = "401",
      description = "Authentication is required",
      content = @Content
    ),
    @ApiResponse(
      responseCode = "403",
      description = "User is not allowed to list admin users",
      content = @Content
    ),
  })
  public PageResponse<AdminUserResponse> listUsers(
    @ParameterObject @Valid @ModelAttribute AdminListUsersRequest request
  ) {
    PageResult<AdminUserListItem> page = users.findAll(AdminUserApiMapper.toQuery(request));
    return AdminUserApiMapper.toPageResponse(page, PageLinksAssembler.fromCurrentRequest(page));
  }

  @GetMapping("/{id}")
  @RequireAdminUsersRead
  public AdminUserResponse getUser(@PathVariable Long id) {
    return toResponse(users.findById(id));
  }

  @PutMapping("/{id}")
  @RequireAdminUsersWrite
  public AdminUserResponse updateUser(
    @PathVariable Long id,
    @Valid @RequestBody AdminUserMutationRequest request
  ) {
    return toResponse(users.update(id, AdminUserApiMapper.toCommand(request)));
  }

  @PatchMapping("/{id}/status")
  @RequireAdminUsersStatus
  public AdminUserResponse updateUserStatus(
    @PathVariable Long id,
    @Valid @RequestBody AdminUserStatusRequest request
  ) {
    return toResponse(users.updateStatus(id, AdminUserApiMapper.toCommand(request)));
  }

  @DeleteMapping("/{id}")
  @RequireAdminUsersWrite
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    users.delete(id);
    return ResponseEntity.noContent().build();
  }

  private AdminUserResponse toResponse(User user) {
    return AdminUserApiMapper.toResponse(user, authorityLookup.findAuthorities(user.id()));
  }
}
