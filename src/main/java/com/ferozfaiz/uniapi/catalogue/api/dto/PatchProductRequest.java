package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.util.ArrayList;
import java.util.List;

public record PatchProductRequest(
  String title,
  boolean titlePresent,
  String slug,
  boolean slugPresent,
  ProductFamily family,
  boolean familyPresent,
  Long brandId,
  boolean brandIdPresent,
  Long manufacturerId,
  boolean manufacturerIdPresent,
  String mainImageUrl,
  boolean mainImageUrlPresent,
  String shortDescription,
  boolean shortDescriptionPresent,
  List<ProductIdentifierRequest> identifiers,
  boolean identifiersPresent,
  Boolean active,
  boolean activePresent
) {
  public static PatchProductRequest from(ObjectNode patch) {
    return new PatchProductRequest(
      text(patch, "title"),
      patch.has("title"),
      text(patch, "slug"),
      patch.has("slug"),
      family(patch, "family"),
      patch.has("family"),
      longValue(patch, "brandId"),
      patch.has("brandId"),
      longValue(patch, "manufacturerId"),
      patch.has("manufacturerId"),
      text(patch, "mainImageUrl"),
      patch.has("mainImageUrl"),
      text(patch, "shortDescription"),
      patch.has("shortDescription"),
      identifiers(patch, "identifiers"),
      patch.has("identifiers"),
      bool(patch, "active"),
      patch.has("active")
    );
  }

  private static String text(ObjectNode patch, String field) {
    JsonNode node = patch.get(field);
    return node == null || node.isNull() ? null : node.asText();
  }

  private static Long longValue(ObjectNode patch, String field) {
    JsonNode node = patch.get(field);
    return node == null || node.isNull() ? null : node.asLong();
  }

  private static Boolean bool(ObjectNode patch, String field) {
    JsonNode node = patch.get(field);
    return node == null || node.isNull() ? null : node.asBoolean();
  }

  private static List<ProductIdentifierRequest> identifiers(ObjectNode patch, String field) {
    JsonNode node = patch.get(field);
    if (node == null) {
      return List.of();
    }
    if (node.isNull()) {
      throw new InvalidRequestException(
        "catalogue.api.patch.field.required",
        field + " cannot be null",
        field
      );
    }
    if (!node.isArray()) {
      throw new InvalidRequestException(
        "catalogue.api.product.identifiers.invalid",
        "product identifiers are invalid",
        field
      );
    }
    List<ProductIdentifierRequest> identifiers = new ArrayList<>(node.size());
    for (JsonNode item : node) {
      if (!item.isObject()) {
        throw new InvalidRequestException(
          "catalogue.api.product.identifiers.invalid",
          "product identifiers are invalid",
          field
        );
      }
      identifiers.add(
        new ProductIdentifierRequest(
          text((ObjectNode) item, "family"),
          text((ObjectNode) item, "value"),
          bool((ObjectNode) item, "primary")
        )
      );
    }
    return List.copyOf(identifiers);
  }

  private static ProductFamily family(ObjectNode patch, String field) {
    String value = text(patch, field);
    if (value == null) {
      return null;
    }
    try {
      return ProductFamily.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestException(
        "catalogue.api.product.family.invalid",
        "product family is invalid",
        value
      );
    }
  }
}
