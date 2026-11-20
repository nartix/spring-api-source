package com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.RolePermissionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionJpaRepository extends JpaRepository<RolePermissionEntity, Long> {
  List<RolePermissionEntity> findByRoleId(Long roleId);

  List<RolePermissionEntity> findByPermissionId(Long permissionId);

  boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

  void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);
}
