package com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.RoleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {
  Optional<RoleEntity> findByCode(String code);

  boolean existsByCode(String code);
}
