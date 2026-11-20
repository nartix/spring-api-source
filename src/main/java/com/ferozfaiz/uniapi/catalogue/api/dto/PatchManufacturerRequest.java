package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record PatchManufacturerRequest(
  String name,
  boolean namePresent,
  String slug,
  boolean slugPresent,
  Boolean active,
  boolean activePresent
) {
  public static PatchManufacturerRequest from(ObjectNode patch) {
    return new PatchManufacturerRequest(
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

  private static Boolean bool(ObjectNode patch, String field) {
    JsonNode node = patch.get(field);
    return node == null || node.isNull() ? null : node.asBoolean();
  }
}
