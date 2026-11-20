package com.ferozfaiz.uniapi.category.application.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.api.AdminCategoryAuthorities;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.iam.api.AdminAuthorities;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminCategorySystemAuthorizationPolicyTest {

  private static final String LEGACY_ADMIN_CATEGORIES_READ = "admin:categories:read";
  private static final String LEGACY_ADMIN_CATEGORIES_WRITE = "admin:categories:write";
  private static final Set<Operation> READ_OPERATIONS = Set.of(
    Operation.CAPABILITIES,
    Operation.LIST,
    Operation.TREE,
    Operation.PARENT_OPTIONS,
    Operation.GET,
    Operation.BREADCRUMB
  );
  private static final Set<Operation> WRITE_OPERATIONS = Set.of(
    Operation.CREATE,
    Operation.REPLACE,
    Operation.PATCH,
    Operation.DELETE,
    Operation.MOVE,
    Operation.REORDER,
    Operation.ACTIVATE,
    Operation.DEACTIVATE
  );

  @Test
  void buildsTrimOnlyStandardPerSystemPermissions() {
    assertThat(AdminCategoryAuthorities.readPermission(" catalogue ")).isEqualTo(
      "admin:catalogue-categories:read"
    );
    assertThat(AdminCategoryAuthorities.writePermission("blog")).isEqualTo(
      "admin:blog-categories:write"
    );
  }

  @Test
  void standardOperationMappingRemainsExactlySixReadsAndEightWrites() {
    assertThat(READ_OPERATIONS).hasSize(6);
    assertThat(WRITE_OPERATIONS).hasSize(8);
    assertThat(READ_OPERATIONS)
      .doesNotContainAnyElementsOf(WRITE_OPERATIONS)
      .containsExactlyInAnyOrder(
        Operation.CAPABILITIES,
        Operation.LIST,
        Operation.TREE,
        Operation.PARENT_OPTIONS,
        Operation.GET,
        Operation.BREADCRUMB
      );
    assertThat(
      java.util.stream.Stream.concat(READ_OPERATIONS.stream(), WRITE_OPERATIONS.stream()).collect(
        java.util.stream.Collectors.toSet()
      )
    ).containsExactlyInAnyOrder(Operation.values());

    for (Operation operation : Operation.values()) {
      assertThat(AdminCategoryAuthorities.standardPermission("catalogue", operation))
        .as(operation.code())
        .isEqualTo(
          READ_OPERATIONS.contains(operation)
            ? "admin:catalogue-categories:read"
            : "admin:catalogue-categories:write"
        );
    }
  }

  @Test
  void declaredOperationUsesOnlyItsExactRegisteredPermission() {
    AdminCategorySystemRegistry registry = registryWith(
      "restricted-system",
      Operation.LIST,
      "tenant:restricted:list"
    );
    AdminCategorySystemAuthorizationPolicy policy = new AdminCategorySystemAuthorizationPolicy(
      registry
    );

    assertThat(
      policy.canAccess(auth("tenant:restricted:list"), "restricted-system", Operation.LIST)
    ).isTrue();
    assertThat(
      policy.canAccess(
        auth("admin:restricted-system-categories:read"),
        "restricted-system",
        Operation.LIST
      )
    ).isFalse();
  }

  @Test
  void everyDeclaredOperationUsesItsExactRegisteredPermission() {
    for (Operation operation : Operation.values()) {
      String permission = "custom:" + operation.code();
      AdminCategorySystemAuthorizationPolicy policy = new AdminCategorySystemAuthorizationPolicy(
        registryWith("restricted-system", operation, permission)
      );

      assertThat(policy.canAccess(auth(permission), "restricted-system", operation))
        .as(operation.code())
        .isTrue();
      assertThat(
        policy.canAccess(
          auth(AdminCategoryAuthorities.standardPermission("restricted-system", operation)),
          "restricted-system",
          operation
        )
      )
        .as("standard permission must not replace custom permission for " + operation.code())
        .isFalse();
    }
  }

  @Test
  void knownUnsupportedOperationUsesOnlyItsStandardRoutePreliminaryPermission() {
    AdminCategorySystemRegistry registry = registryWith(
      "restricted-system",
      Operation.LIST,
      "tenant:restricted:list"
    );
    AdminCategorySystemAuthorizationPolicy policy = new AdminCategorySystemAuthorizationPolicy(
      registry
    );

    assertThat(
      policy.canAccess(
        auth("admin:restricted-system-categories:read"),
        "restricted-system",
        Operation.TREE
      )
    ).isTrue();
    assertThat(
      policy.canAccess(auth("tenant:restricted:list"), "restricted-system", Operation.TREE)
    ).isFalse();
  }

  @Test
  void absentAndMalformedKeysUseTrimmedStandardPermissionWithoutFallback() {
    AdminCategorySystemRegistry registry = mock(AdminCategorySystemRegistry.class);
    when(registry.findRegistration("missing-system")).thenReturn(Optional.empty());
    AdminCategorySystemAuthorizationPolicy policy = new AdminCategorySystemAuthorizationPolicy(
      registry
    );

    assertThat(
      policy.canAccess(
        auth("admin:missing-system-categories:write"),
        " missing-system ",
        Operation.CREATE
      )
    ).isTrue();
    assertThat(
      policy.canAccess(auth("admin:Mixed-System-categories:read"), " Mixed-System ", Operation.GET)
    ).isTrue();
    verify(registry, never()).findRegistration("Mixed-System");
  }

  @Test
  void readAndWritePermissionsAreIndependentAndSystemScoped() {
    AdminCategorySystemAuthorizationPolicy policy = new AdminCategorySystemAuthorizationPolicy(
      mock(AdminCategorySystemRegistry.class)
    );

    assertThat(policy.canRead(auth("admin:catalogue-categories:read"), "catalogue")).isTrue();
    assertThat(policy.canWrite(auth("admin:catalogue-categories:write"), "catalogue")).isTrue();
    assertThat(policy.canRead(auth("admin:catalogue-categories:write"), "catalogue")).isFalse();
    assertThat(policy.canWrite(auth("admin:catalogue-categories:read"), "catalogue")).isFalse();
    assertThat(policy.canRead(auth("admin:blog-categories:read"), "catalogue")).isFalse();
    assertThat(policy.canWrite(auth("admin:blog-categories:write"), "catalogue")).isFalse();
  }

  @Test
  void legacyGlobalRoleAndScopeAuthoritiesDoNotGrantGenericSystemAccess() {
    AdminCategorySystemAuthorizationPolicy policy = new AdminCategorySystemAuthorizationPolicy(
      mock(AdminCategorySystemRegistry.class)
    );
    for (String deniedAuthority : List.of(
      LEGACY_ADMIN_CATEGORIES_READ,
      LEGACY_ADMIN_CATEGORIES_WRITE,
      AdminAuthorities.ROLE_ADMIN,
      "ROLE_MODERATOR",
      "SCOPE_admin:catalogue-categories:read",
      "SCOPE_admin:catalogue-categories:write"
    )) {
      assertThat(policy.canRead(auth(deniedAuthority), "catalogue"))
        .as("read access for %s", deniedAuthority)
        .isFalse();
      assertThat(policy.canWrite(auth(deniedAuthority), "catalogue"))
        .as("write access for %s", deniedAuthority)
        .isFalse();
    }
  }

  @Test
  void nullUnauthenticatedUnderprivilegedAndNullOperationsAreDenied() {
    AdminCategorySystemAuthorizationPolicy policy = new AdminCategorySystemAuthorizationPolicy(
      mock(AdminCategorySystemRegistry.class)
    );
    TestingAuthenticationToken unauthenticated = auth(
      "admin:catalogue-categories:read",
      "admin:catalogue-categories:write"
    );
    unauthenticated.setAuthenticated(false);

    assertThat(policy.canAccess(null, "catalogue", Operation.LIST)).isFalse();
    assertThat(policy.canAccess(unauthenticated, "catalogue", Operation.LIST)).isFalse();
    assertThat(policy.canAccess(auth(), "catalogue", Operation.LIST)).isFalse();
    assertThat(policy.canAccess(auth("anything"), "catalogue", null)).isFalse();
  }

  private static AdminCategorySystemRegistry registryWith(
    String systemKey,
    Operation operation,
    String permission
  ) {
    AdminCategorySystemDefinition definition = mock(AdminCategorySystemDefinition.class);
    AdminCategorySystemDefinition.OperationDeclaration operations = mock(
      AdminCategorySystemDefinition.OperationDeclaration.class
    );
    when(operations.supportedOperations()).thenReturn(Set.of(operation));
    when(definition.operations()).thenReturn(operations);

    AdminCategoryPermissionMapping permissions = mock(AdminCategoryPermissionMapping.class);
    when(permissions.permissionFor(operation)).thenReturn(Optional.of(permission));

    AdminCategorySystemRegistration registration = mock(AdminCategorySystemRegistration.class);
    AdminCategoryRuntimeBinding runtimeBinding = mock(AdminCategoryRuntimeBinding.class);
    when(registration.definition()).thenReturn(Optional.of(definition));
    when(registration.runtimeBinding()).thenReturn(Optional.of(runtimeBinding));
    when(runtimeBinding.permissionMapping()).thenReturn(permissions);

    AdminCategorySystemRegistry registry = mock(AdminCategorySystemRegistry.class);
    when(registry.findRegistration(systemKey)).thenReturn(Optional.of(registration));
    return registry;
  }

  private static TestingAuthenticationToken auth(String... authorities) {
    return new TestingAuthenticationToken(
      "admin",
      "n/a",
      List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
    );
  }
}
