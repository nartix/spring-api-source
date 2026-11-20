package com.ferozfaiz.uniapi.audit.infrastructure.persistence;

import com.ferozfaiz.uniapi.audit.domain.SessionAuditRecord;
import com.ferozfaiz.uniapi.session.api.SessionLifecycleEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "auth_session_audit")
class JpaSessionAuditRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "session_scope", length = 32)
  private SessionLifecycleEvent.Scope sessionScope;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 64)
  private SessionLifecycleEvent.Type eventType;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "username", length = 120)
  private String username;

  @Column(name = "session_id_hash", length = 128)
  private String sessionIdHash;

  @Column(name = "ip_address", length = 128)
  private String ipAddress;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @Column(name = "failure_reason", length = 128)
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected JpaSessionAuditRecord() {}

  private JpaSessionAuditRecord(SessionAuditRecord record) {
    this.sessionScope = record.sessionScope();
    this.eventType = record.eventType();
    this.userId = record.userId();
    this.username = record.username();
    this.sessionIdHash = record.sessionIdHash();
    this.ipAddress = record.ipAddress();
    this.userAgent = record.userAgent();
    this.failureReason = record.failureReason();
    this.createdAt = record.createdAt();
  }

  static JpaSessionAuditRecord from(SessionAuditRecord record) {
    return new JpaSessionAuditRecord(record);
  }

  SessionAuditRecord toDomain() {
    return new SessionAuditRecord(
      sessionScope,
      eventType,
      userId,
      username,
      sessionIdHash,
      ipAddress,
      userAgent,
      failureReason,
      createdAt
    );
  }
}
