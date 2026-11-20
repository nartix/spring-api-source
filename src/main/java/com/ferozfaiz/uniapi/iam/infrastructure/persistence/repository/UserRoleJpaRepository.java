package com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.UserRoleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, Long> {
  List<UserRoleEntity> findByUserId(Long userId);

  List<UserRoleEntity> findByRoleId(Long roleId);

  boolean existsByUserIdAndRoleId(Long userId, Long roleId);

  void deleteByUserIdAndRoleId(Long userId, Long roleId);
}
