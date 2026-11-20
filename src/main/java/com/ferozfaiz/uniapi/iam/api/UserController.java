package com.ferozfaiz.uniapi.iam.api;

import com.ferozfaiz.uniapi.iam.api.authorization.RequireUsersRead;
import com.ferozfaiz.uniapi.iam.api.authorization.RequireUsersWrite;
import com.ferozfaiz.uniapi.iam.api.dto.CreateUserRequest;
import com.ferozfaiz.uniapi.iam.api.dto.ListUsersRequest;
import com.ferozfaiz.uniapi.iam.api.dto.PatchUserRequest;
import com.ferozfaiz.uniapi.iam.api.dto.UpdateUserRequest;
import com.ferozfaiz.uniapi.iam.api.dto.UserResponse;
import com.ferozfaiz.uniapi.iam.api.mapper.UserApiMapper;
import com.ferozfaiz.uniapi.iam.application.UserApplicationService;
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
@RequestMapping("${spring.data.rest.base-path}/users")
public class UserController {

  private final UserApplicationService users;

  public UserController(UserApplicationService users) {
    this.users = users;
  }

  @PostMapping
  @RequireUsersWrite
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    User registered = users.register(UserApiMapper.toCommand(request));
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(registered.id())
      .toUri();

    return ResponseEntity.created(location).body(UserApiMapper.toResponse(registered));
  }

  @GetMapping
  @RequireUsersRead
  @Operation(summary = "List users", description = "Returns a paginated, sorted list of users.")
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
      description = "User is not allowed to list users",
      content = @Content
    ),
  })
  public PageResponse<UserResponse> listUsers(
    @ParameterObject @Valid @ModelAttribute ListUsersRequest request
  ) {
    PageResult<User> page = users.findAll(UserApiMapper.toQuery(request));
    return UserApiMapper.toPageResponse(page, PageLinksAssembler.fromCurrentRequest(page));
  }

  @GetMapping("/{id}")
  @RequireUsersRead
  public UserResponse getUser(@PathVariable Long id) {
    return UserApiMapper.toResponse(users.findById(id));
  }

  @PutMapping("/{id}")
  @RequireUsersWrite
  public UserResponse updateUser(
    @PathVariable Long id,
    @Valid @RequestBody UpdateUserRequest request
  ) {
    return UserApiMapper.toResponse(users.update(id, UserApiMapper.toCommand(request)));
  }

  @PatchMapping("/{id}")
  @RequireUsersWrite
  public UserResponse patchUser(
    @PathVariable Long id,
    @Valid @RequestBody PatchUserRequest request
  ) {
    return UserApiMapper.toResponse(users.patch(id, UserApiMapper.toCommand(request)));
  }

  @DeleteMapping("/{id}")
  @RequireUsersWrite
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    users.delete(id);
    return ResponseEntity.noContent().build();
  }
}
