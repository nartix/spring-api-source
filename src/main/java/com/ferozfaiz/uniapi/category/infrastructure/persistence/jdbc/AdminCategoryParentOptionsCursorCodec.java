package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AdminCategoryParentOptionsCursorCodec {

  private static final int VERSION = 1;
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  public String encode(String systemKey, RequestContext context, Position position) {
    String payload = String.join(
      "\t",
      Integer.toString(VERSION),
      requireText(systemKey, "systemKey"),
      contextHash(context),
      Integer.toString(position.searchRank()),
      Boolean.toString(position.active()),
      encodeScalar(position.normalizedName()),
      Long.toString(position.id())
    );
    return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  public Position decode(String systemKey, RequestContext context, String cursor) {
    try {
      String payload = new String(
        DECODER.decode(requireText(cursor, "cursor")),
        StandardCharsets.UTF_8
      );
      String[] parts = payload.split("\t", -1);
      if (parts.length != 7 || Integer.parseInt(parts[0]) != VERSION) {
        throw AdminCategoryErrors.parentOptionsCursorInvalid();
      }
      if (
        !requireText(systemKey, "systemKey").equals(parts[1]) ||
        !contextHash(context).equals(parts[2])
      ) {
        throw AdminCategoryErrors.parentOptionsCursorInvalid();
      }
      int searchRank = Integer.parseInt(parts[3]);
      boolean active = Boolean.parseBoolean(parts[4]);
      String normalizedName = decodeScalar(parts[5]);
      long id = Long.parseLong(parts[6]);
      if (searchRank < 0 || id <= 0 || normalizedName.isBlank()) {
        throw AdminCategoryErrors.parentOptionsCursorInvalid();
      }
      return new Position(searchRank, active, normalizedName, id);
    } catch (RuntimeException exception) {
      if (exception instanceof AdminCategoryErrors.AdminCategoryException adminCategoryException) {
        throw adminCategoryException;
      }
      throw AdminCategoryErrors.parentOptionsCursorInvalid();
    }
  }

  private static String contextHash(RequestContext context) {
    Objects.requireNonNull(context, "context");
    String normalized = String.join(
      "\t",
      normalizeText(context.q()),
      Boolean.toString(context.includeInactive()),
      context.excludeSubtreeId() == null ? "" : context.excludeSubtreeId().toString(),
      context
        .includeIds()
        .stream()
        .sorted()
        .distinct()
        .map(String::valueOf)
        .collect(Collectors.joining(","))
    );
    return ENCODER.encodeToString(sha256(normalized));
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private static String encodeScalar(String value) {
    return ENCODER.encodeToString(
      requireText(value, "normalizedName").getBytes(StandardCharsets.UTF_8)
    );
  }

  private static String decodeScalar(String value) {
    return new String(DECODER.decode(value), StandardCharsets.UTF_8);
  }

  private static String normalizeText(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return value.trim();
  }

  public record RequestContext(
    String q,
    boolean includeInactive,
    Long excludeSubtreeId,
    List<Long> includeIds
  ) {
    public RequestContext {
      includeIds = includeIds == null ? List.of() : List.copyOf(includeIds);
    }
  }

  public record Position(int searchRank, boolean active, String normalizedName, Long id) {
    public Position {
      if (searchRank < 0) {
        throw new IllegalArgumentException("searchRank must be zero or greater");
      }
      normalizedName = requireText(normalizedName, "normalizedName");
      if (id == null || id <= 0) {
        throw new IllegalArgumentException("id must be positive");
      }
    }
  }
}
