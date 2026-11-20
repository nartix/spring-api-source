package com.ferozfaiz.uniapi.iam.infrastructure.bootstrap;

import com.ferozfaiz.uniapi.iam.application.UserApplicationService;
import com.ferozfaiz.uniapi.iam.application.command.RegisterUserCommand;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.model.UserRole;
import com.ferozfaiz.uniapi.iam.domain.repository.RoleRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRoleRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class DefaultAdminUserInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DefaultAdminUserInitializer.class);
  private static final String ADMIN_ROLE_CODE = "ADMIN";
  private static final String SYSTEM_ACTOR = "system";

  private final AdminBootstrapProperties properties;
  private final UserApplicationService users;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;

  public DefaultAdminUserInitializer(
    AdminBootstrapProperties properties,
    UserApplicationService users,
    UserRepository userRepository,
    RoleRepository roleRepository,
    UserRoleRepository userRoleRepository
  ) {
    this.properties = properties;
    this.users = users;
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.userRoleRepository = userRoleRepository;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!properties.isEnabled()) {
      return;
    }

    validateAdminProperties();

    User admin = userRepository
      .findByUsername(properties.getUsername().trim())
      .orElseGet(this::registerAdminUser);
    Role adminRole = roleRepository
      .findByCode(ADMIN_ROLE_CODE)
      .orElseThrow(() -> new IllegalStateException("ADMIN role is missing"));

    if (!userRoleRepository.existsByUserIdAndRoleId(admin.id(), adminRole.id())) {
      userRoleRepository.save(
        new UserRole(null, admin.id(), adminRole.id(), OffsetDateTime.now(), null, SYSTEM_ACTOR)
      );
      log
        .atInfo()
        .addKeyValue("iam.event", "admin.role_assigned")
        .addKeyValue("user.id", admin.id())
        .addKeyValue("role.id", adminRole.id())
        .log("default admin role assigned");
    }
  }

  private User registerAdminUser() {
    User admin = users.register(
      new RegisterUserCommand(
        properties.getUsername(),
        properties.getEmail(),
        properties.getPassword(),
        "System",
        "Admin"
      )
    );
    log
      .atInfo()
      .addKeyValue("iam.event", "admin.created")
      .addKeyValue("user.id", admin.id())
      .log("default admin user created");
    return admin;
  }

  private void validateAdminProperties() {
    if (!StringUtils.hasText(properties.getUsername())) {
      throw new IllegalStateException("Admin bootstrap username must be configured");
    }
    if (!StringUtils.hasText(properties.getEmail())) {
      throw new IllegalStateException("Admin bootstrap email must be configured");
    }
    if (!StringUtils.hasText(properties.getPassword())) {
      throw new IllegalStateException("Admin bootstrap password must be configured");
    }
  }
}
