package com.ferozfaiz.uniapi.iam.domain.repository;

import com.ferozfaiz.uniapi.iam.domain.model.Role;
import java.util.List;
import java.util.Optional;

public interface RoleRepository {
  Role save(Role role);

  Optional<Role> findById(Long id);

  Optional<Role> findByCode(String code);

  boolean existsByCode(String code);

  List<Role> findAll();

  void deleteById(Long id);
}
