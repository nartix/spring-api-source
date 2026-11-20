package com.ferozfaiz.uniapi.category.application.policy;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AdminCategoryParentOptionsPolicy {

  public static final int DEFAULT_LIMIT = 25;
  public static final int MIN_LIMIT = 1;
  public static final int MAX_LIMIT = 50;
  public static final int MAX_INCLUDE_IDS = 25;
  public static final int MAX_SEARCH_LENGTH = 120;

  private AdminCategoryParentOptionsPolicy() {}

  public static String normalizeSearch(String rawSearch) {
    if (rawSearch == null) {
      return null;
    }
    if (rawSearch.length() > MAX_SEARCH_LENGTH) {
      throw invalidRequest(
        "category.api.parent-options.search.size",
        "q must be at most " + MAX_SEARCH_LENGTH + " characters",
        MAX_SEARCH_LENGTH
      );
    }
    String normalized = rawSearch.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  public static int resolveLimit(Integer limit) {
    int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
    if (resolvedLimit < MIN_LIMIT) {
      throw invalidRequest(
        "category.api.parent-options.limit.min",
        "limit must be at least " + MIN_LIMIT,
        MIN_LIMIT
      );
    }
    if (resolvedLimit > MAX_LIMIT) {
      throw invalidRequest(
        "category.api.parent-options.limit.max",
        "limit must be at most " + MAX_LIMIT,
        MAX_LIMIT
      );
    }
    return resolvedLimit;
  }

  public static List<Long> normalizeIncludeIds(List<String> rawIncludeIds) {
    if (rawIncludeIds == null || rawIncludeIds.isEmpty()) {
      return List.of();
    }
    if (rawIncludeIds.size() > MAX_INCLUDE_IDS) {
      throw invalidRequest(
        "category.api.parent-options.include-ids.max",
        "includeIds must contain at most " + MAX_INCLUDE_IDS + " values",
        MAX_INCLUDE_IDS
      );
    }
    Set<Long> ids = new LinkedHashSet<>();
    for (String rawValue : rawIncludeIds) {
      ids.add(parsePositiveId(rawValue, "category.api.parent-options.include-ids.invalid"));
    }
    return List.copyOf(new ArrayList<>(ids));
  }

  public static Long normalizeExcludeSubtreeId(String rawExcludeSubtreeId) {
    String value = blankToNull(rawExcludeSubtreeId);
    if (value == null) {
      return null;
    }
    return parsePositiveId(value, "category.api.parent-options.exclude-subtree-id.invalid");
  }

  public static boolean resolveIncludeInactive(String rawIncludeInactive) {
    String value = blankToNull(rawIncludeInactive);
    if (value == null) {
      return true;
    }
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw invalidRequest(
      "category.api.parent-options.include-inactive.invalid",
      "includeInactive must be true or false"
    );
  }

  public static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static Long parsePositiveId(String rawValue, String messageKey) {
    String value = blankToNull(rawValue);
    if (value == null || !value.matches("[0-9]+")) {
      throw invalidRequest(messageKey, defaultPositiveIdMessage(messageKey));
    }
    try {
      long parsed = Long.parseLong(value);
      if (parsed > 0) {
        return parsed;
      }
    } catch (NumberFormatException exception) {
      // Fall through to the stable validation response.
    }
    throw invalidRequest(messageKey, defaultPositiveIdMessage(messageKey));
  }

  private static String defaultPositiveIdMessage(String messageKey) {
    if (messageKey.contains("exclude-subtree-id")) {
      return "excludeSubtreeId must be a positive category ID";
    }
    return "includeIds must contain positive category IDs";
  }

  private static InvalidRequestException invalidRequest(
    String messageKey,
    String defaultMessage,
    Object... args
  ) {
    return new InvalidRequestException(messageKey, defaultMessage, args);
  }
}
