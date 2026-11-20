package com.ferozfaiz.uniapi.iam.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.iam.domain.model.UserPermission;
import com.ferozfaiz.uniapi.iam.domain.repository.UserPermissionRepository;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.mapper.IamPersistenceMapper;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository.UserPermissionJpaRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class UserPermissionRepositoryAdapter implements UserPermissionRepository {

  private final UserPermissionJpaRepository userPermissions;

  public UserPermissionRepositoryAdapter(UserPermissionJpaRepository userPermissions) {
    this.userPermissions = userPermissions;
  }

  @Override
  @Transactional
  public UserPermission save(UserPermission userPermission) {
    return Objects.requireNonNull(
      IamPersistenceMapper.toDomain(
        Objects.requireNonNull(userPermissions.save(IamPersistenceMapper.toEntity(userPermission)))
      )
    );
  }

  @Override
  public Optional<UserPermission> findById(Long id) {
    return Objects.requireNonNull(userPermissions.findById(id).map(IamPersistenceMapper::toDomain));
  }

  @Override
  public List<UserPermission> findByUserId(Long userId) {
    return Objects.requireNonNull(
      userPermissions.findByUserId(userId).stream().map(IamPersistenceMapper::toDomain).toList()
    );
  }

  @Override
  public List<UserPermission> findByPermissionId(Long permissionId) {
    return Objects.requireNonNull(
      userPermissions
        .findByPermissionId(permissionId)
        .stream()
        .map(IamPersistenceMapper::toDomain)
        .toList()
    );
  }

  @Override
  public boolean existsByUserIdAndPermissionId(Long userId, Long permissionId) {
    return userPermissions.existsByUserIdAndPermissionId(userId, permissionId);
  }

  @Override
  @Transactional
  public void deleteByUserIdAndPermissionId(Long userId, Long permissionId) {
    userPermissions.deleteByUserIdAndPermissionId(userId, permissionId);
  }
}
