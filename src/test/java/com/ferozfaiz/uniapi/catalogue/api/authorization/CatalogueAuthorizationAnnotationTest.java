package com.ferozfaiz.uniapi.catalogue.api.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.api.CatalogueAuthorities;
import com.ferozfaiz.uniapi.catalogue.api.CatalogueCategoryController;
import com.ferozfaiz.uniapi.catalogue.api.CatalogueController;
import com.ferozfaiz.uniapi.catalogue.api.CatalogueReferenceDataController;
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

class CatalogueAuthorizationAnnotationTest {

  @Test
  void composedAnnotationsUseCatalogueAuthorityExpressions() {
    assertThat(preAuthorize(RequireCatalogueRead.class)).isEqualTo(
      CatalogueAuthorities.CATALOGUE_READ_EXPRESSION
    );
    assertThat(preAuthorize(RequireCatalogueWrite.class)).isEqualTo(
      CatalogueAuthorities.CATALOGUE_WRITE_EXPRESSION
    );
  }

  @Test
  void catalogueControllersUseComposedAuthorizationAnnotations() {
    Stream.of(
      CatalogueController.class,
      CatalogueCategoryController.class,
      CatalogueReferenceDataController.class
    )
      .flatMap(CatalogueAuthorizationAnnotationTest::mappedMethods)
      .forEach(method -> {
        assertThat(method.isAnnotationPresent(PreAuthorize.class))
          .as("%s should use a composed authorization annotation", method.getName())
          .isFalse();

        if (method.isAnnotationPresent(GetMapping.class)) {
          assertThat(method.isAnnotationPresent(RequireCatalogueRead.class))
            .as("%s should require catalogue read authority", method.getName())
            .isTrue();
        } else {
          assertThat(method.isAnnotationPresent(RequireCatalogueWrite.class))
            .as("%s should require catalogue write authority", method.getName())
            .isTrue();
        }
      });
  }

  private static String preAuthorize(Class<? extends Annotation> annotationType) {
    return annotationType.getAnnotation(PreAuthorize.class).value();
  }

  private static Stream<Method> mappedMethods(Class<?> controllerType) {
    return Arrays.stream(controllerType.getDeclaredMethods()).filter(
      CatalogueAuthorizationAnnotationTest::isMappedEndpoint
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
