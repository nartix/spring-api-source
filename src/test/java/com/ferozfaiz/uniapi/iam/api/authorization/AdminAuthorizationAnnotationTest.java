package com.ferozfaiz.uniapi.iam.api.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.iam.api.AdminAuthorities;
import com.ferozfaiz.uniapi.iam.api.auth.AdminSessionAuthController;
import java.lang.annotation.Annotation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminAuthorizationAnnotationTest {

  @Test
  void composedAnnotationsUseAdminAuthorityExpressions() {
    assertThat(preAuthorize(RequireAdminAccess.class)).isEqualTo(
      AdminAuthorities.ADMIN_ACCESS_EXPRESSION
    );
    assertThat(preAuthorize(RequireAdminUsersRead.class)).isEqualTo(
      AdminAuthorities.ADMIN_USERS_READ_EXPRESSION
    );
    assertThat(preAuthorize(RequireAdminUsersStatus.class)).isEqualTo(
      AdminAuthorities.ADMIN_USERS_STATUS_EXPRESSION
    );
    assertThat(preAuthorize(RequireAdminUsersWrite.class)).isEqualTo(
      AdminAuthorities.ADMIN_USERS_WRITE_EXPRESSION
    );
  }

  @Test
  void adminIdentityEndpointRequiresExplicitAdminAccess() throws Exception {
    assertThat(
      AdminSessionAuthController.class
        .getDeclaredMethod("me", org.springframework.security.core.Authentication.class)
        .isAnnotationPresent(RequireAdminAccess.class)
    ).isTrue();
  }

  private static String preAuthorize(Class<? extends Annotation> annotationType) {
    return annotationType.getAnnotation(PreAuthorize.class).value();
  }
}
