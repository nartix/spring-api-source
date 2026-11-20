package com.ferozfaiz.uniapi.iam.domain.repository;

import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Optional;

public interface UserRepository {
  User save(User user);

  Optional<User> findById(Long id);

  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  PageResult<User> findAll(PageCriteria page, SortCriteria sort);

  void deleteById(Long id);
}
