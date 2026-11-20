package com.ferozfaiz.uniapi.catalogue.application.validation;

import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CatalogueCategoryCommandValidator {

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

  public void validateNotMovingIntoDescendant(boolean moveWouldCreateLoop) {
    if (moveWouldCreateLoop) {
      throw CatalogueConflictException.invalidCategoryMove(null, null);
    }
  }

  public void validateRequiredCategoryFields(String name, String slug) {
    if (isBlank(name)) {
      throw new CatalogueException(
        "catalogue.application.category.name.required",
        "category name is required"
      );
    }
    if (isBlank(slug)) {
      throw new CatalogueException(
        "catalogue.application.category.slug.required",
        "category slug is required"
      );
    }
  }

  public void validateCategoryFieldShapes(String name, String slug) {
    validateSize(name, 255, "catalogue.api.category.name.size", "category name is too long");
    validateSlug(slug);
  }

  public void validateConfiguredPlacementMode(
    CategorySystemDescriptor descriptor,
    SparseOrderTarget orderTarget
  ) {
    if (
      !orderTarget.hasExplicitPlacement() ||
      descriptor.ranking().placementModes().contains(orderTarget.mode())
    ) {
      return;
    }
    throw new InvalidRequestException(
      "catalogue.api.category.placement.unsupported",
      "placement mode %s is not supported for catalogue categories".formatted(orderTarget.mode()),
      orderTarget.mode().name()
    );
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static void validateSize(String value, int max, String key, String message) {
    if (value != null && value.length() > max) {
      throw new CatalogueException(key, message, max);
    }
  }

  private static void validateSlug(String slug) {
    if (!isBlank(slug) && (slug.length() > 180 || !SLUG_PATTERN.matcher(slug).matches())) {
      throw new CatalogueException(
        "catalogue.api.slug.pattern",
        "slug may contain lowercase letters, numbers, and hyphens",
        slug
      );
    }
  }
}
