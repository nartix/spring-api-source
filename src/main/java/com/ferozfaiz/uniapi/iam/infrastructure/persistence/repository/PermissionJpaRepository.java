package com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.PermissionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, Long> {
  Optional<PermissionEntity> findByCode(String code);

  boolean existsByCode(String code);
}
