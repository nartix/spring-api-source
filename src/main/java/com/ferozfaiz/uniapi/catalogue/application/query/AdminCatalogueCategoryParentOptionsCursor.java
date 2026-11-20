package com.ferozfaiz.uniapi.catalogue.application.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.catalogue.application.policy.AdminCategoryParentOptionsPolicy;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AdminCatalogueCategoryParentOptionsCursor(
  int version,
  Mode mode,
  String q,
  boolean includeInactive,
  Long excludeSubtreeId,
  Integer rank,
  boolean active,
  String name,
  Long id
) {
  public static final int VERSION = 1;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public AdminCatalogueCategoryParentOptionsCursor {
    Objects.requireNonNull(mode, "mode must not be null");
    if (version != VERSION) {
      throw invalidCursor();
    }
    if (mode == Mode.SEARCH && q == null) {
      throw invalidCursor();
    }
    if (mode == Mode.DEFAULT && q != null) {
      throw invalidCursor();
    }
    if (mode == Mode.SEARCH && rank == null) {
      throw invalidCursor();
    }
    if (excludeSubtreeId != null && excludeSubtreeId <= 0) {
      throw invalidCursor();
    }
    if (name == null || name.isBlank() || id == null || id <= 0) {
      throw invalidCursor();
    }
    q = AdminCategoryParentOptionsPolicy.normalizedSearchKey(q);
    name = AdminCategoryParentOptionsPolicy.normalizedSearchKey(name);
  }

  public enum Mode {
    DEFAULT,
    SEARCH,
  }

  public static AdminCatalogueCategoryParentOptionsCursor decode(String rawCursor) {
    String cursor = AdminCategoryParentOptionsPolicy.blankToNull(rawCursor);
    if (cursor == null) {
      return null;
    }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(cursor);
      JsonNode root = OBJECT_MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8));
      return fromJson(root);
    } catch (IllegalArgumentException | JsonProcessingException exception) {
      throw invalidCursor();
    }
  }

  public static String encode(
    Mode mode,
    String q,
    boolean includeInactive,
    Long excludeSubtreeId,
    Integer rank,
    boolean active,
    String name,
    Long id
  ) {
    var cursor = new AdminCatalogueCategoryParentOptionsCursor(
      VERSION,
      mode,
      AdminCategoryParentOptionsPolicy.normalizedSearchKey(q),
      includeInactive,
      excludeSubtreeId,
      rank,
      active,
      name,
      id
    );
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("version", cursor.version());
    payload.put("mode", cursor.mode().name());
    payload.put("q", cursor.q());
    payload.put("includeInactive", cursor.includeInactive());
    payload.put("excludeSubtreeId", cursor.excludeSubtreeId());
    payload.put("rank", cursor.rank());
    payload.put("active", cursor.active());
    payload.put("name", cursor.name());
    payload.put("id", cursor.id());
    try {
      return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(OBJECT_MAPPER.writeValueAsBytes(payload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to encode category parent-options cursor", exception);
    }
  }

  public void validateContext(String q, boolean includeInactive, Long excludeSubtreeId) {
    Mode expectedMode = q == null ? Mode.DEFAULT : Mode.SEARCH;
    String expectedQ = AdminCategoryParentOptionsPolicy.normalizedSearchKey(q);
    if (
      mode != expectedMode ||
      !Objects.equals(this.q, expectedQ) ||
      this.includeInactive != includeInactive ||
      !Objects.equals(this.excludeSubtreeId, excludeSubtreeId)
    ) {
      throw new InvalidRequestException(
        "catalogue.api.parent-options.cursor.context",
        "cursor does not match the request context"
      );
    }
  }

  private static AdminCatalogueCategoryParentOptionsCursor fromJson(JsonNode root) {
    if (root == null || !root.isObject()) {
      throw invalidCursor();
    }
    int version = requiredInt(root, "version");
    if (version != VERSION) {
      throw invalidCursor();
    }
    Mode mode = requiredMode(root);
    return new AdminCatalogueCategoryParentOptionsCursor(
      version,
      mode,
      nullableText(root, "q"),
      requiredBoolean(root, "includeInactive"),
      nullablePositiveLong(root, "excludeSubtreeId"),
      nullableInt(root, "rank"),
      requiredBoolean(root, "active"),
      requiredText(root, "name"),
      requiredPositiveLong(root, "id")
    );
  }

  private static Mode requiredMode(JsonNode root) {
    String mode = requiredText(root, "mode");
    try {
      return Mode.valueOf(mode);
    } catch (IllegalArgumentException exception) {
      throw invalidCursor();
    }
  }

  private static String requiredText(JsonNode root, String field) {
    JsonNode value = root.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw invalidCursor();
    }
    return value.textValue();
  }

  private static String nullableText(JsonNode root, String field) {
    JsonNode value = root.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isTextual()) {
      throw invalidCursor();
    }
    String text = value.textValue();
    return text.isBlank() ? null : text;
  }

  private static int requiredInt(JsonNode root, String field) {
    JsonNode value = root.get(field);
    if (value == null || !value.canConvertToInt()) {
      throw invalidCursor();
    }
    return value.intValue();
  }

  private static Integer nullableInt(JsonNode root, String field) {
    JsonNode value = root.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.canConvertToInt()) {
      throw invalidCursor();
    }
    return value.intValue();
  }

  private static boolean requiredBoolean(JsonNode root, String field) {
    JsonNode value = root.get(field);
    if (value == null || !value.isBoolean()) {
      throw invalidCursor();
    }
    return value.booleanValue();
  }

  private static Long nullablePositiveLong(JsonNode root, String field) {
    JsonNode value = root.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    return positiveLong(value);
  }

  private static Long requiredPositiveLong(JsonNode root, String field) {
    JsonNode value = root.get(field);
    if (value == null) {
      throw invalidCursor();
    }
    return positiveLong(value);
  }

  private static Long positiveLong(JsonNode value) {
    if (!value.canConvertToLong()) {
      throw invalidCursor();
    }
    long parsed = value.longValue();
    if (parsed <= 0) {
      throw invalidCursor();
    }
    return parsed;
  }

  private static InvalidRequestException invalidCursor() {
    return new InvalidRequestException(
      "catalogue.api.parent-options.cursor.format",
      "cursor is invalid"
    );
  }
}
