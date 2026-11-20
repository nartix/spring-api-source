package com.ferozfaiz.uniapi.iam.domain.repository;

import com.ferozfaiz.uniapi.iam.domain.model.UserRole;
import java.util.List;
import java.util.Optional;

public interface UserRoleRepository {
  UserRole save(UserRole userRole);

  Optional<UserRole> findById(Long id);

  List<UserRole> findByUserId(Long userId);

  List<UserRole> findByRoleId(Long roleId);

  boolean existsByUserIdAndRoleId(Long userId, Long roleId);

  void deleteByUserIdAndRoleId(Long userId, Long roleId);
}
