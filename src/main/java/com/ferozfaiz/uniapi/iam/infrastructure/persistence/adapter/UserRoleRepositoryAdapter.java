package com.ferozfaiz.uniapi.iam.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.iam.domain.model.UserRole;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRoleRepository;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.mapper.IamPersistenceMapper;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository.UserRoleJpaRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class UserRoleRepositoryAdapter implements UserRoleRepository {

  private final UserRoleJpaRepository userRoles;

  public UserRoleRepositoryAdapter(UserRoleJpaRepository userRoles) {
    this.userRoles = userRoles;
  }

  @Override
  @Transactional
  public UserRole save(UserRole userRole) {
    return Objects.requireNonNull(
      IamPersistenceMapper.toDomain(
        Objects.requireNonNull(userRoles.save(IamPersistenceMapper.toEntity(userRole)))
      )
    );
  }

  @Override
  public Optional<UserRole> findById(Long id) {
    return Objects.requireNonNull(userRoles.findById(id).map(IamPersistenceMapper::toDomain));
  }

  @Override
  public List<UserRole> findByUserId(Long userId) {
    return Objects.requireNonNull(
      userRoles.findByUserId(userId).stream().map(IamPersistenceMapper::toDomain).toList()
    );
  }

  @Override
  public List<UserRole> findByRoleId(Long roleId) {
    return Objects.requireNonNull(
      userRoles.findByRoleId(roleId).stream().map(IamPersistenceMapper::toDomain).toList()
    );
  }

  @Override
  public boolean existsByUserIdAndRoleId(Long userId, Long roleId) {
    return userRoles.existsByUserIdAndRoleId(userId, roleId);
  }

  @Override
  @Transactional
  public void deleteByUserIdAndRoleId(Long userId, Long roleId) {
    userRoles.deleteByUserIdAndRoleId(userId, roleId);
  }
}
