package com.ferozfaiz.uniapi.iam.domain.repository;

import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository {
  Permission save(Permission permission);

  Optional<Permission> findById(Long id);

  Optional<Permission> findByCode(String code);

  boolean existsByCode(String code);

  List<Permission> findAll();

  void deleteById(Long id);
}
