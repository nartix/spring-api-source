package com.ferozfaiz.uniapi.iam.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.mapper.IamPersistenceMapper;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class UserRepositoryAdapter implements UserRepository {

  private static final Map<String, String> SORT_PROPERTIES = Map.of(
    "id",
    "id",
    "username",
    "username",
    "email",
    "email",
    "active",
    "active",
    "createdAt",
    "createdAt",
    "updatedAt",
    "updatedAt"
  );

  private final UserJpaRepository users;

  public UserRepositoryAdapter(UserJpaRepository users) {
    this.users = users;
  }

  @Override
  @Transactional
  public User save(User user) {
    return Objects.requireNonNull(
      IamPersistenceMapper.toDomain(
        Objects.requireNonNull(users.save(IamPersistenceMapper.toEntity(user)))
      )
    );
  }

  @Override
  public Optional<User> findById(Long id) {
    return Objects.requireNonNull(users.findById(id).map(IamPersistenceMapper::toDomain));
  }

  @Override
  public Optional<User> findByUsername(String username) {
    return Objects.requireNonNull(
      users.findByUsername(username).map(IamPersistenceMapper::toDomain)
    );
  }

  @Override
  public Optional<User> findByEmail(String email) {
    return Objects.requireNonNull(users.findByEmail(email).map(IamPersistenceMapper::toDomain));
  }

  @Override
  public boolean existsByUsername(String username) {
    return users.existsByUsername(username);
  }

  @Override
  public boolean existsByEmail(String email) {
    return users.existsByEmail(email);
  }

  @Override
  public PageResult<User> findAll(PageCriteria page, SortCriteria sort) {
    Sort springSort = Sort.by(
      sort
        .orders()
        .stream()
        .map(order -> {
          String sortProperty = SORT_PROPERTIES.get(order.property());
          if (sortProperty == null) {
            throw new IllegalArgumentException(
              "Unsupported user sort property: " + order.property()
            );
          }
          return new Sort.Order(Sort.Direction.fromString(order.direction().name()), sortProperty);
        })
        .toList()
    );
    var pageable = PageRequest.of(page.page(), page.size(), springSort);
    var result = users.findAll(pageable);

    return new PageResult<>(
      result.getContent().stream().map(IamPersistenceMapper::toDomain).toList(),
      result.getNumber(),
      result.getSize(),
      result.getTotalElements(),
      result.getTotalPages(),
      result.isFirst(),
      result.isLast(),
      sort
    );
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    users.deleteById(id);
  }
}
