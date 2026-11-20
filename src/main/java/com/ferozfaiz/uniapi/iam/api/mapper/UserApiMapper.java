package com.ferozfaiz.uniapi.iam.api.mapper;

import com.ferozfaiz.uniapi.iam.api.dto.CreateUserRequest;
import com.ferozfaiz.uniapi.iam.api.dto.ListUsersRequest;
import com.ferozfaiz.uniapi.iam.api.dto.PatchUserRequest;
import com.ferozfaiz.uniapi.iam.api.dto.UpdateUserRequest;
import com.ferozfaiz.uniapi.iam.api.dto.UserResponse;
import com.ferozfaiz.uniapi.iam.application.command.PatchUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.RegisterUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.UpdateUserCommand;
import com.ferozfaiz.uniapi.iam.application.query.ListUsersQuery;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.shared.pagination.PageLinks;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;

public final class UserApiMapper {

  private UserApiMapper() {}

  public static RegisterUserCommand toCommand(CreateUserRequest request) {
    return new RegisterUserCommand(
      request.username(),
      request.email(),
      request.password(),
      request.firstName(),
      request.lastName()
    );
  }

  public static UpdateUserCommand toCommand(UpdateUserRequest request) {
    return new UpdateUserCommand(
      request.username(),
      request.email(),
      request.firstName(),
      request.lastName(),
      request.active()
    );
  }

  public static PatchUserCommand toCommand(PatchUserRequest request) {
    return new PatchUserCommand(
      request.username(),
      request.email(),
      request.firstName(),
      request.lastName(),
      request.active()
    );
  }

  public static ListUsersQuery toQuery(ListUsersRequest request) {
    return new ListUsersQuery(request.toPageCriteria(), request.toSortCriteria());
  }

  public static UserResponse toResponse(User user) {
    return new UserResponse(
      user.id(),
      user.username(),
      user.email(),
      user.firstName(),
      user.lastName(),
      user.active(),
      user.createdAt(),
      user.updatedAt()
    );
  }

  public static PageResponse<UserResponse> toPageResponse(PageResult<User> page, PageLinks links) {
    return PageResponse.from(page, UserApiMapper::toResponse, links);
  }
}
