package com.ferozfaiz.uniapi.iam.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.iam.application.UserApplicationService;
import com.ferozfaiz.uniapi.iam.application.command.RegisterUserCommand;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.model.UserRole;
import com.ferozfaiz.uniapi.iam.domain.repository.RoleRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRoleRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultAdminUserInitializerTest {

  private final AdminBootstrapProperties properties = new AdminBootstrapProperties();
  private final UserApplicationService users = org.mockito.Mockito.mock(
    UserApplicationService.class
  );
  private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
  private final RoleRepository roleRepository = org.mockito.Mockito.mock(RoleRepository.class);
  private final UserRoleRepository userRoleRepository = org.mockito.Mockito.mock(
    UserRoleRepository.class
  );
  private final DefaultAdminUserInitializer initializer = new DefaultAdminUserInitializer(
    properties,
    users,
    userRepository,
    roleRepository,
    userRoleRepository
  );

  @Test
  void skipsBootstrapWhenDisabled() {
    properties.setEnabled(false);

    initializer.run(null);

    verifyNoInteractions(users, userRepository, roleRepository, userRoleRepository);
  }

  @Test
  void createsAdminUserAndAssignsAdminRoleWhenMissing() {
    configureEnabledAdmin();
    User admin = user(1L);
    Role role = role(2L);
    when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
    when(users.register(any(RegisterUserCommand.class))).thenReturn(admin);
    when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(role));
    when(userRoleRepository.existsByUserIdAndRoleId(1L, 2L)).thenReturn(false);

    initializer.run(null);

    ArgumentCaptor<RegisterUserCommand> command = ArgumentCaptor.forClass(
      RegisterUserCommand.class
    );
    ArgumentCaptor<UserRole> userRole = ArgumentCaptor.forClass(UserRole.class);
    verify(users).register(command.capture());
    verify(userRoleRepository).save(userRole.capture());
    assertThat(command.getValue())
      .extracting(
        RegisterUserCommand::username,
        RegisterUserCommand::email,
        RegisterUserCommand::rawPassword
      )
      .containsExactly("admin", "admin@example.com", "secret");
    assertThat(userRole.getValue().userId()).isEqualTo(1L);
    assertThat(userRole.getValue().roleId()).isEqualTo(2L);
    assertThat(userRole.getValue().createdBy()).isEqualTo("system");
  }

  @Test
  void reusesExistingAdminUserAndDoesNotDuplicateRoleAssignment() {
    configureEnabledAdmin();
    when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L)));
    when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(role(2L)));
    when(userRoleRepository.existsByUserIdAndRoleId(1L, 2L)).thenReturn(true);

    initializer.run(null);

    verify(users, never()).register(any(RegisterUserCommand.class));
    verify(userRoleRepository, never()).save(any(UserRole.class));
  }

  @Test
  void failsFastWhenEnabledWithoutPassword() {
    properties.setEnabled(true);
    properties.setUsername("admin");
    properties.setEmail("admin@example.com");

    assertThatThrownBy(() -> initializer.run(null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Admin bootstrap password must be configured");
  }

  private void configureEnabledAdmin() {
    properties.setEnabled(true);
    properties.setUsername("admin");
    properties.setEmail("admin@example.com");
    properties.setPassword("secret");
  }

  private static User user(Long id) {
    OffsetDateTime now = OffsetDateTime.now();
    return new User(
      id,
      "admin",
      "admin@example.com",
      "hash",
      "System",
      "Admin",
      true,
      true,
      true,
      true,
      null,
      null,
      now,
      now,
      "system",
      "system"
    );
  }

  private static Role role(Long id) {
    OffsetDateTime now = OffsetDateTime.now();
    return new Role(
      id,
      "ADMIN",
      "Administrator",
      "Default administrator role",
      true,
      now,
      now,
      "system",
      "system"
    );
  }
}
