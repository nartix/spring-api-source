package com.ferozfaiz.uniapi.session.api;

import java.time.Instant;
import java.util.Objects;
import org.springframework.util.StringUtils;

public record SessionLifecycleEvent(
  Type eventType,
  Scope sessionScope,
  Long userId,
  String username,
  String sessionIdHash,
  String ipAddress,
  String userAgent,
  String failureReason,
  Instant createdAt
) {
  private static final int MAX_METADATA_LENGTH = 512;

  public SessionLifecycleEvent {
    Objects.requireNonNull(eventType, "eventType must not be null");
    createdAt = createdAt == null ? Instant.now() : createdAt;
    username = bounded(username);
    sessionIdHash = bounded(sessionIdHash);
    ipAddress = bounded(ipAddress);
    userAgent = bounded(userAgent);
    failureReason = bounded(failureReason);
  }

  public static SessionLifecycleEvent now(
    Type eventType,
    Scope sessionScope,
    Long userId,
    String username,
    String sessionIdHash,
    String ipAddress,
    String userAgent,
    String failureReason
  ) {
    return new SessionLifecycleEvent(
      eventType,
      sessionScope,
      userId,
      username,
      sessionIdHash,
      ipAddress,
      userAgent,
      failureReason,
      Instant.now()
    );
  }

  private static String bounded(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= MAX_METADATA_LENGTH
      ? trimmed
      : trimmed.substring(0, MAX_METADATA_LENGTH);
  }

  public enum Type {
    USER_LOGIN_SUCCESS,
    USER_LOGIN_FAILURE,
    USER_LOGOUT,
    ADMIN_LOGIN_SUCCESS,
    ADMIN_LOGIN_FAILURE,
    ADMIN_LOGOUT,
    MODERATOR_LOGIN_SUCCESS,
    MODERATOR_LOGOUT,
    ADMIN_ACCESS_DENIED,
  }

  public enum Scope {
    USER,
    ADMIN,
    MODERATOR,
  }
}
