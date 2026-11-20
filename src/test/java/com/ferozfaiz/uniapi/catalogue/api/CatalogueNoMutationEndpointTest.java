package com.ferozfaiz.uniapi.catalogue.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class CatalogueNoMutationEndpointTest {

  @Test
  void catalogueControllersExposeOnlyScopedWriteMutationEndpoints() {
    List<String> productMutations = mutationPaths(CatalogueController.class);
    List<String> categoryMutations = mutationPaths(CatalogueCategoryController.class);

    assertThat(productMutations).containsExactlyInAnyOrder(
      "/products",
      "/products/{id}",
      "/products/{id}",
      "/products/{id}",
      "/products/{id}/activate",
      "/products/{id}/deactivate",
      "/products/{id}/categories/{categoryId}",
      "/products/{id}/categories/{categoryId}"
    );
    assertThat(categoryMutations).containsExactlyInAnyOrder(
      "/catalogue/categories",
      "/catalogue/categories/{id}",
      "/catalogue/categories/{id}",
      "/catalogue/categories/{id}",
      "/catalogue/categories/{id}/move",
      "/catalogue/categories/{id}/reorder",
      "/catalogue/categories/{id}/activate",
      "/catalogue/categories/{id}/deactivate"
    );
    assertThat(productMutations).allSatisfy(path ->
      assertThat(path)
        .doesNotContain("bulk")
        .doesNotContain("soft")
        .doesNotContain("tombstone")
        .doesNotContain("audit")
        .doesNotContain("image")
        .doesNotContain("offers")
        .doesNotContain("prices")
    );
    assertThat(
      Stream.concat(productMutations.stream(), categoryMutations.stream()).toList()
    ).allSatisfy(path ->
      assertThat(path)
        .doesNotContain("public")
        .doesNotContain("anonymous")
        .doesNotContain("idempotency")
        .doesNotContain("bulk")
        .doesNotContain("soft")
        .doesNotContain("tombstone")
        .doesNotContain("audit")
        .doesNotContain("image")
        .doesNotContain("offers")
        .doesNotContain("prices")
    );
  }

  private static List<String> mutationPaths(Class<?> controllerType) {
    return Arrays.stream(controllerType.getDeclaredMethods())
      .filter(
        method ->
          method.isAnnotationPresent(PostMapping.class) ||
          method.isAnnotationPresent(PutMapping.class) ||
          method.isAnnotationPresent(PatchMapping.class) ||
          method.isAnnotationPresent(DeleteMapping.class)
      )
      .map(CatalogueNoMutationEndpointTest::path)
      .sorted()
      .toList();
  }

  private static String path(Method method) {
    if (method.isAnnotationPresent(PostMapping.class)) {
      return firstPath(method.getAnnotation(PostMapping.class).value());
    }
    if (method.isAnnotationPresent(PutMapping.class)) {
      return firstPath(method.getAnnotation(PutMapping.class).value());
    }
    if (method.isAnnotationPresent(PatchMapping.class)) {
      return firstPath(method.getAnnotation(PatchMapping.class).value());
    }
    return firstPath(method.getAnnotation(DeleteMapping.class).value());
  }

  private static String firstPath(String[] paths) {
    return paths.length == 0 ? "" : paths[0];
  }
}
