package com.ferozfaiz.uniapi.iam.domain.repository;

import com.ferozfaiz.uniapi.iam.domain.model.UserPermission;
import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository {
  UserPermission save(UserPermission userPermission);

  Optional<UserPermission> findById(Long id);

  List<UserPermission> findByUserId(Long userId);

  List<UserPermission> findByPermissionId(Long permissionId);

  boolean existsByUserIdAndPermissionId(Long userId, Long permissionId);

  void deleteByUserIdAndPermissionId(Long userId, Long permissionId);
}
