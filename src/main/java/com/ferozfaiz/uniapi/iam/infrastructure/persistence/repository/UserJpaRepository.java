package com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
  @Query("select u from UserEntity u where lower(u.username) = lower(:username)")
  Optional<UserEntity> findByUsername(@Param("username") String username);

  @Query("select u from UserEntity u where lower(u.email) = lower(:email)")
  Optional<UserEntity> findByEmail(@Param("email") String email);

  @Query(
    "select case when count(u) > 0 then true else false end from UserEntity u where lower(u.username) = lower(:username)"
  )
  boolean existsByUsername(@Param("username") String username);

  @Query(
    "select case when count(u) > 0 then true else false end from UserEntity u where lower(u.email) = lower(:email)"
  )
  boolean existsByEmail(@Param("email") String email);
}
