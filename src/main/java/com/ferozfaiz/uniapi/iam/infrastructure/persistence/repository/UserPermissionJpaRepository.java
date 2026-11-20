package com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.UserPermissionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionJpaRepository extends JpaRepository<UserPermissionEntity, Long> {
  List<UserPermissionEntity> findByUserId(Long userId);

  List<UserPermissionEntity> findByPermissionId(Long permissionId);

  boolean existsByUserIdAndPermissionId(Long userId, Long permissionId);

  void deleteByUserIdAndPermissionId(Long userId, Long permissionId);
}
