package com.ferozfaiz.uniapi.iam.application;

import com.ferozfaiz.uniapi.iam.application.command.AdminUserStatusCommand;
import com.ferozfaiz.uniapi.iam.application.command.PatchUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.RegisterUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.UpdateUserCommand;
import com.ferozfaiz.uniapi.iam.application.port.PasswordHasher;
import com.ferozfaiz.uniapi.iam.application.query.ListUsersQuery;
import com.ferozfaiz.uniapi.iam.domain.exception.DuplicateUserException;
import com.ferozfaiz.uniapi.iam.domain.exception.UserNotFoundException;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.text.Text;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserApplicationService {

  private static final Logger log = LoggerFactory.getLogger(UserApplicationService.class);
  private static final String SYSTEM_ACTOR = "system";

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final Clock clock;

  @Autowired
  public UserApplicationService(UserRepository users, PasswordHasher passwordHasher) {
    this(users, passwordHasher, Clock.systemUTC());
  }

  UserApplicationService(UserRepository users, PasswordHasher passwordHasher, Clock clock) {
    this.users = users;
    this.passwordHasher = passwordHasher;
    this.clock = clock;
  }

  public User register(RegisterUserCommand command) {
    String username = requireText(command.username(), "username");
    String email = requireText(command.email(), "email").toLowerCase(Locale.ROOT);
    String rawPassword = requireText(command.rawPassword(), "rawPassword");

    ensureUsernameIsAvailable(username);
    ensureEmailIsAvailable(email);

    OffsetDateTime now = OffsetDateTime.now(clock);
    User user = new User(
      null,
      username,
      email,
      passwordHasher.hash(rawPassword),
      Text.trimToNull(command.firstName()),
      Text.trimToNull(command.lastName()),
      true,
      true,
      true,
      true,
      null,
      null,
      now,
      now,
      SYSTEM_ACTOR,
      SYSTEM_ACTOR
    );

    User saved = users.save(user);
    log
      .atInfo()
      .addKeyValue("iam.event", "user.created")
      .addKeyValue("user.id", saved.id())
      .log("user created");
    return saved;
  }

  @PreAuthorize("@userReadAuthorizationPolicy.canRead(authentication)")
  @Transactional(readOnly = true)
  public PageResult<User> findAll(ListUsersQuery query) {
    return users.findAll(query.page(), query.sort());
  }

  @PreAuthorize("@userReadAuthorizationPolicy.canRead(authentication, #id)")
  @Transactional(readOnly = true)
  public User findById(Long id) {
    return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
  }

  public User update(Long id, UpdateUserCommand command) {
    User existing = findById(id);
    String username = requireText(command.username(), "username");
    String email = requireText(command.email(), "email").toLowerCase(Locale.ROOT);

    ensureUsernameIsAvailableForUser(username, existing);
    ensureEmailIsAvailableForUser(email, existing);

    User updated = new User(
      existing.id(),
      username,
      email,
      existing.passwordHash(),
      Text.trimToNull(command.firstName()),
      Text.trimToNull(command.lastName()),
      command.active(),
      existing.accountNonExpired(),
      existing.accountNonLocked(),
      existing.credentialsNonExpired(),
      existing.lastLoginAt(),
      existing.lastActivityAt(),
      existing.createdAt(),
      OffsetDateTime.now(clock),
      existing.createdBy(),
      SYSTEM_ACTOR
    );

    User saved = users.save(updated);
    log
      .atInfo()
      .addKeyValue("iam.event", "user.updated")
      .addKeyValue("user.id", saved.id())
      .log("user updated");
    return saved;
  }

  public User updateStatus(Long id, AdminUserStatusCommand command) {
    User existing = findById(id);
    User updated = new User(
      existing.id(),
      existing.username(),
      existing.email(),
      existing.passwordHash(),
      existing.firstName(),
      existing.lastName(),
      command.active() == null ? existing.active() : command.active(),
      command.accountNonExpired() == null
        ? existing.accountNonExpired()
        : command.accountNonExpired(),
      command.accountNonLocked() == null ? existing.accountNonLocked() : command.accountNonLocked(),
      command.credentialsNonExpired() == null
        ? existing.credentialsNonExpired()
        : command.credentialsNonExpired(),
      existing.lastLoginAt(),
      existing.lastActivityAt(),
      existing.createdAt(),
      OffsetDateTime.now(clock),
      existing.createdBy(),
      SYSTEM_ACTOR
    );

    User saved = users.save(updated);
    log
      .atInfo()
      .addKeyValue("iam.event", "user.status.updated")
      .addKeyValue("user.id", saved.id())
      .log("user status updated");
    return saved;
  }

  public User patch(Long id, PatchUserCommand command) {
    User existing = findById(id);
    String username =
      command.username() == null
        ? existing.username()
        : requireText(command.username(), "username");
    String email =
      command.email() == null
        ? existing.email()
        : requireText(command.email(), "email").toLowerCase(Locale.ROOT);

    ensureUsernameIsAvailableForUser(username, existing);
    ensureEmailIsAvailableForUser(email, existing);

    User patched = new User(
      existing.id(),
      username,
      email,
      existing.passwordHash(),
      command.firstName() == null ? existing.firstName() : Text.trimToNull(command.firstName()),
      command.lastName() == null ? existing.lastName() : Text.trimToNull(command.lastName()),
      command.active() == null ? existing.active() : command.active(),
      existing.accountNonExpired(),
      existing.accountNonLocked(),
      existing.credentialsNonExpired(),
      existing.lastLoginAt(),
      existing.lastActivityAt(),
      existing.createdAt(),
      OffsetDateTime.now(clock),
      existing.createdBy(),
      SYSTEM_ACTOR
    );

    User saved = users.save(patched);
    log
      .atInfo()
      .addKeyValue("iam.event", "user.patched")
      .addKeyValue("user.id", saved.id())
      .log("user patched");
    return saved;
  }

  public void delete(Long id) {
    findById(id);
    users.deleteById(id);
    log
      .atInfo()
      .addKeyValue("iam.event", "user.deleted")
      .addKeyValue("user.id", id)
      .log("user deleted");
  }

  private void ensureUsernameIsAvailable(String username) {
    if (users.existsByUsername(username)) {
      throw DuplicateUserException.usernameAlreadyRegistered();
    }
  }

  private void ensureEmailIsAvailable(String email) {
    if (users.existsByEmail(email)) {
      throw DuplicateUserException.emailAlreadyRegistered();
    }
  }

  private void ensureUsernameIsAvailableForUser(String username, User existing) {
    if (!existing.username().equalsIgnoreCase(username) && users.existsByUsername(username)) {
      throw DuplicateUserException.usernameAlreadyRegistered();
    }
  }

  private void ensureEmailIsAvailableForUser(String email, User existing) {
    if (!existing.email().equalsIgnoreCase(email) && users.existsByEmail(email)) {
      throw DuplicateUserException.emailAlreadyRegistered();
    }
  }

  private static String requireText(String value, String fieldName) {
    String trimmed = Text.trimToNull(value);
    if (trimmed == null) {
      throw InvalidRequestException.requiredField(fieldName);
    }
    return trimmed;
  }
}
