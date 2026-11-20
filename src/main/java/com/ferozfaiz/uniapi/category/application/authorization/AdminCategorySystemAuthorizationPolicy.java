package com.ferozfaiz.uniapi.category.application.authorization;

import com.ferozfaiz.uniapi.category.api.AdminCategoryAuthorities;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("adminCategorySystemAuthorizationPolicy")
public class AdminCategorySystemAuthorizationPolicy {

  public static final String ADMIN_CATEGORY_SYSTEM_READ_EXPRESSION =
    "@adminCategorySystemAuthorizationPolicy.canRead(authentication,#systemKey)";
  public static final String ADMIN_CATEGORY_SYSTEM_WRITE_EXPRESSION =
    "@adminCategorySystemAuthorizationPolicy.canWrite(authentication,#systemKey)";

  private static final Logger log = LoggerFactory.getLogger(
    AdminCategorySystemAuthorizationPolicy.class
  );

  private final AdminCategorySystemRegistry registrations;

  public AdminCategorySystemAuthorizationPolicy(AdminCategorySystemRegistry registrations) {
    this.registrations = registrations;
  }

  public boolean canAccess(Authentication authentication, String systemKey, Operation operation) {
    if (operation == null || !isAuthenticated(authentication)) {
      logDenied(safeKey(systemKey), operation == null ? "unknown" : operation.code());
      return false;
    }

    String selectedKey = safeKey(systemKey);
    String permission = standardPermission(selectedKey, operation);
    if (isCanonicalKey(selectedKey)) {
      try {
        Optional<AdminCategorySystemRegistration> registration = registrations.findRegistration(
          selectedKey
        );
        if (registration.isPresent() && declares(registration.orElseThrow(), operation)) {
          permission = registration
            .orElseThrow()
            .runtimeBinding()
            .map(binding -> binding.permissionMapping())
            .flatMap(mapping -> mapping.permissionFor(operation))
            .orElse(null);
        }
      } catch (AdminCategoryRegistrationValidationException ignored) {
        permission = standardPermission(selectedKey, operation);
      }
    }

    boolean allowed = AdminCategoryAuthorities.hasPermission(authentication, permission);
    if (!allowed) {
      logDenied(selectedKey, operation.code());
    }
    return allowed;
  }

  public boolean canRead(Authentication authentication, String systemKey) {
    boolean allowed = AdminCategoryAuthorities.hasSystemRead(authentication, systemKey);
    if (!allowed) {
      logDenied(systemKey, "read");
    }
    return allowed;
  }

  public boolean canWrite(Authentication authentication, String systemKey) {
    boolean allowed = AdminCategoryAuthorities.hasSystemWrite(authentication, systemKey);
    if (!allowed) {
      logDenied(systemKey, "write");
    }
    return allowed;
  }

  private static boolean declares(
    AdminCategorySystemRegistration registration,
    Operation operation
  ) {
    return registration
      .definition()
      .map(definition -> definition.operations().supportedOperations().contains(operation))
      .orElse(false);
  }

  private static boolean isAuthenticated(Authentication authentication) {
    return authentication != null && authentication.isAuthenticated();
  }

  private static String standardPermission(String systemKey, Operation operation) {
    try {
      return AdminCategoryAuthorities.standardPermission(systemKey, operation);
    } catch (IllegalArgumentException failure) {
      return null;
    }
  }

  private static String safeKey(String systemKey) {
    return systemKey == null ? "" : systemKey.trim();
  }

  private static boolean isCanonicalKey(String systemKey) {
    return systemKey.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*") && systemKey.length() <= 63;
  }

  private static void logDenied(String systemKey, String operation) {
    log
      .atWarn()
      .addKeyValue("category.event", "authorization.denied")
      .addKeyValue("systemKey", systemKey)
      .addKeyValue("operation", operation)
      .addKeyValue("outcome", "denied")
      .addKeyValue("errorCode", "ACCESS_DENIED")
      .log("admin category authorization denied");
  }
}
