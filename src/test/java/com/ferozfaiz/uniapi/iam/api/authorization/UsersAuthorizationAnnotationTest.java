package com.ferozfaiz.uniapi.iam.api.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.iam.api.UserAuthorities;
import com.ferozfaiz.uniapi.iam.api.UserController;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class UsersAuthorizationAnnotationTest {

  @Test
  void composedAnnotationsUseUserAuthorityExpressions() {
    assertThat(preAuthorize(RequireUsersRead.class)).isEqualTo(
      UserAuthorities.USERS_READ_EXPRESSION
    );
    assertThat(preAuthorize(RequireUsersWrite.class)).isEqualTo(
      UserAuthorities.USERS_WRITE_EXPRESSION
    );
  }

  @Test
  void userControllerUsesComposedAuthorizationAnnotations() {
    mappedMethods(UserController.class).forEach(method -> {
      assertThat(method.isAnnotationPresent(PreAuthorize.class))
        .as("%s should use a composed authorization annotation", method.getName())
        .isFalse();

      if (method.isAnnotationPresent(GetMapping.class)) {
        assertThat(method.isAnnotationPresent(RequireUsersRead.class))
          .as("%s should require users read authority", method.getName())
          .isTrue();
      } else {
        assertThat(method.isAnnotationPresent(RequireUsersWrite.class))
          .as("%s should require users write authority", method.getName())
          .isTrue();
      }
    });
  }

  private static String preAuthorize(Class<? extends Annotation> annotationType) {
    return annotationType.getAnnotation(PreAuthorize.class).value();
  }

  private static Stream<Method> mappedMethods(Class<?> controllerType) {
    return Arrays.stream(controllerType.getDeclaredMethods()).filter(
      UsersAuthorizationAnnotationTest::isMappedEndpoint
    );
  }

  private static boolean isMappedEndpoint(Method method) {
    return (
      method.isAnnotationPresent(GetMapping.class) ||
      method.isAnnotationPresent(PostMapping.class) ||
      method.isAnnotationPresent(PutMapping.class) ||
      method.isAnnotationPresent(PatchMapping.class) ||
      method.isAnnotationPresent(DeleteMapping.class)
    );
  }
}
