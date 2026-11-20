package com.ferozfaiz.uniapi.iam.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.iam.domain.repository.RoleRepository;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.mapper.IamPersistenceMapper;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository.RoleJpaRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class RoleRepositoryAdapter implements RoleRepository {

  private final RoleJpaRepository roles;

  public RoleRepositoryAdapter(RoleJpaRepository roles) {
    this.roles = roles;
  }

  @Override
  @Transactional
  public Role save(Role role) {
    return Objects.requireNonNull(
      IamPersistenceMapper.toDomain(
        Objects.requireNonNull(roles.save(IamPersistenceMapper.toEntity(role)))
      )
    );
  }

  @Override
  public Optional<Role> findById(Long id) {
    return Objects.requireNonNull(roles.findById(id).map(IamPersistenceMapper::toDomain));
  }

  @Override
  public Optional<Role> findByCode(String code) {
    return Objects.requireNonNull(roles.findByCode(code).map(IamPersistenceMapper::toDomain));
  }

  @Override
  public boolean existsByCode(String code) {
    return roles.existsByCode(code);
  }

  @Override
  public List<Role> findAll() {
    return Objects.requireNonNull(
      roles.findAll().stream().map(IamPersistenceMapper::toDomain).toList()
    );
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    roles.deleteById(id);
  }
}
