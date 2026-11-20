package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record PatchCatalogueCategoryRequest(
  Long parentId,
  boolean parentIdPresent,
  String name,
  boolean namePresent,
  String slug,
  boolean slugPresent,
  Boolean active,
  boolean activePresent
) {
  public static PatchCatalogueCategoryRequest from(ObjectNode patch) {
    return new PatchCatalogueCategoryRequest(
      longValue(patch, "parentId"),
      patch.has("parentId"),
      text(patch, "name"),
      patch.has("name"),
      text(patch, "slug"),
      patch.has("slug"),
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
}
