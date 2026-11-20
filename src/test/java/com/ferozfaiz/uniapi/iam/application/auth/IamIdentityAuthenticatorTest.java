package com.ferozfaiz.uniapi.iam.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedIdentity;
import com.ferozfaiz.uniapi.iam.api.auth.AuthenticationFailedException;
import com.ferozfaiz.uniapi.iam.application.port.PasswordHasher;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import com.ferozfaiz.uniapi.iam.support.IamTestUsers;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IamIdentityAuthenticatorTest {

  private final FakeUserRepository users = new FakeUserRepository();
  private final IamIdentityAuthenticator authenticator = new IamIdentityAuthenticator(
    users,
    new TestPasswordHasher()
  );

  @Test
  void authenticatesActiveUserByUsername() {
    users.save(IamTestUsers.testUser());

    AuthenticatedIdentity identity = authenticator.authenticate(" feroz ", "secret");

    assertThat(identity.userId()).isEqualTo(1L);
    assertThat(identity.username()).isEqualTo("feroz");
    assertThat(identity.active()).isTrue();
  }

  @Test
  void authenticatesActiveUserByUsernameCaseInsensitively() {
    users.save(IamTestUsers.activeUser(1L, "Admin", "admin@example.com"));

    AuthenticatedIdentity identity = authenticator.authenticate("admin", "secret");

    assertThat(identity.userId()).isEqualTo(1L);
    assertThat(identity.username()).isEqualTo("Admin");
  }

  @Test
  void authenticatesActiveUserByEmailCaseInsensitively() {
    users.save(IamTestUsers.activeUser(1L, "feroz", "Feroz@Example.com"));

    AuthenticatedIdentity identity = authenticator.authenticate("FEROZ@example.com", "secret");

    assertThat(identity.userId()).isEqualTo(1L);
    assertThat(identity.username()).isEqualTo("feroz");
  }

  @Test
  void rejectsInvalidPassword() {
    users.save(IamTestUsers.testUser());

    assertThatThrownBy(() -> authenticator.authenticate("feroz", "wrong"))
      .isInstanceOf(AuthenticationFailedException.class)
      .extracting("reason")
      .isEqualTo(AuthenticationFailedException.Reason.INVALID_CREDENTIALS);
    assertThatThrownBy(() -> authenticator.authenticate("feroz", "wrong"))
      .isInstanceOf(AuthenticationFailedException.class)
      .extracting("messageKey")
      .isEqualTo("iam.application.auth.invalid-credentials");
  }

  @Test
  void rejectsLockedUser() {
    users.save(IamTestUsers.lockedUser());

    assertThatThrownBy(() -> authenticator.authenticate("feroz", "secret"))
      .isInstanceOf(AuthenticationFailedException.class)
      .extracting("reason")
      .isEqualTo(AuthenticationFailedException.Reason.LOCKED);
    assertThatThrownBy(() -> authenticator.authenticate("feroz", "secret"))
      .isInstanceOf(AuthenticationFailedException.class)
      .extracting("messageKey")
      .isEqualTo("iam.application.auth.locked");
  }

  private static class TestPasswordHasher implements PasswordHasher {

    @Override
    public String hash(String rawPassword) {
      return "hashed:" + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
      return hash(rawPassword).equals(passwordHash);
    }
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
