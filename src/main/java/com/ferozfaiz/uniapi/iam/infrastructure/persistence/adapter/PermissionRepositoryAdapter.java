package com.ferozfaiz.uniapi.iam.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.repository.PermissionRepository;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.mapper.IamPersistenceMapper;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository.PermissionJpaRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class PermissionRepositoryAdapter implements PermissionRepository {

  private final PermissionJpaRepository permissions;

  public PermissionRepositoryAdapter(PermissionJpaRepository permissions) {
    this.permissions = permissions;
  }

  @Override
  @Transactional
  public Permission save(Permission permission) {
    return Objects.requireNonNull(
      IamPersistenceMapper.toDomain(
        Objects.requireNonNull(permissions.save(IamPersistenceMapper.toEntity(permission)))
      )
    );
  }

  @Override
  public Optional<Permission> findById(Long id) {
    return Objects.requireNonNull(permissions.findById(id).map(IamPersistenceMapper::toDomain));
  }

  @Override
  public Optional<Permission> findByCode(String code) {
    return Objects.requireNonNull(permissions.findByCode(code).map(IamPersistenceMapper::toDomain));
  }

  @Override
  public boolean existsByCode(String code) {
    return permissions.existsByCode(code);
  }

  @Override
  public List<Permission> findAll() {
    return Objects.requireNonNull(
      permissions.findAll().stream().map(IamPersistenceMapper::toDomain).toList()
    );
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    permissions.deleteById(id);
  }
}
