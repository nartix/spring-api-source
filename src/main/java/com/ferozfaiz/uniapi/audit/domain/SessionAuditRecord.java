package com.ferozfaiz.uniapi.audit.domain;

import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import java.time.Instant;
import java.util.Objects;

public record SessionAuditRecord(
  SessionLifecycleEvent.Scope sessionScope,
  SessionLifecycleEvent.Type eventType,
  Long userId,
  String username,
  String sessionIdHash,
  String ipAddress,
  String userAgent,
  String failureReason,
  Instant createdAt
) {
  public SessionAuditRecord {
    Objects.requireNonNull(eventType, "eventType must not be null");
    createdAt = createdAt == null ? Instant.now() : createdAt;
  }

  public static SessionAuditRecord from(SessionLifecycleEvent event) {
    return new SessionAuditRecord(
      event.sessionScope(),
      event.eventType(),
      event.userId(),
      event.username(),
      event.sessionIdHash(),
      event.ipAddress(),
      event.userAgent(),
      event.failureReason(),
      event.createdAt()
    );
  }
}
