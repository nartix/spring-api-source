package com.ferozfaiz.uniapi.catalogue.application.validation;

import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProductIdentifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProductCommandValidator {

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

  public void validateFamilyCompatibility(boolean compatible) {
    if (!compatible) {
      throw new CatalogueException(
        "catalogue.application.product.family.incompatible",
        "product details are incompatible with the selected family"
      );
    }
  }

  public void validateRequiredProductFields(
    String title,
    String slug,
    Object family,
    Long brandId,
    Long manufacturerId
  ) {
    if (isBlank(title)) {
      throw new CatalogueException(
        "catalogue.application.product.title.required",
        "product title is required"
      );
    }
    if (isBlank(slug)) {
      throw new CatalogueException(
        "catalogue.application.product.slug.required",
        "product slug is required"
      );
    }
    if (family == null) {
      throw new CatalogueException(
        "catalogue.application.product.family.required",
        "product family is required"
      );
    }
    if (brandId == null) {
      throw new CatalogueException(
        "catalogue.application.product.brand.required",
        "product brand is required"
      );
    }
    if (manufacturerId == null) {
      throw new CatalogueException(
        "catalogue.application.product.manufacturer.required",
        "product manufacturer is required"
      );
    }
  }

  public void validateProductFieldShapes(
    String title,
    String slug,
    String mainImageUrl,
    String shortDescription
  ) {
    validateSize(title, 255, "catalogue.api.product.title.size", "product title is too long");
    validateSlug(slug);
    validateSize(
      mainImageUrl,
      1000,
      "catalogue.api.product.main-image-url.size",
      "product main image URL is too long"
    );
    validateSize(
      shortDescription,
      1000,
      "catalogue.api.product.short-description.size",
      "product short description is too long"
    );
  }

  public void validateProductIdentifiers(List<CatalogueProductIdentifier> identifiers) {
    if (identifiers == null || identifiers.isEmpty()) {
      return;
    }
    int primaryCount = 0;
    Set<String> canonicalValues = new HashSet<>();
    for (CatalogueProductIdentifier identifier : identifiers) {
      if (identifier == null) {
        throw new CatalogueException(
          "catalogue.api.product.identifiers.invalid",
          "product identifiers are invalid"
        );
      }
      if (identifier.primary()) {
        primaryCount++;
      }
      if (!canonicalValues.add(identifier.canonicalGtin14())) {
        throw new CatalogueException(
          "catalogue.api.product.identifier.duplicate",
          "product identifier is duplicated",
          identifier.canonicalGtin14()
        );
      }
    }
    if (primaryCount > 1) {
      throw new CatalogueException(
        "catalogue.api.product.identifier.primary.max",
        "only one product identifier may be primary"
      );
    }
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
