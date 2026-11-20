package com.ferozfaiz.uniapi.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.iam.application.command.PatchUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.RegisterUserCommand;
import com.ferozfaiz.uniapi.iam.application.port.PasswordHasher;
import com.ferozfaiz.uniapi.iam.application.query.ListUsersQuery;
import com.ferozfaiz.uniapi.iam.domain.exception.DuplicateUserException;
import com.ferozfaiz.uniapi.iam.domain.exception.UserNotFoundException;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import com.ferozfaiz.uniapi.iam.support.IamTestUsers;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class UserApplicationServiceTest {

  private static final Clock FIXED_CLOCK = Clock.fixed(
    Instant.parse("2026-05-01T06:30:00Z"),
    ZoneOffset.UTC
  );

  private final FakeUserRepository users = new FakeUserRepository();
  private final TestPasswordHasher passwordHasher = new TestPasswordHasher();
  private final UserApplicationService service = new UserApplicationService(
    users,
    passwordHasher,
    FIXED_CLOCK
  );

  @Test
  void registerCreatesActiveUserWithHashedPassword() {
    User registered = service.register(
      new RegisterUserCommand(" feroz ", " FEROZ@example.COM ", "secret", " Feroz ", " Faiz ")
    );

    assertThat(registered.id()).isEqualTo(1L);
    assertThat(registered.username()).isEqualTo("feroz");
    assertThat(registered.email()).isEqualTo("feroz@example.com");
    assertThat(registered.passwordHash()).isEqualTo("hashed:secret");
    assertThat(registered.firstName()).isEqualTo("Feroz");
    assertThat(registered.lastName()).isEqualTo("Faiz");
    assertThat(registered.active()).isTrue();
    assertThat(registered.accountNonExpired()).isTrue();
    assertThat(registered.accountNonLocked()).isTrue();
    assertThat(registered.credentialsNonExpired()).isTrue();
    assertThat(registered.createdAt()).isEqualTo(OffsetDateTime.parse("2026-05-01T06:30:00Z"));
    assertThat(registered.updatedAt()).isEqualTo(OffsetDateTime.parse("2026-05-01T06:30:00Z"));
    assertThat(registered.createdBy()).isEqualTo("system");
    assertThat(registered.updatedBy()).isEqualTo("system");
    assertThat(passwordHasher.hashedValues()).containsExactly("secret");
  }

  @Test
  void registerRejectsDuplicateUsername() {
    users.save(existingUser("feroz", "feroz@example.com"));

    assertThatThrownBy(() ->
      service.register(new RegisterUserCommand("feroz", "other@example.com", "secret", null, null))
    )
      .isInstanceOf(DuplicateUserException.class)
      .hasMessage("Username is already registered")
      .satisfies(exception ->
        assertThat(((DuplicateUserException) exception).messageKey()).isEqualTo(
          "iam.domain.user.duplicate.username"
        )
      );
  }

  @Test
  void registerRejectsDuplicateUsernameCaseInsensitively() {
    users.save(existingUser("Admin", "admin@example.com"));

    assertThatThrownBy(() ->
      service.register(new RegisterUserCommand("admin", "other@example.com", "secret", null, null))
    )
      .isInstanceOf(DuplicateUserException.class)
      .hasMessage("Username is already registered")
      .satisfies(exception ->
        assertThat(((DuplicateUserException) exception).messageKey()).isEqualTo(
          "iam.domain.user.duplicate.username"
        )
      );
  }

  @Test
  void registerRejectsDuplicateEmailCaseInsensitively() {
    users.save(existingUser("feroz", "feroz@example.com"));

    assertThatThrownBy(() ->
      service.register(new RegisterUserCommand("other", "FEROZ@example.com", "secret", null, null))
    )
      .isInstanceOf(DuplicateUserException.class)
      .hasMessage("Email is already registered")
      .satisfies(exception ->
        assertThat(((DuplicateUserException) exception).messageKey()).isEqualTo(
          "iam.domain.user.duplicate.email"
        )
      );
  }

  @Test
  void patchAllowsUsernameAndEmailCaseChangeForSameUser() {
    User existing = users.save(existingUser("Admin", "Admin@example.com"));

    User patched = service.patch(
      existing.id(),
      new PatchUserCommand("admin", "admin@example.com", null, null, null)
    );

    assertThat(patched.username()).isEqualTo("admin");
    assertThat(patched.email()).isEqualTo("admin@example.com");
  }

  @Test
  void registerRejectsBlankRequiredFields() {
    assertThatThrownBy(() ->
      service.register(new RegisterUserCommand("feroz", "feroz@example.com", " ", null, null))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("rawPassword must not be blank");
  }

  @Test
  void patchUpdatesOnlyProvidedFields() {
    User existing = users.save(existingUser("feroz", "feroz@example.com"));

    User patched = service.patch(
      existing.id(),
      new PatchUserCommand(null, " FEROZ.UPDATED@example.COM ", " Feroz Updated ", null, false)
    );

    assertThat(patched.id()).isEqualTo(existing.id());
    assertThat(patched.username()).isEqualTo("feroz");
    assertThat(patched.email()).isEqualTo("feroz.updated@example.com");
    assertThat(patched.passwordHash()).isEqualTo(existing.passwordHash());
    assertThat(patched.firstName()).isEqualTo("Feroz Updated");
    assertThat(patched.lastName()).isEqualTo(existing.lastName());
    assertThat(patched.active()).isFalse();
    assertThat(patched.createdAt()).isEqualTo(existing.createdAt());
    assertThat(patched.updatedAt()).isEqualTo(OffsetDateTime.parse("2026-05-01T06:30:00Z"));
    assertThat(patched.createdBy()).isEqualTo(existing.createdBy());
    assertThat(patched.updatedBy()).isEqualTo("system");
  }

  @Test
  void patchRejectsBlankUsernameWhenProvided() {
    User existing = users.save(existingUser("feroz", "feroz@example.com"));

    assertThatThrownBy(() ->
      service.patch(existing.id(), new PatchUserCommand(" ", null, null, null, null))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("username must not be blank");
  }

  @Test
  void patchRejectsDuplicateEmail() {
    users.save(existingUser("feroz", "feroz@example.com"));
    User other = users.save(existingUser("other", "other@example.com"));

    assertThatThrownBy(() ->
      service.patch(other.id(), new PatchUserCommand(null, "feroz@example.com", null, null, null))
    )
      .isInstanceOf(DuplicateUserException.class)
      .hasMessage("Email is already registered")
      .satisfies(exception ->
        assertThat(((DuplicateUserException) exception).messageKey()).isEqualTo(
          "iam.domain.user.duplicate.email"
        )
      );
  }

  @Test
  void patchRejectsMissingUser() {
    assertThatThrownBy(() ->
      service.patch(404L, new PatchUserCommand(null, null, "Feroz", null, null))
    )
      .isInstanceOf(UserNotFoundException.class)
      .hasMessage("User not found: 404")
      .satisfies(exception ->
        assertThat(((UserNotFoundException) exception).messageKey()).isEqualTo(
          "iam.domain.user.not-found"
        )
      );
  }

  @Test
  void findAllReturnsPagedUsers() {
    users.save(existingUser("feroz", "feroz@example.com"));
    users.save(existingUser("alex", "alex@example.com"));

    PageResult<User> result = service.findAll(
      new ListUsersQuery(new PageCriteria(0, 1), SortCriteria.of("username", SortDirection.ASC))
    );

    assertThat(result.content()).extracting(User::username).containsExactly("alex");
    assertThat(result.page()).isZero();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.totalElements()).isEqualTo(2);
    assertThat(result.totalPages()).isEqualTo(2);
    assertThat(result.first()).isTrue();
    assertThat(result.last()).isFalse();
    assertThat(result.sort()).isEqualTo(SortCriteria.of("username", SortDirection.ASC));
  }

  @Test
  void readMethodsDeclareUserReadPolicyAuthorization() throws Exception {
    assertPreAuthorize(
      "findAll",
      new Class<?>[] { ListUsersQuery.class },
      "@userReadAuthorizationPolicy.canRead(authentication)"
    );
    assertPreAuthorize(
      "findById",
      new Class<?>[] { Long.class },
      "@userReadAuthorizationPolicy.canRead(authentication, #id)"
    );
  }

  private static void assertPreAuthorize(
    String methodName,
    Class<?>[] parameterTypes,
    String expectedExpression
  ) throws NoSuchMethodException {
    Method method = UserApplicationService.class.getDeclaredMethod(methodName, parameterTypes);

    assertThat(method.getAnnotation(PreAuthorize.class))
      .extracting(PreAuthorize::value)
      .isEqualTo(expectedExpression);
  }

  private static User existingUser(String username, String email) {
    return IamTestUsers.unsavedUser(username, email);
  }

  private static class TestPasswordHasher implements PasswordHasher {

    private final List<String> hashedValues = new ArrayList<>();

    @Override
    public String hash(String rawPassword) {
      hashedValues.add(rawPassword);
      return "hashed:" + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
      return ("hashed:" + rawPassword).equals(passwordHash);
    }

    List<String> hashedValues() {
      return hashedValues;
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
      List<User> sortedUsers = savedUsers.stream().sorted(comparator(sort)).toList();
      int fromIndex = Math.min(page.page() * page.size(), sortedUsers.size());
      int toIndex = Math.min(fromIndex + page.size(), sortedUsers.size());
      List<User> content = sortedUsers.subList(fromIndex, toIndex);
      int totalPages = (int) Math.ceil((double) sortedUsers.size() / page.size());

      return new PageResult<>(
        content,
        page.page(),
        page.size(),
        sortedUsers.size(),
        totalPages,
        page.page() == 0,
        page.page() >= Math.max(totalPages - 1, 0),
        sort
      );
    }

    @Override
    public void deleteById(Long id) {
      savedUsers.removeIf(user -> user.id().equals(id));
    }

    private static Comparator<User> comparator(SortCriteria sort) {
      return sort
        .orders()
        .stream()
        .map(FakeUserRepository::comparator)
        .reduce(Comparator::thenComparing)
        .orElseThrow(() -> new IllegalArgumentException("sort orders must not be empty"));
    }

    private static Comparator<User> comparator(SortOrder sort) {
      Comparator<User> comparator = switch (sort.property()) {
        case "id" -> Comparator.comparing(User::id);
        case "username" -> Comparator.comparing(User::username);
        case "email" -> Comparator.comparing(User::email);
        case "active" -> Comparator.comparing(User::active);
        case "createdAt" -> Comparator.comparing(User::createdAt);
        case "updatedAt" -> Comparator.comparing(User::updatedAt);
        default -> throw new IllegalArgumentException(
          "Unsupported user sort property: " + sort.property()
        );
      };
      return sort.direction() == SortDirection.DESC ? comparator.reversed() : comparator;
    }
  }
}
