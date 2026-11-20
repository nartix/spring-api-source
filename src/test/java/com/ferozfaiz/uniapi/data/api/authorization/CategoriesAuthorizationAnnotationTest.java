package com.ferozfaiz.uniapi.data.api.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.data.api.CategoryAuthorities;
import com.ferozfaiz.uniapi.data.api.CategoryController;
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

class CategoriesAuthorizationAnnotationTest {

  @Test
  void composedAnnotationsUseCategoryAuthorityExpressions() {
    assertThat(preAuthorize(RequireCategoriesRead.class)).isEqualTo(
      CategoryAuthorities.CATEGORIES_READ_EXPRESSION
    );
    assertThat(preAuthorize(RequireCategoriesWrite.class)).isEqualTo(
      CategoryAuthorities.CATEGORIES_WRITE_EXPRESSION
    );
  }

  @Test
  void categoryControllerUsesComposedAuthorizationAnnotations() {
    mappedMethods(CategoryController.class).forEach(method -> {
      assertThat(method.isAnnotationPresent(PreAuthorize.class))
        .as("%s should use a composed authorization annotation", method.getName())
        .isFalse();

      if (method.isAnnotationPresent(GetMapping.class)) {
        assertThat(method.isAnnotationPresent(RequireCategoriesRead.class))
          .as("%s should require categories read authority", method.getName())
          .isTrue();
      } else {
        assertThat(method.isAnnotationPresent(RequireCategoriesWrite.class))
          .as("%s should require categories write authority", method.getName())
          .isTrue();
      }
    });
  }

  private static String preAuthorize(Class<? extends Annotation> annotationType) {
    return annotationType.getAnnotation(PreAuthorize.class).value();
  }

  private static Stream<Method> mappedMethods(Class<?> controllerType) {
    return Arrays.stream(controllerType.getDeclaredMethods()).filter(
      CategoriesAuthorizationAnnotationTest::isMappedEndpoint
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
