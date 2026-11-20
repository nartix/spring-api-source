package com.ferozfaiz.uniapi.iam.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.iam.domain.model.RolePermission;
import com.ferozfaiz.uniapi.iam.domain.repository.RolePermissionRepository;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.mapper.IamPersistenceMapper;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository.RolePermissionJpaRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class RolePermissionRepositoryAdapter implements RolePermissionRepository {

  private final RolePermissionJpaRepository rolePermissions;

  public RolePermissionRepositoryAdapter(RolePermissionJpaRepository rolePermissions) {
    this.rolePermissions = rolePermissions;
  }

  @Override
  @Transactional
  public RolePermission save(RolePermission rolePermission) {
    return Objects.requireNonNull(
      IamPersistenceMapper.toDomain(
        Objects.requireNonNull(rolePermissions.save(IamPersistenceMapper.toEntity(rolePermission)))
      )
    );
  }

  @Override
  public Optional<RolePermission> findById(Long id) {
    return Objects.requireNonNull(rolePermissions.findById(id).map(IamPersistenceMapper::toDomain));
  }

  @Override
  public List<RolePermission> findByRoleId(Long roleId) {
    return Objects.requireNonNull(
      rolePermissions.findByRoleId(roleId).stream().map(IamPersistenceMapper::toDomain).toList()
    );
  }

  @Override
  public List<RolePermission> findByPermissionId(Long permissionId) {
    return Objects.requireNonNull(
      rolePermissions
        .findByPermissionId(permissionId)
        .stream()
        .map(IamPersistenceMapper::toDomain)
        .toList()
    );
  }

  @Override
  public boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId) {
    return rolePermissions.existsByRoleIdAndPermissionId(roleId, permissionId);
  }

  @Override
  @Transactional
  public void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId) {
    rolePermissions.deleteByRoleIdAndPermissionId(roleId, permissionId);
  }
}
