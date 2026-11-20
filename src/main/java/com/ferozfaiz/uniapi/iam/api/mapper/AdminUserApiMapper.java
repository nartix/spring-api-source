package com.ferozfaiz.uniapi.iam.api.mapper;

import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedAuthorities;
import com.ferozfaiz.uniapi.iam.api.dto.AdminCreateUserRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminListUsersRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminUserMutationRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminUserResponse;
import com.ferozfaiz.uniapi.iam.api.dto.AdminUserStatusRequest;
import com.ferozfaiz.uniapi.iam.application.command.AdminCreateUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.AdminUserMutationCommand;
import com.ferozfaiz.uniapi.iam.application.command.AdminUserStatusCommand;
import com.ferozfaiz.uniapi.iam.application.query.AdminListUsersQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminUserListItem;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.shared.pagination.PageLinks;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.Collection;
import java.util.List;

public final class AdminUserApiMapper {

  private AdminUserApiMapper() {}

  public static AdminCreateUserCommand toCommand(AdminCreateUserRequest request) {
    return new AdminCreateUserCommand(
      request.username(),
      request.email(),
      request.password(),
      request.firstName(),
      request.lastName(),
      request.active(),
      request.roles(),
      request.permissions()
    );
  }

  public static AdminUserMutationCommand toCommand(AdminUserMutationRequest request) {
    return new AdminUserMutationCommand(
      request.username(),
      request.email(),
      request.firstName(),
      request.lastName(),
      request.active(),
      request.roles(),
      request.permissions()
    );
  }

  public static AdminUserStatusCommand toCommand(AdminUserStatusRequest request) {
    return new AdminUserStatusCommand(
      request.active(),
      request.accountNonExpired(),
      request.accountNonLocked(),
      request.credentialsNonExpired()
    );
  }

  public static AdminListUsersQuery toQuery(AdminListUsersRequest request) {
    return request.toQuery();
  }

  public static AdminUserResponse toResponse(User user, AuthenticatedAuthorities authorities) {
    return new AdminUserResponse(
      String.valueOf(user.id()),
      user.username(),
      user.email(),
      user.firstName(),
      user.lastName(),
      user.active(),
      user.accountNonExpired(),
      user.accountNonLocked(),
      user.credentialsNonExpired(),
      user.lastLoginAt(),
      user.lastActivityAt(),
      sorted(authorities.roles()),
      sorted(authorities.permissions()),
      user.createdAt(),
      user.updatedAt(),
      user.createdBy(),
      user.updatedBy()
    );
  }

  public static AdminUserResponse toResponse(AdminUserListItem user) {
    return new AdminUserResponse(
      String.valueOf(user.id()),
      user.username(),
      user.email(),
      user.firstName(),
      user.lastName(),
      user.active(),
      user.accountNonExpired(),
      user.accountNonLocked(),
      user.credentialsNonExpired(),
      user.lastLoginAt(),
      user.lastActivityAt(),
      sorted(user.roles()),
      sorted(user.permissions()),
      user.createdAt(),
      user.updatedAt(),
      user.createdBy(),
      user.updatedBy()
    );
  }

  public static PageResponse<AdminUserResponse> toPageResponse(
    PageResult<AdminUserListItem> page,
    PageLinks links
  ) {
    return PageResponse.from(page, AdminUserApiMapper::toResponse, links);
  }

  private static List<String> sorted(Collection<String> values) {
    return values.stream().sorted().toList();
  }
}
