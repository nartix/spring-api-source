package com.ferozfaiz.uniapi.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.iam.application.command.AdminCreateUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.AdminUserMutationCommand;
import com.ferozfaiz.uniapi.iam.application.command.AdminUserStatusCommand;
import com.ferozfaiz.uniapi.iam.application.port.AdminUserListingRepository;
import com.ferozfaiz.uniapi.iam.application.port.PasswordHasher;
import com.ferozfaiz.uniapi.iam.application.query.AdminListUsersQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminUserListItem;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.model.UserPermission;
import com.ferozfaiz.uniapi.iam.domain.model.UserRole;
import com.ferozfaiz.uniapi.iam.domain.repository.PermissionRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.RoleRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserPermissionRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRoleRepository;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminUserApplicationServiceTest {

  private static final Clock FIXED_CLOCK = Clock.fixed(
    Instant.parse("2026-05-01T06:30:00Z"),
    ZoneOffset.UTC
  );
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-01T06:30:00Z");

  private final FakeUserRepository users = new FakeUserRepository();
  private final TestPasswordHasher passwordHasher = new TestPasswordHasher();
  private final FakeAdminUserListingRepository listings = new FakeAdminUserListingRepository();
  private final FakeRoleRepository roles = new FakeRoleRepository();
  private final FakeUserRoleRepository userRoles = new FakeUserRoleRepository();
  private final FakePermissionRepository permissions = new FakePermissionRepository();
  private final FakeUserPermissionRepository userPermissions = new FakeUserPermissionRepository();
  private final AdminUserApplicationService service = new AdminUserApplicationService(
    new UserApplicationService(users, passwordHasher, FIXED_CLOCK),
    listings,
    roles,
    userRoles,
    permissions,
    userPermissions,
    FIXED_CLOCK
  );

  @Test
  void createStoresUserAndRequestedAssignments() {
    roles.save(role(10L, "ADMIN"));
    permissions.save(permission(20L, "admin:users:read"));

    User created = service.create(
      new AdminCreateUserCommand(
        "feroz",
        "feroz@example.com",
        "secret123",
        "Feroz",
        "Faiz",
        false,
        List.of("ADMIN"),
        List.of("admin:users:read")
      )
    );

    assertThat(created.id()).isEqualTo(1L);
    assertThat(created.active()).isFalse();
    assertThat(passwordHasher.hashedValues()).containsExactly("secret123");
    assertThat(userRoles.findByUserId(created.id()))
      .extracting(UserRole::roleId)
      .containsExactly(10L);
    assertThat(userPermissions.findByUserId(created.id()))
      .extracting(UserPermission::permissionId)
      .containsExactly(20L);
  }

  @Test
  void createHashesSubmittedCredential() {
    service.create(
      new AdminCreateUserCommand(
        "feroz",
        "feroz@example.com",
        "secret123",
        "Feroz",
        "Faiz",
        true,
        List.of(),
        List.of()
      )
    );

    assertThat(passwordHasher.hashedValues()).containsExactly("secret123");
  }

  @Test
  void updateReplacesProvidedAssignments() {
    roles.save(role(10L, "ADMIN"));
    roles.save(role(11L, "USER"));
    permissions.save(permission(20L, "admin:users:read"));
    permissions.save(permission(21L, "users:read"));
    User existing = users.save(user("feroz", "feroz@example.com", true));
    userRoles.save(new UserRole(null, existing.id(), 11L, NOW, null, "system"));
    userPermissions.save(new UserPermission(null, existing.id(), 21L, NOW, null, "system"));

    User updated = service.update(
      existing.id(),
      new AdminUserMutationCommand(
        "feroz-updated",
        "updated@example.com",
        "Feroz",
        "Updated",
        true,
        List.of("ADMIN"),
        List.of("admin:users:read")
      )
    );

    assertThat(updated.username()).isEqualTo("feroz-updated");
    assertThat(userRoles.findByUserId(existing.id()))
      .extracting(UserRole::roleId)
      .containsExactly(10L);
    assertThat(userPermissions.findByUserId(existing.id()))
      .extracting(UserPermission::permissionId)
      .containsExactly(20L);
  }

  @Test
  void updateStatusChangesOnlyAccountState() {
    User existing = users.save(
      new User(
        null,
        "feroz",
        "feroz@example.com",
        "hashed:secret",
        "Feroz",
        "Faiz",
        true,
        true,
        true,
        false,
        NOW.minusDays(1),
        NOW.minusHours(1),
        NOW.minusDays(10),
        NOW.minusDays(2),
        "import",
        "import"
      )
    );
    userRoles.save(new UserRole(null, existing.id(), 10L, NOW, null, "system"));
    userPermissions.save(new UserPermission(null, existing.id(), 20L, NOW, null, "system"));

    User updated = service.updateStatus(
      existing.id(),
      new AdminUserStatusCommand(false, null, false, true)
    );

    assertThat(updated.username()).isEqualTo("feroz");
    assertThat(updated.email()).isEqualTo("feroz@example.com");
    assertThat(updated.firstName()).isEqualTo("Feroz");
    assertThat(updated.lastName()).isEqualTo("Faiz");
    assertThat(updated.active()).isFalse();
    assertThat(updated.accountNonExpired()).isTrue();
    assertThat(updated.accountNonLocked()).isFalse();
    assertThat(updated.credentialsNonExpired()).isTrue();
    assertThat(updated.createdAt()).isEqualTo(NOW.minusDays(10));
    assertThat(updated.updatedAt()).isEqualTo(NOW);
    assertThat(userRoles.findByUserId(existing.id()))
      .extracting(UserRole::roleId)
      .containsExactly(10L);
    assertThat(userPermissions.findByUserId(existing.id()))
      .extracting(UserPermission::permissionId)
      .containsExactly(20L);
  }

  @Test
  void rejectsUnknownRoleCode() {
    assertThatThrownBy(() ->
      service.create(
        new AdminCreateUserCommand(
          "feroz",
          "feroz@example.com",
          "secret123",
          null,
          null,
          true,
          List.of("MISSING"),
          List.of()
        )
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .satisfies(exception ->
        assertThat(((InvalidRequestException) exception).messageKey()).isEqualTo(
          "iam.api.admin-user.role.unknown"
        )
      );
  }

  @Test
  void findAllDelegatesToAdminListingRepository() {
    AdminListUsersQuery query = new AdminListUsersQuery(
      new PageCriteria(0, 20),
      SortCriteria.of("username", SortDirection.ASC),
      "feroz",
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      List.of(),
      List.of()
    );
    PageResult<AdminUserListItem> expected = new PageResult<>(
      List.of(),
      0,
      20,
      0,
      0,
      true,
      true,
      query.sort()
    );
    listings.result = expected;

    assertThat(service.findAll(query)).isSameAs(expected);
    assertThat(listings.query).isEqualTo(query);
  }

  private static User user(String username, String email, boolean active) {
    return new User(
      null,
      username,
      email,
      "hashed:secret",
      null,
      null,
      active,
      true,
      true,
      true,
      null,
      null,
      NOW,
      NOW,
      "system",
      "system"
    );
  }

  private static Role role(Long id, String code) {
    return new Role(id, code, code, null, true, NOW, NOW, "system", "system");
  }

  private static Permission permission(Long id, String code) {
    return new Permission(
      id,
      code,
      code,
      null,
      "admin-users",
      "read",
      true,
      NOW,
      NOW,
      "system",
      "system"
    );
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

  private static class FakeAdminUserListingRepository implements AdminUserListingRepository {

    private AdminListUsersQuery query;
    private PageResult<AdminUserListItem> result;

    @Override
    public PageResult<AdminUserListItem> findAll(AdminListUsersQuery query) {
      this.query = query;
      return result;
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
      List<User> sortedUsers = savedUsers.stream().sorted(Comparator.comparing(User::id)).toList();
      return new PageResult<>(
        sortedUsers,
        page.page(),
        page.size(),
        sortedUsers.size(),
        1,
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

  private static class FakeRoleRepository implements RoleRepository {

    private final List<Role> roles = new ArrayList<>();

    @Override
    public Role save(Role role) {
      roles.removeIf(existing -> existing.id().equals(role.id()));
      roles.add(role);
      return role;
    }

    @Override
    public Optional<Role> findById(Long id) {
      return roles
        .stream()
        .filter(role -> role.id().equals(id))
        .findFirst();
    }

    @Override
    public Optional<Role> findByCode(String code) {
      return roles
        .stream()
        .filter(role -> role.code().equals(code))
        .findFirst();
    }

    @Override
    public boolean existsByCode(String code) {
      return findByCode(code).isPresent();
    }

    @Override
    public List<Role> findAll() {
      return List.copyOf(roles);
    }

    @Override
    public void deleteById(Long id) {
      roles.removeIf(role -> role.id().equals(id));
    }
  }

  private static class FakePermissionRepository implements PermissionRepository {

    private final List<Permission> permissions = new ArrayList<>();

    @Override
    public Permission save(Permission permission) {
      permissions.removeIf(existing -> existing.id().equals(permission.id()));
      permissions.add(permission);
      return permission;
    }

    @Override
    public Optional<Permission> findById(Long id) {
      return permissions
        .stream()
        .filter(permission -> permission.id().equals(id))
        .findFirst();
    }

    @Override
    public Optional<Permission> findByCode(String code) {
      return permissions
        .stream()
        .filter(permission -> permission.code().equals(code))
        .findFirst();
    }

    @Override
    public boolean existsByCode(String code) {
      return findByCode(code).isPresent();
    }

    @Override
    public List<Permission> findAll() {
      return List.copyOf(permissions);
    }

    @Override
    public void deleteById(Long id) {
      permissions.removeIf(permission -> permission.id().equals(id));
    }
  }

  private static class FakeUserRoleRepository implements UserRoleRepository {

    private final List<UserRole> userRoles = new ArrayList<>();
    private long nextId = 1L;

    @Override
    public UserRole save(UserRole userRole) {
      UserRole saved = new UserRole(
        userRole.id() == null ? nextId++ : userRole.id(),
        userRole.userId(),
        userRole.roleId(),
        userRole.assignedAt(),
        userRole.expiresAt(),
        userRole.createdBy()
      );
      userRoles.removeIf(existing -> existing.id().equals(saved.id()));
      userRoles.add(saved);
      return saved;
    }

    @Override
    public Optional<UserRole> findById(Long id) {
      return userRoles
        .stream()
        .filter(userRole -> userRole.id().equals(id))
        .findFirst();
    }

    @Override
    public List<UserRole> findByUserId(Long userId) {
      return userRoles
        .stream()
        .filter(userRole -> userRole.userId().equals(userId))
        .toList();
    }

    @Override
    public List<UserRole> findByRoleId(Long roleId) {
      return userRoles
        .stream()
        .filter(userRole -> userRole.roleId().equals(roleId))
        .toList();
    }

    @Override
    public boolean existsByUserIdAndRoleId(Long userId, Long roleId) {
      return userRoles
        .stream()
        .anyMatch(userRole -> userRole.userId().equals(userId) && userRole.roleId().equals(roleId));
    }

    @Override
    public void deleteByUserIdAndRoleId(Long userId, Long roleId) {
      userRoles.removeIf(
        userRole -> userRole.userId().equals(userId) && userRole.roleId().equals(roleId)
      );
    }
  }

  private static class FakeUserPermissionRepository implements UserPermissionRepository {

    private final List<UserPermission> userPermissions = new ArrayList<>();
    private long nextId = 1L;

    @Override
    public UserPermission save(UserPermission userPermission) {
      UserPermission saved = new UserPermission(
        userPermission.id() == null ? nextId++ : userPermission.id(),
        userPermission.userId(),
        userPermission.permissionId(),
        userPermission.grantedAt(),
        userPermission.expiresAt(),
        userPermission.createdBy()
      );
      userPermissions.removeIf(existing -> existing.id().equals(saved.id()));
      userPermissions.add(saved);
      return saved;
    }

    @Override
    public Optional<UserPermission> findById(Long id) {
      return userPermissions
        .stream()
        .filter(userPermission -> userPermission.id().equals(id))
        .findFirst();
    }

    @Override
    public List<UserPermission> findByUserId(Long userId) {
      return userPermissions
        .stream()
        .filter(userPermission -> userPermission.userId().equals(userId))
        .toList();
    }

    @Override
    public List<UserPermission> findByPermissionId(Long permissionId) {
      return userPermissions
        .stream()
        .filter(userPermission -> userPermission.permissionId().equals(permissionId))
        .toList();
    }

    @Override
    public boolean existsByUserIdAndPermissionId(Long userId, Long permissionId) {
      return userPermissions
        .stream()
        .anyMatch(
          userPermission ->
            userPermission.userId().equals(userId) &&
            userPermission.permissionId().equals(permissionId)
        );
    }

    @Override
    public void deleteByUserIdAndPermissionId(Long userId, Long permissionId) {
      userPermissions.removeIf(
        userPermission ->
          userPermission.userId().equals(userId) &&
          userPermission.permissionId().equals(permissionId)
      );
    }
  }
}
