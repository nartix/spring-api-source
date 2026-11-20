package com.ferozfaiz.uniapi.iam.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.iam.domain.exception.UserNotFoundException;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import com.ferozfaiz.uniapi.iam.support.IamTestUsers;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IamLoginActivityRecorderTest {

  private static final Clock FIXED_CLOCK = Clock.fixed(
    Instant.parse("2026-05-02T07:45:00Z"),
    ZoneOffset.UTC
  );

  private final FakeUserRepository users = new FakeUserRepository();
  private final IamLoginActivityRecorder recorder = new IamLoginActivityRecorder(
    users,
    FIXED_CLOCK
  );

  @Test
  void recordsLastLoginAtForExistingUser() {
    User existing = users.save(IamTestUsers.testUser());

    recorder.recordSuccessfulLogin(existing.id());

    User saved = users.findById(existing.id()).orElseThrow();
    assertThat(saved.lastLoginAt()).isEqualTo(OffsetDateTime.parse("2026-05-02T07:45:00Z"));
    assertThat(saved.updatedAt()).isEqualTo(existing.updatedAt());
    assertThat(saved.createdAt()).isEqualTo(existing.createdAt());
  }

  @Test
  void rejectsMissingUser() {
    assertThatThrownBy(() -> recorder.recordSuccessfulLogin(404L))
      .isInstanceOf(UserNotFoundException.class)
      .hasMessage("User not found: 404");
  }

  private static class FakeUserRepository implements UserRepository {

    private final List<User> savedUsers = new ArrayList<>();
    private long nextId = 1L;

    @Override
    public User save(User user) {
      User saved = new User(
        user.id() == null ? nextId++ : user.id(),
        user.username(),
        user.email(),
        user.passwordHash(),
        user.firstName(),
        user.lastName(),
        user.active(),
        user.accountNonExpired(),
        user.accountNonLocked(),
        user.credentialsNonExpired(),
        user.lastLoginAt(),
        user.lastActivityAt(),
        user.createdAt(),
        user.updatedAt(),
        user.createdBy(),
        user.updatedBy()
      );
      savedUsers.removeIf(existing -> existing.id().equals(saved.id()));
      savedUsers.add(saved);
      return saved;
    }

    @Override
    public Optional<User> findById(Long id) {
      return savedUsers
        .stream()
        .filter(user -> user.id().equals(id))
        .findFirst();
    }

    @Override
    public Optional<User> findByUsername(String username) {
      return savedUsers
        .stream()
        .filter(user -> user.username().equalsIgnoreCase(username))
        .findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
      return savedUsers
        .stream()
        .filter(user -> user.email().equalsIgnoreCase(email))
        .findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
      return savedUsers.stream().anyMatch(user -> user.username().equalsIgnoreCase(username));
    }

    @Override
    public boolean existsByEmail(String email) {
      return savedUsers.stream().anyMatch(user -> user.email().equalsIgnoreCase(email));
    }

    @Override
    public PageResult<User> findAll(PageCriteria page, SortCriteria sort) {
      return new PageResult<>(
        savedUsers,
        page.page(),
        page.size(),
        savedUsers.size(),
        savedUsers.isEmpty() ? 0 : 1,
        true,
        true,
        sort
      );
    }

    @Override
    public void deleteById(Long id) {
      savedUsers.removeIf(user -> user.id().equals(id));
    }
  }
}
